---
title: 권한 부여 코드 승인 방식 (Authorization Code Grant)
date: 2026-10-03
tags: [oauth, authorization-code, pkce, google, http]
---

# 권한 부여 코드 승인 방식 (Authorization Code Grant)

> OAuth의 역할과 용어는 [OAuth 2.0이란 무엇인가](./01_oauth.md)에서 설명했다.
> HTTP 메시지의 형식은 [토큰 기반 인증의 흐름과 HTTP 메시지 형식](../13_jwt/02_token_auth_flow_and_http_message.md)에서 설명했다.
> 이 노트의 주소와 기본값은 Google과 Spring Security 7.0 기준이다. Client ID, 비밀 값, 코드, 토큰은 설명용 예시 값이다.

## 1. 전체 흐름
```
 사용자       브라우저                  클라이언트                  인가 서버                리소스 서버
(리소스 소유자) (User-Agent)            (블로그 서버)               (Google 계정)            (Google API)
   │            │                          │                          │                       │
   │ 로그인 클릭 │ ① GET /oauth2/authorization/google                 │                       │
   │───────────▶│─────────────────────────▶│                          │                       │
   │            │ ② 302 → 인가 서버 (권한 요청)                        │                       │
   │            │◀─────────────────────────│                          │                       │
   │            │ ③ GET /o/oauth2/v2/auth?response_type=code&client_id=...                     │
   │            │────────────────────────────────────────────────────▶│                       │
   │ ④ 로그인, 동의 (데이터 접근용 권한 부여)                           │                       │
   │◀──────────────────────────────────────────────────────────────▶│                       │
   │            │ ⑤ 302 → 클라이언트 (인증 코드 제공) ?code=...&state=... │                       │
   │            │◀────────────────────────────────────────────────────│                       │
   │            │ ⑥ GET /login/oauth2/code/google?code=...&state=...  │                       │
   │            │─────────────────────────▶│                          │                       │
   │            │                          │ ⑦ POST /token (코드 + 클라이언트 인증)             │
   │            │                          │─────────────────────────▶│                       │
   │            │                          │ ⑧ 200 {access_token...} (액세스 토큰 응답)        │
   │            │                          │◀─────────────────────────│                       │
   │            │                          │ ⑨ GET /userinfo (Authorization: Bearer)          │
   │            │                          │─────────────────────────────────────────────────▶│
   │            │                          │ ⑩ 200 {sub, email, name...} (API 응답, 반환)       │
   │            │                          │◀─────────────────────────────────────────────────│
   │            │ ⑪ 로그인 처리 후 응답      │                          │                       │
   │            │◀─────────────────────────│                          │                       │
```
| 구간 | 경로 | 이름 |
|---|---|---|
| ①~⑥ | **브라우저를 거친다** (리다이렉트) | 프론트 채널 (Front Channel) |
| ⑦~⑩ | **서버와 서버가 직접 통신한다** | 백 채널 (Back Channel) |

> **프론트 채널과 백 채널이란?**
> 프론트 채널은 브라우저의 주소창과 리다이렉트를 통해 오가는 통신이다. 사용자, 브라우저 확장 프로그램, 방문 기록이 그 내용을 볼 수 있다. 백 채널은 클라이언트 서버가 인가 서버에 직접 HTTPS로 요청하는 통신이다. 브라우저를 거치지 않으므로 사용자도 내용을 볼 수 없다.

**이 방식의 핵심:** 프론트 채널로는 **짧게 쓰고 버리는 코드**만 오가고, 실제 **액세스 토큰은 백 채널**로만 전달된다. 그리고 코드를 토큰으로 바꿀 때 클라이언트가 **비밀 값(Client Secret)으로 자기 자신을 증명**해야 한다.

## 2. 사전 준비: 클라이언트 등록
흐름을 시작하기 전에 클라이언트는 인가 서버에 자신을 등록해야 한다. Google Cloud Console에서 등록하면 다음을 받는다.

| 값 | 예시 | 역할 |
|---|---|---|
| Client ID | `123456789012-abcdefghijklmnop.apps.googleusercontent.com` | 클라이언트를 식별하는 공개 값 |
| Client Secret | `GOCSPX-ExampleSecretValue1234` | 클라이언트를 인증하는 비밀 값 |
| 승인된 리디렉션 URI | `http://localhost:8080/login/oauth2/code/google` | 인가 서버가 코드를 보내도 되는 주소 목록 |

자세한 내용은 [Client ID와 Client Secret](./04_client_id_and_secret.md)에서 본다.

