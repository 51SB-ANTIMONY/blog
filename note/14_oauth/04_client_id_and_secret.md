---
title: Client ID와 Client Secret
date: 2026-10-03
tags: [oauth, client-id, client-secret, security, google]
---

# Client ID와 Client Secret

> OAuth의 역할과 권한 부여 코드 방식은 [OAuth 2.0이란 무엇인가](./01_oauth.md), [권한 부여 코드 승인 방식](./02_authorization_code_grant.md)에서 설명했다.

## 1. 클라이언트 등록이 필요한 이유
인가 서버(Google)는 아무 애플리케이션에나 사용자 데이터 접근을 허락하지 않는다. 다음을 미리 알고 있어야 한다.
- 이 애플리케이션이 **누구인가**: 동의 화면에 보여 줄 이름, 로고, 개발자 연락처
- 코드를 **어디로** 보내도 되는가: 승인된 리디렉션 URI
- 토큰을 요청하는 쪽이 **정말 그 애플리케이션인지** 확인할 방법

그래서 클라이언트는 먼저 인가 서버에 자신을 **등록**하고, 그 결과로 **Client ID**와 **Client Secret**을 받는다(RFC 6749 2장).

## 2. Client ID
**인가 서버가 등록된 클라이언트를 구별하기 위해 발급하는 식별자**다.
```
123456789012-abcdefghijklmnop.apps.googleusercontent.com
```
Google의 Client ID는 `프로젝트 번호-임의 문자열.apps.googleusercontent.com` 형식이다.

| 항목 | 내용 |
|---|---|
| 역할 | "어떤 애플리케이션의 요청인가"를 알린다. 인가 서버는 이 값으로 등록 정보(이름, 리디렉션 URI, 허용 범위)를 찾는다 |
| 쓰이는 곳 | ③ 권한 요청 URL의 `client_id` 파라미터, ⑦ 토큰 요청의 클라이언트 인증 |
| 공개 여부 | **공개 정보.** 권한 요청 URL에 그대로 들어가므로 브라우저 주소창에 보인다 |

Client ID는 숨길 수 없고, 숨길 필요도 없다. 사용자의 브라우저가 이 값을 들고 Google에 가야 하기 때문이다. Client ID만으로는 토큰을 받을 수 없다.

## 3. Client Secret
**클라이언트가 자신이 등록된 그 클라이언트임을 인가 서버에 증명하기 위한 비밀 값**이다. 인가 서버와 클라이언트만 아는 값이라서, 이 값을 제시할 수 있다는 것이 곧 그 클라이언트라는 증거가 된다.
```
GOCSPX-ExampleSecretValue1234
```
Google의 Client Secret은 `GOCSPX-`로 시작한다.

| 항목 | 내용 |
|---|---|
| 역할 | 토큰 요청(⑦)에서 **클라이언트 인증**. "이 코드를 토큰으로 바꾸려는 쪽이 정말 이 Client ID의 주인인가"를 증명 |
| 쓰이는 곳 | ⑦ 토큰 요청의 `Authorization: Basic` 헤더 (또는 본문의 `client_secret`). 리프레시 토큰으로 재발급할 때도 쓴다 |
| 전달 경로 | **백 채널만.** 클라이언트 서버 → 인가 서버 HTTPS 직접 요청. 브라우저를 거치지 않는다 |
| 공개 여부 | **비공개.** 클라이언트 서버와 인가 서버만 알아야 한다 |

### 실제로 어떻게 확인하는가
```http
POST /oauth2/v4/token HTTP/1.1
Host: www.googleapis.com
Authorization: Basic MTIzNDU2Nzg5MDEyLWFiY2RlZmdoaWprbG1ub3AuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb206R09DU1BYLUV4YW1wbGVTZWNyZXRWYWx1ZTEyMzQ=
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=4%2F0AVMBsJh...&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Fgoogle
```
1. 인가 서버가 `Authorization` 헤더를 Base64 디코딩한다 → `client_id:client_secret`
2. `client_id`로 등록 정보를 찾고, 저장된 비밀 값과 `client_secret`을 비교한다
3. 다르면 `401 invalid_client`. 같으면 코드 검사로 넘어간다
4. 코드가 **이 `client_id`에게** 발급된 것인지 확인한다

