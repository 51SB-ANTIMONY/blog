---
title: accounts 분리하기 - 2단계 blog를 OIDC 클라이언트로 (목적과 계획)
date: 2026-10-03
tags: [oidc, oauth2-client, spring-security, session, csrf, bff]
---

# accounts 분리하기 - 2단계 blog를 OIDC 클라이언트로 (목적과 계획)

> 전체 단계는 [로드맵](./01_roadmap.md)에 있다.
> 실제 코드, 확인 결과, 겪은 문제는 [2단계 구현, 확인, 트러블슈팅](./07_step2_blog_oidc_client.md)에 있다.

## 1. 목적
**blog가 로그인을 직접 처리하지 않고 accounts에 맡기게 한다.**

| 목적 | 내용 |
|---|---|
| 로그인을 accounts로 | blog의 Google 로그인과 자체 JWT 발급을 없애고, accounts의 OIDC 클라이언트가 된다 |
| 토큰을 브라우저에 두지 않기 (BFF) | 토큰은 blog 서버의 세션에만 두고, 브라우저에는 `HttpOnly` 세션 쿠키만 준다 |
| 같은 사람은 같은 회원 | 아이디·비밀번호든 Google이든, accounts의 `sub`로 blog 회원을 구별한다 |
| SSO와 전체 로그아웃 | accounts에 로그인돼 있으면 blog에서 비밀번호를 다시 묻지 않고, blog에서 로그아웃하면 accounts 로그인도 끝난다 |

> **BFF(Backend For Frontend)란?**
> 인가 서버에서 받은 토큰을 브라우저가 아니라 서버가 가지고 있는 방식이다. 브라우저에는 서버와의 세션 쿠키만 있어서, 화면에 악성 스크립트가 들어와도 토큰을 가져가지 못한다.

**이 단계에서 하지 않는 것:** blog 화면 교체(3단계). 임시 Thymeleaf 화면은 로그인 방식에 맞게 최소한만 고친다.

### 바꾸기 전의 blog
- Google 로그인을 blog가 직접 처리한다(`OAuth2UserCustomService`).
- 로그인에 성공하면 blog가 자기 JWT와 리프레시 토큰을 발급한다.
- 화면의 JavaScript가 토큰을 `localStorage`에 두고 `Authorization` 헤더로 보낸다. 서버는 세션 없이(STATELESS) 토큰만 보고, CSRF 보호는 꺼져 있다.
- 글 작성자는 이메일 문자열로 저장된다.

## 2. 세부 단계
| 단계 | 목적 | 커밋 |
|---|---|---|
| 2-1 | accounts를 로그인 제공자로 등록하고 세션 로그인으로 바꾼다. 옛 Google 로그인·JWT 코드를 지운다 | `06cbebc` |
| 2-2 | blog 회원을 `sub`로 연결하고, 글 작성자를 회원으로 | `359ca6e` |
| 2-3 | CSRF 보호를 다시 켠다 | `b7fd8ea` |
| 2-4 | blog 로그아웃 때 accounts 로그인도 끝낸다 | `57ebdd1` |
| 2-5 | 세 서버를 함께 띄워 실제 흐름을 확인하고, 찾은 문제를 고친다 | `8664d11` |
| 2-6 | README, 노트 | `d444ec8`, `2980ff3`, `4989c20` |

### 계획에서 바뀐 점
처음 계획은 "accounts 등록(2-1) → 회원 연결 → CSRF → 로그아웃 → **옛 코드 삭제(2-5)** → 확인 → 기록"이었다. 실제로는 옛 코드 삭제를 2-1에 합쳤다. 옛 `OAuth2SuccessHandler`가 `@Component`라서 보안 설정에서 빼도 Spring이 계속 만들려 했고, 그러려면 함께 지울 쿠키 저장소 빈이 필요해서 서버가 켜지지 않았기 때문이다. 그래서 "로그인 방식 교체"를 등록과 삭제를 합친 한 단위로 했다.

## 3. 수행 방안

### 2-1. accounts를 로그인 제공자로 등록, 세션 로그인
| 할 일 | 방법 |
|---|---|
| 등록 정보 | `ClientRegistration`을 Java 코드(`AccountsClientConfig`)로 만든다. client_id `blog`, `client_secret_basic`, 범위 `openid profile email`, redirect_uri `{baseUrl}/login/oauth2/code/{registrationId}`, `userNameAttributeName = sub` |
| `issuer-uri`를 쓰지 않는 이유 | `issuer-uri` 설정은 서버를 켤 때 accounts에서 설정 문서를 받아 오므로, accounts가 꺼져 있으면 blog도 켜지지 않는다. 주소를 직접 넣고, `issuerUri`는 ID 토큰의 `iss` 검사에만 쓴다 |
| 세션 | `STATELESS` 제거. 세션 쿠키 이름 `BLOG_SESSION` (accounts의 `ACCOUNTS_SESSION`과 구별) |
| 로그인이 필요한 곳 | `/new-article`, `/api/**`. 화면은 accounts 로그인으로, API는 401 |
| PKCE | Spring Security 7이 기본으로 붙인다. 테스트로 확인 |
| 지울 것 | `TokenProvider`, `TokenAuthenticationFilter`, 리프레시 토큰, 재발급 API, blog의 Google 로그인 코드, 쿠키 저장소, `CookieUtil`, 로그인·회원가입 화면, `token.js`, `jjwt` |
| 비밀 값 | `application.yml`(Git 제외)에 `accounts.issuer`, `client-id`, `client-secret`. Secret은 accounts 설정과 같은 값. `application.yml.example`도 만든다 |
| 확인 | 로그인이 필요한 화면 → `/oauth2/authorization/accounts`, 권한 요청 주소(3001, `client_id`, 범위, PKCE, `state`, `nonce`), API 401 |