---

## 3. 권한 요청 (①~③)

### ① 사용자가 로그인 버튼을 누른다
```http
GET /oauth2/authorization/google HTTP/1.1
Host: localhost:8080
```
Spring Security의 `OAuth2AuthorizationRequestRedirectFilter`가 이 주소를 처리한다. `/oauth2/authorization/{registrationId}` 형식이며, `google`은 `application.yml`에 등록한 이름이다.

### ② 클라이언트가 인가 서버로 리다이렉트한다
클라이언트는 **권한 요청(Authorization Request)** URL을 만들어 브라우저를 그쪽으로 보낸다.
```http
HTTP/1.1 302 
Location: https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=123456789012-abcdefghijklmnop.apps.googleusercontent.com&scope=email%20profile&state=Zx4k9Qm2Rr7Lw0pTc5Hs1VbN8yJd3Ge6Fa_Ui-Ko&redirect_uri=http://localhost:8080/login/oauth2/code/google
Set-Cookie: oauth2_auth_request=rO0ABXNy...; Max-Age=18000; Path=/
```
- `Location`: 브라우저가 이동할 인가 서버 주소
- `Set-Cookie`: 이 요청의 내용(특히 `state`)을 ⑥에서 다시 확인하기 위해 저장한다. 기본은 서버 세션에 저장하지만, 이 프로젝트는 세션을 쓰지 않으므로(`STATELESS`) 쿠키에 저장한다([쿠키](./03_cookie.md) 7장).

### ③ 브라우저가 인가 서버에 권한을 요청한다
```http
GET /o/oauth2/v2/auth?response_type=code&client_id=123456789012-abcdefghijklmnop.apps.googleusercontent.com&scope=email%20profile&state=Zx4k9Qm2Rr7Lw0pTc5Hs1VbN8yJd3Ge6Fa_Ui-Ko&redirect_uri=http://localhost:8080/login/oauth2/code/google HTTP/1.1
Host: accounts.google.com
```
**권한 요청의 쿼리 파라미터 (RFC 6749 4.1.1)**

| 파라미터 | 필수 | 예시 값 | 의미 |
|---|---|---|---|
| `response_type` | 필수 | `code` | 원하는 응답 종류. 권한 부여 코드 방식은 `code` |
| `client_id` | 필수 | `123456789012-...googleusercontent.com` | 어떤 클라이언트가 요청하는가. 인가 서버는 이 값으로 등록 정보를 찾는다 |
| `redirect_uri` | 조건부 | `http://localhost:8080/login/oauth2/code/google` | 결과(코드)를 돌려보낼 주소. 등록된 주소와 **정확히 일치**해야 한다 |
| `scope` | 선택 | `email profile` | 요청하는 권한 범위. 공백으로 구분하며 URL에서는 `%20`으로 인코딩 |
| `state` | 권장 | `Zx4k9Qm2...` | 클라이언트가 만든 임의의 값. ⑤에서 그대로 돌려받아 비교한다 |
| `code_challenge` | PKCE | `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM` | PKCE 사용 시 (8장) |
| `code_challenge_method` | PKCE | `S256` | PKCE 사용 시 |

Google만의 추가 파라미터도 있다.
| 파라미터 | 의미 |
|---|---|
| `access_type=offline` | Google의 리프레시 토큰도 받고 싶을 때 |
| `prompt=consent` | 이미 동의한 사용자에게도 동의 화면을 다시 보여 줌 |
| `login_hint=a@gmail.com` | 로그인 화면에 이메일을 미리 채움 |

**인가 서버의 검사**
1. `client_id`가 등록된 클라이언트인가
2. `redirect_uri`가 그 클라이언트의 승인된 리디렉션 URI 목록에 **정확히** 있는가. 없으면 코드를 보내지 않고 오류 화면(`redirect_uri_mismatch`)을 보여 준다. 공격자가 코드를 자기 서버로 받아 가는 것을 막는 핵심 검사다.
3. `scope`가 허용된 범위인가

---

## 4. 데이터 접근용 권한 부여 (④)
인가 서버가 사용자와 직접 대화하는 단계다. **클라이언트는 이 단계에 관여하지 않는다.**

