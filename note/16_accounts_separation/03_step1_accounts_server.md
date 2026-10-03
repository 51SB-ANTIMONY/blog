---
title: accounts 분리하기 - 1단계 accounts 서버 (목적과 계획)
date: 2026-10-03
tags: [oidc, authorization-server, spring-security, nextjs, accounts]
---

# accounts 분리하기 - 1단계 accounts 서버 (목적과 계획)

> 전체 단계는 [로드맵](./01_roadmap.md)에 있다.
> 실제 코드와 이유는 [1단계 구현 과정](./04_step1_accounts_implementation.md), 확인 결과와 겪은 문제는 [1단계 확인과 트러블슈팅](./05_step1_accounts_verification_and_troubleshooting.md)에 있다.

## 1. 목적
**antimony.kr의 로그인을 맡을 OIDC 인가 서버 accounts를 만든다.** 로그인 화면도 accounts가 가진다.

| 목적 | 내용 |
|---|---|
| 로그인을 한곳으로 | blog에 있던 로그인 코드를 accounts로 옮긴다 |
| 표준 인가 서버 | 직접 만든 JWT 발급 대신, OIDC 표준을 지키는 Spring Authorization Server로 토큰을 발급한다 |
| 회원 모델 정리 | 아이디·비밀번호 회원과 소셜 회원을 한 회원 테이블로 관리하고, 소셜 계정은 연결 테이블로 붙인다 |
| 화면 | 프로토타입대로 Next.js 로그인·회원가입 화면을 만든다 |
| 독립성 | 나중에 `accounts/`만 떼어 새 저장소로 옮길 수 있게 만든다 |

**이 단계에서 하지 않는 것:** blog를 accounts에 연결하는 일(2단계). 1단계 동안 blog는 기존 Google 로그인으로 계속 동작한다.

## 2. 세부 단계
| 단계 | 목적 | 커밋 |
|---|---|---|
| 1-1 | 독립 프로젝트 뼈대 | `d525572` |
| 1-2 | blog의 로그인 코드를 그대로 복사 | `2d09b81` |
| 1-3 | 토큰 처리를 Spring Authorization Server로 교체 | `cde278b` |
| 1-4 | 아이디·비밀번호 회원가입 정리 | `d226214` |
| 1-5 | 소셜 계정을 이메일이 아니라 제공자의 `sub`로 연결 | `6f04e27` |
| 1-6 | 토큰의 `sub`를 회원 id로 통일, 클레임 추가, 전체 로그아웃 | `8202648` |
| 1-7 | 화면이 쓸 API와 Next.js 화면 | `fd7783b`, `dac7fb9`, `39c94ec` |
| 1-8 | README, 노트 | `aebbfe9`, `ef771f7` |

## 3. 수행 방안

### 1-1. 독립 프로젝트 뼈대
**왜:** 나중에 이 폴더만 떼어 내도 빌드되려면, 처음부터 blog의 빌드 설정에 기대지 않아야 한다.

**어떻게:**
- `accounts/backend`에 자체 `settings.gradle`, `build.gradle`, `gradlew`, `.gitignore`, `application.yml.example`을 둔다.
- Spring Boot 버전, Java 버전을 blog와 같게 맞춘다(4.0.1, 25). 1-2에서 복사한 코드가 수정 없이 동작하게 하려고.
- 포트는 9000, 세션 쿠키 이름은 `ACCOUNTS_SESSION`. 쿠키는 포트를 구분하지 않아서, blog와 같은 기본 이름(`JSESSIONID`)을 쓰면 서로 덮어쓴다.

### 1-2. blog 코드를 그대로 복사
**왜:** "옮기기"와 "구조 바꾸기"를 한 번에 하면, 문제가 생겼을 때 어느 쪽 때문인지 알 수 없다. 먼저 그대로 옮겨서 똑같이 동작하는 것을 확인한다.

**어떻게:**
- blog의 인증 관련 파일(보안 설정, JWT, OAuth, 회원, 토큰 재발급, 로그인 화면, 테스트)을 복사하고 **패키지 이름만** `kr.antimony.accounts`로 바꾼다.
- 꼭 필요한 한 곳만 고친다. 로그인 후 이동할 주소(blog의 `/articles`)를 설정값으로 뺀다.
- blog 원본은 지우지 않는다. blog는 2단계 전까지 계속 동작해야 한다.

### 1-3. Spring Authorization Server로 교체
**왜:** 직접 만든 JWT 발급은 OIDC 표준(설정 문서, 공개 키, 권한 부여 코드, PKCE 등)을 지키지 않아서 다른 앱이 붙을 수 없다.

**어떻게:**
| 할 일 | 방법 |
|---|---|
| 의존성 | `spring-boot-starter-oauth2-authorization-server`. `jjwt` 제거 |
| 필터 체인 2개 | ① 인가 서버 주소(`/oauth2/**`, `/.well-known/**`, `/userinfo`, `/connect/**`)만 담당하는 체인, ② 나머지(로그인 화면, 회원가입)를 담당하는 체인. ①이 먼저 |
| 앱 등록 | `blog` 클라이언트: `client_secret_basic`, 권한 부여 코드 + 리프레시 토큰, 범위 `openid profile email`, PKCE 필수, 동의 화면 생략(자사 앱) |
| 서명 | RS256 키 쌍. 공개 키는 `/oauth2/jwks`로 공개 |
| issuer | 설정값으로 지정 |
| 지울 것 | `TokenProvider`, `TokenAuthenticationFilter`, 리프레시 토큰 저장소, 재발급 API, 쿠키 저장소 |
| 확인 | 설정 문서, 공개 키, 로그인 전 권한 요청 → 로그인 화면, 잘못된 redirect_uri 거부, PKCE 없으면 거부, 코드 → 토큰 교환 |