### 2-2. 회원을 `sub`로 연결
| 할 일 | 방법 |
|---|---|
| `users` 테이블 | `id`, `accounts_sub`(NOT NULL, UNIQUE), `nickname`(NOT NULL). 아이디·비밀번호·이메일은 accounts에만 둔다 |
| 로그인 직후 | `OidcUserService`를 상속한 `AccountsOidcUserService`가 `sub`로 회원을 찾거나 만들고, 닉네임을 accounts 값으로 갱신 |
| 글 작성자 | `article.author`(이메일 문자열) → `author_id`(회원 외래 키). 응답의 `author`는 닉네임 |
| 작성자 확인 | 로그인 사용자의 이름(`sub`)과 글 작성자의 `accountsSub` 비교 |
| 테스트 | `spring-security-test`의 `oidcLogin()`으로 로그인 상태를 만든다 |

### 2-3. CSRF 보호
| 할 일 | 방법 |
|---|---|
| 왜 다시 켜나 | 로그인 정보가 쿠키(세션)로 바뀌었고, 쿠키는 브라우저가 자동으로 보내므로 다른 사이트가 보낸 요청을 막아야 한다 |
| 방식 | accounts와 같은 `csrf.spa()`. `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더 |
| 토큰 발급 | `GET /api/csrf` (`CsrfApiController`) |
| 화면 | `article.js`가 쓰기 요청에 헤더를 붙인다. 쿠키가 없으면 먼저 `/api/csrf` |
| 테스트 | 실제 CSRF 설정은 새 Spring 컨텍스트(`@DirtiesContext`)에서 확인. 토큰 없으면 403, 쿠키 값을 헤더로 보내면 통과 |

### 2-4. 로그아웃
| 할 일 | 방법 |
|---|---|
| 처리기 | `OidcClientInitiatedLogoutSuccessHandler`. blog 세션을 지운 뒤 accounts의 `/connect/logout?id_token_hint=...&post_logout_redirect_uri=http://localhost:8080/`로 보낸다 |
| 로그아웃 주소 | 설정 문서를 받아 오지 않으므로 `end_session_endpoint`를 `ClientRegistration`에 직접 넣는다 |
| 돌아올 곳 | `/` → `/articles`. accounts에 등록한 `post_logout_redirect_uri`와 같아야 한다 |
| 화면 | 글 목록에 로그인 버튼 / 닉네임과 로그아웃 버튼(POST 폼, `_csrf` 자동 포함) |

### 2-5. 실제 흐름 확인
**어떻게:** accounts backend(9000), accounts frontend(3001), blog(8080)를 띄우고 `curl`로 브라우저처럼 요청한다. 쿠키 저장 파일은 하나만 쓴다(브라우저도 `localhost` 쿠키를 포트와 상관없이 한곳에 둔다).

| 확인할 것 | 기대 결과 |
|---|---|
| 로그인 전 글쓰기 | accounts 로그인 화면 → 로그인 → blog 글쓰기 화면 |
| 글 작성 | 작성자 = accounts 닉네임 |
| 틀린 CSRF 토큰 | 403 |
| SSO | blog 세션만 끝난 상태에서 다시 로그인하면 비밀번호를 묻지 않음 |
| 로그아웃 | accounts 로그인도 끝남. 다시 로그인하면 비밀번호를 물음 |

이 확인에서 blog 서버가 accounts의 Next.js(3001)를 거쳐 토큰을 받으려다 연결이 끊기는 문제를 찾았다. 서버끼리 부르는 주소(`accounts.backchannel-uri`, 9000)를 따로 두어 해결했다.

### 2-6. 기록
blog README(인증 설계, 흐름, ERD, 실행 방법, 트러블슈팅), 루트 README, 노트를 갱신한다.

## 4. 결과
- blog의 로그인은 accounts가 처리하고, blog는 세션으로 로그인 상태를 기억한다.
- blog 회원은 `sub`로 구별되고, 글 작성자는 회원 외래 키로 연결된다.
- CSRF 보호, SSO, 전체 로그아웃이 동작한다.
- 자동 테스트 20개, 세 서버를 함께 띄운 실제 흐름 확인 완료.
- blog README 트러블슈팅의 미해결 항목(로그아웃 후 토큰이 남음, 쿠키 역직렬화, 토큰 노출, 닉네임 중복, 토큰 유효 시간)이 함께 해결됐다.