1. **사용자 인증:** 사용자가 Google에 로그인되어 있지 않으면 Google 로그인 화면을 보여 준다. 비밀번호, 2단계 인증은 모두 `accounts.google.com`에서 처리된다. **블로그 서버는 비밀번호를 볼 수 없다.**
2. **동의 화면:** Google이 클라이언트 등록 정보(앱 이름, 로고)와 요청된 범위를 사람이 읽을 수 있는 문장으로 보여 준다.
   ```
   Blog에서 Google 계정에 액세스하려고 합니다
     · 기본 Google 계정 이메일 주소 보기
     · 개인정보(공개로 설정한 개인정보 포함) 보기
   [취소] [계속]
   ```
3. **허락 기록:** 사용자가 "계속"을 누르면, Google은 "이 사용자가 이 클라이언트에게 이 범위를 허락했다"는 사실을 기록하고 **권한 부여 코드**를 만든다.

이미 동의한 적이 있는 사용자는 동의 화면을 건너뛸 수 있다.

---

## 5. 인증 코드 제공 (⑤~⑥)

### ⑤ 인가 서버가 코드를 담아 클라이언트로 리다이렉트한다
```http
HTTP/1.1 302 
Location: http://localhost:8080/login/oauth2/code/google?state=Zx4k9Qm2Rr7Lw0pTc5Hs1VbN8yJd3Ge6Fa_Ui-Ko&code=4%2F0AVMBsJh...example...&scope=email%20profile%20https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.email%20https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fuserinfo.profile%20openid&authuser=0&prompt=consent
```
**권한 부여 응답의 파라미터 (RFC 6749 4.1.2)**

| 파라미터 | 의미 |
|---|---|
| `code` | **권한 부여 코드.** 토큰으로 바꿀 수 있는 일회용 값 |
| `state` | ③에서 클라이언트가 보낸 값 그대로 |

Google은 실제로 허락된 범위(`scope`), 선택한 계정 순번(`authuser`) 같은 값도 함께 붙인다. Google은 `email`, `profile`을 요청받으면 그에 해당하는 전체 이름의 범위와 `openid`를 함께 허락한 것으로 응답하기도 한다.

**권한 부여 코드의 성질**
| 성질 | 설명 |
|---|---|
| 짧은 수명 | RFC 6749는 최대 10분을 권장한다. 보통 몇 분 안에 만료된다. |
| 일회용 | 한 번 토큰으로 바꾸면 다시 쓸 수 없다. 같은 코드가 두 번 쓰이면 인가 서버는 그 코드로 발급한 토큰을 취소해야 한다(RFC 6749 4.1.2). |
| 클라이언트에 묶임 | 이 코드는 이 `client_id`와 이 `redirect_uri`로만 교환할 수 있다. |
| 그 자체로는 쓸모없다 | 코드로는 API를 호출할 수 없다. 반드시 ⑦에서 **Client Secret과 함께** 토큰으로 바꿔야 한다. |

코드는 브라우저 주소창을 거치므로 방문 기록, 서버 로그에 남을 수 있다. 위 성질들 덕분에 코드가 노출되더라도 그것만으로는 사용자 데이터에 접근할 수 없다.

**사용자가 거부했을 때**
```http
HTTP/1.1 302 
Location: http://localhost:8080/login/oauth2/code/google?error=access_denied&state=Zx4k9Qm2...
```
| `error` 값 | 의미 |
|---|---|
| `access_denied` | 사용자가 거부함 |
| `invalid_scope` | 잘못된 범위 |
| `unauthorized_client` | 이 클라이언트는 이 방식을 쓸 수 없음 |
| `server_error`, `temporarily_unavailable` | 인가 서버 문제 |

### ⑥ 브라우저가 코드를 클라이언트에 전달한다
```http
GET /login/oauth2/code/google?state=Zx4k9Qm2...&code=4%2F0AVMBsJh...&scope=... HTTP/1.1
Host: localhost:8080
Cookie: oauth2_auth_request=rO0ABXNy...
```
Spring Security의 `OAuth2LoginAuthenticationFilter`가 이 주소(`/login/oauth2/code/*`)를 처리한다.

**클라이언트의 `state` 검증**
1. `oauth2_auth_request` 쿠키에서 ②에서 저장한 권한 요청을 꺼낸다.
2. 저장된 `state`와 받은 `state`가 같은지 비교한다.
3. 다르거나 저장된 요청이 없으면 거부한다(`authorization_request_not_found`, `invalid_state_parameter`).