## 4. 두 값의 비교
| | Client ID | Client Secret |
|---|---|---|
| 의미 | 클라이언트를 가리키는 식별자 | 클라이언트를 인증하는 비밀 값 |
| 하는 일 | 식별 (누구인가) | 인증 (정말 그인가) |
| 지나가는 길 | 프론트 채널 + 백 채널 | 백 채널만 |
| 사용자에게 보이는가 | 보인다 (주소창) | 보이지 않는다 |
| 유출되면 | 문제없음 | **교체해야 함** |

식별과 인증을 서로 다른 값으로 나눴기 때문에, 식별자는 공개되어도 되고 인증 값만 비밀로 지키면 된다.

## 5. 기밀 클라이언트와 공개 클라이언트
Client Secret을 **안전하게 보관할 수 있는가**에 따라 클라이언트를 나눈다(RFC 6749 2.1).

| 종류 | 예시 | Client Secret | 토큰 교환 시 증명 방법 |
|---|---|---|---|
| **기밀 클라이언트** (Confidential) | 서버에서 실행되는 웹 애플리케이션 (이 프로젝트의 Spring Boot 서버) | 서버 설정에 보관. 사용자가 볼 수 없음 | Client Secret (+ PKCE 권장) |
| **공개 클라이언트** (Public) | SPA (React), 모바일 앱, 데스크톱 앱 | 보관 불가. 코드가 사용자 기기에 배포되므로 누구나 꺼낼 수 있음 | **PKCE** |

React 코드에 Client Secret을 넣으면 빌드된 JS 파일에 그대로 들어가서, 브라우저 개발자 도구로 누구나 볼 수 있다. 모바일 앱도 설치 파일을 풀면 꺼낼 수 있다. 그래서 공개 클라이언트는 Client Secret 대신 PKCE를 쓴다([권한 부여 코드 승인 방식](./02_authorization_code_grant.md) 8장).

## 6. Client Secret을 공개하면 안 되는 이유
Client Secret이 유출되면 **누구나 그 애플리케이션인 척 인가 서버에 토큰을 요청할 수 있다.** 구체적으로는 다음 일이 가능해진다.

| 위험 | 설명 |
|---|---|
| **가로챈 코드를 토큰으로 바꿀 수 있다** | 권한 부여 코드는 브라우저 주소창을 거치므로 방문 기록, 서버 로그, 브라우저 확장 프로그램, `Referer` 헤더로 새어 나갈 수 있다. 원래는 Client Secret이 없으면 코드만으로는 아무것도 못 한다. Secret이 유출되면 이 마지막 방어선이 사라진다. |
| **리프레시 토큰을 계속 쓸 수 있다** | 리프레시 토큰으로 새 액세스 토큰을 받을 때도 클라이언트 인증이 필요하다. 리프레시 토큰과 Secret이 함께 새면, 공격자가 사용자 데이터에 오랫동안 접근할 수 있다. |
| **애플리케이션 사칭** | 공격자가 그 애플리케이션 이름으로 인가 서버와 통신한다. 인가 서버 입장에서는 정상 클라이언트와 구분할 수 없다. |
| **사용량 한도와 계정 문제** | 공격자의 요청도 그 애플리케이션의 API 사용량으로 집계된다. 남용이 감지되면 인가 서버가 클라이언트 자체를 정지시킬 수 있고, 정상 사용자도 로그인할 수 없게 된다. |

**리디렉션 URI 등록이 남은 방어선이다.** 인가 서버는 등록된 리디렉션 URI로만 코드를 보낸다. 그래서 Secret이 유출되더라도 공격자가 정상적인 흐름으로 코드를 자기 서버로 받아 갈 수는 없다. 하지만 위 표처럼 코드가 다른 경로로 새는 경우에는 막지 못한다. 그래서 "리디렉션 URI 검사 + Client Secret" 두 겹이 함께 있어야 한다.