> **필터 체인을 두 개로 나누는 이유**
> 인가 서버 주소는 앱(blog)이 Client Secret이나 토큰으로 인증하고, 화면 주소는 사람이 로그인 폼으로 인증한다. 인증 방식이 다르므로 체인을 나눠, 요청 주소에 따라 한쪽만 적용되게 한다. 자세한 내용은 [인가 서버와 Spring Authorization Server](../15_sso/04_spring_authorization_server.md)에 있다.

### 1-4. 아이디·비밀번호 회원가입 정리
**왜:** 0-2에서 미뤄 둔 `uid` 제약과 회원가입 규칙을 정한다.

**어떻게:**
- `uid`를 `NOT NULL`, `UNIQUE`로. 소셜 회원은 `google_3f9a2c1b`처럼 자동 생성한다(비밀번호가 없으므로 아이디 로그인은 불가).
- 입력값 규칙과 중복 검사(아이디, 이메일, 닉네임). 중복이면 409와 필드별 메시지.
- 규칙은 1-7에서 프로토타입에 맞춰 다시 조정했다(아이디 4–16자 소문자·숫자, 비밀번호 영문·숫자·특수문자).

### 1-5. 소셜 계정을 `sub`로 연결
**왜:** 이메일로 소셜 계정과 회원을 찾으면 두 가지 문제가 있다. 아이디로 `a@gmail.com`을 가입한 회원이 있을 때, 같은 이메일의 Google 계정으로 로그인한 사람이 그 회원으로 로그인된다(이메일이 같다고 같은 사람이라고 확신할 수 없다). 또 Google 계정의 이메일이 바뀌면 같은 사람인데도 다른 회원이 된다.

**어떻게:**
- `user_identities(provider, provider_subject → user_id)` 테이블을 만든다. 제공자의 계정 고유 번호(`sub`)로 연결한다.
- 처음 보는 소셜 계정인데 같은 이메일의 회원이 있으면 **자동으로 합치지 않고 거부**한다.
- 아이디로 로그인한 상태에서 소셜 로그인을 하면 그 회원에 **연결**한다.

### 1-6. 토큰의 `sub`를 회원 id로 통일
**왜:** 로그인 방식에 따라 ID 토큰의 `sub`가 달랐다(아이디 로그인은 `uid`, Google 로그인은 Google의 번호). 앱이 같은 사람을 다른 사람으로 여기게 된다.

**어떻게:**
- 로그인 방식마다 다른 사용자 객체가 `AccountPrincipal`(회원 id를 꺼내는 인터페이스)을 구현하게 한다.
- `OAuth2TokenCustomizer`로 모든 토큰의 `sub`를 회원 id로 바꾸고, ID 토큰에 `nickname`, `preferred_username`, `email`을 넣는다.
- 전체 로그아웃(`/connect/logout`)을 켜고, 돌아갈 주소를 앱마다 등록한다.

### 1-7. 화면이 쓸 API와 Next.js 화면
**왜:** 로그인 화면을 Thymeleaf 임시 화면에서 프로토타입대로의 Next.js 화면으로 바꾼다.

**어떻게:**
| 할 일 | 방법 |
|---|---|
| 프로토타입 | 디자인 아티팩트의 화면(로그인, 회원가입, 이메일 인증, 아이디·비밀번호 찾기)을 그대로 구현. 필요한 화면(로그인 오류, 내 계정)은 프로토타입에 먼저 추가 |
| API | 이메일 인증 가입, 아이디 찾기, 비밀번호 재설정, 내 계정, 소셜 연결 해제, 로그인 상태 유지, CSRF 토큰, 제공자 목록 |
| 같은 주소 | Next.js(3001)가 `/api/**`, `/login/password`, `/oauth2/**` 등을 Spring(9000)으로 넘긴다(rewrites). issuer도 3001 |
| CSRF | `csrf.spa()`: `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더 또는 `_csrf` 폼 값 |
| 반응형, 테마 | PC·모바일, 라이트·다크 |
| 확인 | 모든 흐름을 Next.js를 거쳐 실제로 실행 |

### 1-8. 기록
accounts README, 구현 노트, 확인·트러블슈팅 노트를 단계마다 갱신한다.

## 4. 결과
- accounts가 OIDC 인가 서버로 동작한다. 설정 문서, 공개 키, 권한 부여 코드 + PKCE, ID 토큰(`sub` = 회원 id), 전체 로그아웃.
- 아이디·비밀번호(이메일 인증 가입)와 Google 로그인, 소셜 계정 연결·해제.
- Next.js 로그인 화면(PC·모바일, 라이트·다크).
- 자동 테스트 37개, 실제 흐름 확인 완료.
- 남은 것은 [로드맵](./01_roadmap.md)의 "배포 준비"에 모았다.