> **`state`가 막는 공격 (로그인 CSRF)**
> 공격자가 **자기 계정**으로 ③~⑤를 진행해서 코드를 얻은 뒤, `http://localhost:8080/login/oauth2/code/google?code=공격자의코드` 링크를 피해자가 열게 만든다. `state` 검사가 없으면 피해자의 브라우저가 **공격자 계정으로 로그인**된다. 피해자가 그 상태로 글을 쓰거나 정보를 입력하면 공격자 계정에 쌓인다. 피해자 브라우저에는 이 요청을 시작한 기록(`state`가 담긴 쿠키나 세션)이 없으므로 `state` 비교에서 걸린다.

---

## 6. 액세스 토큰 응답 (⑦~⑧)

### ⑦ 클라이언트가 코드를 토큰으로 교환한다 (토큰 요청)
여기서부터는 **백 채널**이다. 블로그 서버가 Google 토큰 서버에 직접 HTTPS 요청을 보낸다.
```http
POST /oauth2/v4/token HTTP/1.1
Host: www.googleapis.com
Authorization: Basic MTIzNDU2Nzg5MDEyLWFiY2RlZmdoaWprbG1ub3AuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb206R09DU1BYLUV4YW1wbGVTZWNyZXRWYWx1ZTEyMzQ=
Content-Type: application/x-www-form-urlencoded;charset=UTF-8
Accept: application/json;charset=UTF-8

grant_type=authorization_code&code=4%2F0AVMBsJh...&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Fgoogle
```
**토큰 요청의 본문 파라미터 (RFC 6749 4.1.3)**

| 파라미터 | 필수 | 의미 |
|---|---|---|
| `grant_type` | 필수 | `authorization_code` (권한 부여 코드 방식) |
| `code` | 필수 | ⑤에서 받은 권한 부여 코드 |
| `redirect_uri` | ③에서 보냈다면 필수 | ③에서 보낸 값과 **같아야** 한다 |
| `client_id` | 클라이언트 인증을 안 할 때 필수 | 공개 클라이언트일 때 |
| `code_verifier` | PKCE 사용 시 | 8장 |

본문 형식은 JSON이 아니라 **폼 형식**(`application/x-www-form-urlencoded`)이다. RFC 6749가 그렇게 정했다.

**클라이언트 인증 방법**
토큰 요청에서 클라이언트는 자신이 등록된 그 클라이언트임을 증명해야 한다. 두 가지 방법이 있다.

| 방법 | 형식 |
|---|---|
| `client_secret_basic` | `Authorization: Basic Base64(client_id + ":" + client_secret)` 헤더 |
| `client_secret_post` | 본문에 `client_id=...&client_secret=...` 추가 |

Spring Security는 Google에 대해 **`client_secret_basic`**을 기본으로 쓴다. 위 예시의 `Authorization` 헤더를 디코딩하면 다음과 같다.
```
MTIzNDU2Nzg5MDEy...MTIzNA==
  → Base64 디코딩 →
123456789012-abcdefghijklmnop.apps.googleusercontent.com:GOCSPX-ExampleSecretValue1234
└──────────────────── client_id ────────────────────────┘ └────── client_secret ──────┘
```
Base64는 암호화가 아니므로 이 헤더는 **HTTPS로만** 보내야 한다. 백 채널은 브라우저를 거치지 않으므로 Client Secret이 사용자에게 노출되지 않는다.

**인가 서버의 검사**
1. `Authorization` 헤더의 `client_id`와 `client_secret`이 등록된 값과 일치하는가 → 아니면 `401 invalid_client`
2. `code`가 존재하고, 만료되지 않았고, 사용된 적이 없는가 → 아니면 `400 invalid_grant`
3. `code`가 **이 `client_id`에게** 발급된 것인가 → 아니면 `invalid_grant`
4. `redirect_uri`가 ③과 같은가 → 아니면 `invalid_grant`
5. (PKCE) `code_verifier`가 맞는가 → 아니면 `invalid_grant`

### ⑧ 인가 서버가 액세스 토큰을 응답한다
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache

{
  "access_token": "ya29.a0AeDClZB...example...",
  "expires_in": 3599,
  "scope": "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile openid",
  "token_type": "Bearer",
  "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6...example..."
}
```
**토큰 응답의 필드 (RFC 6749 5.1)**

| 필드 | 필수 | 의미 |
|---|---|---|
| `access_token` | 필수 | 리소스 서버에 보낼 액세스 토큰 |
| `token_type` | 필수 | 토큰 사용 방식. `Bearer` |
| `expires_in` | 권장 | 액세스 토큰 유효 시간(초). Google은 약 1시간 |
| `refresh_token` | 선택 | 리프레시 토큰. Google은 `access_type=offline`으로 요청했을 때 준다 |
| `scope` | 조건부 | 실제로 허락된 범위가 요청과 다를 때 |
| `id_token` | OIDC | `openid` 범위가 허락됐을 때 ID 토큰(JWT) |

`Cache-Control: no-store`, `Pragma: no-cache`는 RFC 6749가 토큰 응답에 요구하는 헤더다. 토큰이 중간 캐시에 저장되지 않게 한다.

Google의 액세스 토큰(`ya29.`로 시작)은 JWT가 아니라 **불투명 토큰**이다. 클라이언트는 그 내용을 해석하지 않고 그대로 리소스 서버에 보낸다([세션 기반 인증과 토큰 기반 인증](../13_jwt/01_session_vs_token_auth.md) 3장).

**오류 응답 (RFC 6749 5.2)**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{"error": "invalid_grant", "error_description": "Bad Request"}
```
| `error` | 의미 |
|---|---|
| `invalid_request` | 필수 파라미터 누락, 형식 오류 |
| `invalid_client` | 클라이언트 인증 실패 (Client ID/Secret 틀림). 401 |
| `invalid_grant` | 코드가 만료, 이미 사용됨, 다른 클라이언트의 코드, `redirect_uri` 불일치 |
| `unauthorized_client` | 이 클라이언트는 이 `grant_type`을 쓸 수 없음 |
| `unsupported_grant_type` | 지원하지 않는 `grant_type` |

---

## 7. 액세스 토큰으로 API 요청과 응답 (⑨~⑩)

### ⑨ 클라이언트가 리소스 서버에 요청한다
```http
GET /oauth2/v3/userinfo HTTP/1.1
Host: www.googleapis.com
Authorization: Bearer ya29.a0AeDClZB...example...
Accept: application/json
```
[토큰 기반 인증의 흐름과 HTTP 메시지 형식](../13_jwt/02_token_auth_flow_and_http_message.md)에서 본 `Bearer` 형식(RFC 6750) 그대로다.

**리소스 서버의 검사**
1. 토큰이 유효한가 (존재, 만료되지 않음, 취소되지 않음)
2. 토큰에 이 API를 호출할 범위가 있는가 (`userinfo`는 `email`/`profile`/`openid`)
3. 토큰이 가리키는 사용자의 데이터만 돌려준다

### ⑩ 리소스 서버가 데이터를 응답한다
```http
HTTP/1.1 200 OK
Content-Type: application/json; charset=UTF-8

{
  "sub": "110248495921238986420",
  "name": "홍길동",
  "given_name": "길동",
  "family_name": "홍",
  "picture": "https://lh3.googleusercontent.com/a/example",
  "email": "a@gmail.com",
  "email_verified": true
}
```
| 필드 | 범위 | 의미 |
|---|---|---|
| `sub` | (항상) | Google 계정의 **고유 번호**. 이메일이 바뀌어도 변하지 않는다 |
| `email`, `email_verified` | `email` | 이메일과 인증 여부 |
| `name`, `given_name`, `family_name`, `picture` | `profile` | 이름, 프로필 사진 |

**오류 응답**
```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer realm="https://accounts.google.com/", error="invalid_token"
```

### ⑪ 클라이언트가 로그인을 마무리한다
클라이언트는 받은 사용자 정보로 회원을 조회하거나 새로 만들고, **자기 서비스의 로그인 상태**를 만든다. 이 프로젝트에서는 자체 JWT(액세스 토큰, 리프레시 토큰)를 발급한다.

Google의 액세스 토큰과 블로그의 JWT는 **서로 다른 토큰**이다.
| | Google 액세스 토큰 | 블로그 JWT |
|---|---|---|
| 발급자 | Google | 블로그 서버 |
| 용도 | 블로그 서버가 Google API를 호출 | 브라우저가 블로그 API를 호출 |
| 쓰는 시점 | ⑨ 한 번 | 로그인 이후 계속 |
| 브라우저에 전달 | 안 됨 (백 채널에서만) | 됨 |

---

## 8. PKCE (Proof Key for Code Exchange)
RFC 7636. 코드를 가로챈 사람이 그 코드를 토큰으로 바꾸지 못하게 하는 방법이다. Client Secret을 안전하게 보관할 수 없는 **공개 클라이언트**(SPA, 모바일 앱)를 위해 만들어졌고, 지금은 모든 클라이언트에 권장된다.