### 유출됐을 때 할 일
1. **즉시 교체:** Google Cloud Console → 사용자 인증 정보 → 해당 OAuth 클라이언트 → 새 Secret 추가 → 애플리케이션 설정을 새 값으로 바꾼 뒤 → 옛 Secret 사용 중지 및 삭제
2. **사용 기록 확인:** 이상한 토큰 발급이 있었는지 확인
3. **Git 기록 확인:** 커밋에 들어간 적이 있다면, 지금 파일에서 지워도 **이전 커밋에 남아 있다.** 공개 저장소였다면 이미 수집되었다고 보고 반드시 교체한다. 기록에서 지우는 것(`git filter-repo` 등)은 교체 **이후의 정리**일 뿐 대신할 수 없다.

## 7. 안전하게 보관하는 방법
| 방법 | 설명 |
|---|---|
| 설정 파일을 Git에서 제외 | `application.yml`을 `.gitignore`에 넣는다. 이 프로젝트의 현재 방식 |
| 환경 변수로 주입 | 설정 파일에는 변수 이름만 두고, 실제 값은 실행 환경에서 넣는다 |
| 비밀 관리 서비스 | AWS Secrets Manager, Parameter Store, GitHub Actions Secrets 등 |

환경 변수 방식의 예:
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - email
              - profile
```
`${...}`는 Spring Boot가 실행할 때 같은 이름의 환경 변수 값으로 바꾼다. 이렇게 하면 `application.yml` 자체는 Git에 올려도 되고, 실제 값은 로컬에서는 IDE 실행 설정, 배포 환경에서는 Elastic Beanstalk 환경 속성 같은 곳에 둔다.

## 8. Google에서 등록하는 과정
1. **Google Cloud Console**에서 프로젝트를 만든다.
2. **OAuth 동의 화면**을 설정한다.
   - 앱 이름, 지원 이메일, 로고: 사용자가 동의 화면에서 보는 정보
   - 범위: `email`, `profile`
   - 게시 상태: "테스트" 상태에서는 등록한 테스트 사용자만 로그인할 수 있다
3. **사용자 인증 정보 → OAuth 클라이언트 ID 만들기**
   - 애플리케이션 유형: **웹 애플리케이션** (서버가 Secret을 보관하는 기밀 클라이언트)
   - 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google`. 배포하면 `https://도메인/login/oauth2/code/google`도 추가한다
4. 발급된 **Client ID**와 **Client Secret**을 `application.yml`(또는 환경 변수)에 넣는다.

**`redirect_uri_mismatch` 오류:** 권한 요청의 `redirect_uri`가 등록된 값과 **한 글자라도** 다르면 발생한다. `http`/`https`, 포트, 끝의 `/` 유무까지 같아야 한다. 프록시 뒤에서 실행할 때 Spring이 `http`로 주소를 만드는 경우가 흔한 원인이다.

## 9. 이 프로젝트에서
| 항목 | 현재 상태 |
|---|---|
| 클라이언트 종류 | 기밀 클라이언트 (Spring Boot 서버가 Secret 보관) |
| 보관 위치 | `src/main/resources/application.yml` (`.gitignore`에 포함, Git 기록에 없음) |
| 클라이언트 인증 방식 | `client_secret_basic` (Spring Security의 Google 기본값) |
| 리디렉션 URI | `http://localhost:8080/login/oauth2/code/google` |
| PKCE | 사용 안 함 (기밀 클라이언트의 Spring 기본값) |

배포할 때는 7장의 환경 변수 방식으로 바꾸고, 운영용 리디렉션 URI를 Google에 추가 등록해야 한다.

## 정리
| 항목 | 핵심 |
|---|---|
| Client ID | 클라이언트 식별자. 공개 정보. 권한 요청 URL에 들어감 |
| Client Secret | 클라이언트 인증 값. 백 채널(토큰 요청)에서만 사용. 비공개 |
| 유출 시 | 가로챈 코드나 리프레시 토큰을 토큰으로 바꿀 수 있게 됨 → 즉시 교체 |
| 보관 | Git 제외, 환경 변수, 비밀 관리 서비스 |
| 공개 클라이언트 | Secret을 보관할 수 없으므로 PKCE 사용 |