**과정**
```
1. 클라이언트가 임의의 문자열 code_verifier를 만든다 (43~128글자)
   dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk

2. code_challenge = BASE64URL( SHA-256( code_verifier ) )
   E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM

3. ③ 권한 요청에 code_challenge, code_challenge_method=S256을 붙인다
   → 인가 서버가 코드와 함께 code_challenge를 기억한다

4. ⑦ 토큰 요청에 code_verifier 원문을 붙인다
   → 인가 서버가 SHA-256(code_verifier)를 계산해서 기억한 code_challenge와 비교한다
```
코드를 가로챈 공격자는 `code_challenge`만 볼 수 있다. SHA-256은 단방향이라 `code_verifier`를 알아낼 수 없으므로 토큰으로 바꿀 수 없다.

Spring Security는 Client Secret이 없는 클라이언트(`client-authentication-method: none`)이거나 PKCE 사용을 설정했을 때 PKCE를 적용한다. 이 프로젝트처럼 Client Secret을 쓰는 서버 클라이언트에는 기본으로 적용하지 않는다.

---

## 9. 왜 코드를 거쳐서 토큰을 받는가
인가 서버가 ⑤에서 토큰을 바로 주면(암묵적 방식) 단계가 줄어든다. 그래도 코드를 거치는 이유는 다음과 같다.

| 이유 | 설명 |
|---|---|
| 토큰이 브라우저를 거치지 않는다 | 프론트 채널(주소창, 방문 기록, 로그, 브라우저 확장 프로그램)에는 코드만 남는다. 토큰은 백 채널로만 전달된다. |
| 클라이언트를 인증할 수 있다 | ⑦에서 Client Secret으로 "토큰을 받는 쪽이 정말 등록된 그 클라이언트인지" 확인한다. 리다이렉트만으로는 받는 쪽을 확인할 수 없다. |
| 코드는 쓸모가 제한된다 | 짧은 수명, 일회용, 클라이언트에 묶임, Client Secret(또는 PKCE)이 있어야 교환 가능 |

## 10. Spring Security에서의 처리
| 단계 | 처리하는 구성 요소 | 개발자가 바꿀 수 있는 지점 |
|---|---|---|
| ①② 권한 요청 만들기 | `OAuth2AuthorizationRequestRedirectFilter` | `authorizationRequestRepository` (요청 저장 위치) |
| ⑥ 코드 받기, `state` 검증 | `OAuth2LoginAuthenticationFilter` | |
| ⑦⑧ 토큰 교환 | `OAuth2LoginAuthenticationProvider` → `RestClientAuthorizationCodeTokenResponseClient` | `tokenEndpoint` |
| ⑨⑩ 사용자 정보 | `DefaultOAuth2UserService` (OIDC면 `OidcUserService`) | `userInfoEndpoint().userService(...)` |
| ⑪ 로그인 마무리 | `AuthenticationSuccessHandler` | `successHandler(...)` |

Google 기본값(Spring Security `CommonOAuth2Provider.GOOGLE`):
| 항목 | 값 |
|---|---|
| 권한 요청 주소 | `https://accounts.google.com/o/oauth2/v2/auth` |
| 토큰 주소 | `https://www.googleapis.com/oauth2/v4/token` |
| 사용자 정보 주소 | `https://www.googleapis.com/oauth2/v3/userinfo` |
| 사용자 이름 속성 | `sub` |
| 리다이렉트 주소 | `{baseUrl}/login/oauth2/code/{registrationId}` |
| 기본 범위 | `openid`, `profile`, `email` (이 프로젝트는 `email`, `profile`로 바꿈) |
| 클라이언트 인증 | `client_secret_basic` |

`application.yml`에는 `client-id`, `client-secret`, `scope`만 적으면 나머지는 이 기본값이 채워진다.

## 정리
| 단계 | 채널 | 메시지 | 핵심 필드 |
|---|---|---|---|
| 권한 요청 | 프론트 | `GET /o/oauth2/v2/auth?...` | `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state` |
| 권한 부여 | 사용자 ↔ 인가 서버 | 로그인, 동의 화면 | |
| 인증 코드 제공 | 프론트 | `302 redirect_uri?code=...&state=...` | `code`, `state` |
| 토큰 요청 | 백 | `POST /token` (폼) | `grant_type`, `code`, `redirect_uri` + `Authorization: Basic` |
| 토큰 응답 | 백 | `200` JSON | `access_token`, `token_type`, `expires_in` |
| API 요청 | 백 | `GET /userinfo` | `Authorization: Bearer` |
| API 응답 | 백 | `200` JSON | `sub`, `email`, `name` |
