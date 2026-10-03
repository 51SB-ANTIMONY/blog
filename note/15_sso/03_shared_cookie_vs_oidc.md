---
title: 공유 쿠키 방식과 OpenID Connect - 왜 OIDC인가
date: 2026-10-03
tags: [sso, oidc, cookie, security, design]
---

# 공유 쿠키 방식과 OpenID Connect - 왜 OIDC인가

> 쿠키의 `Domain`, `SameSite` 속성은 [쿠키](../14_oauth/03_cookie.md), OAuth 2.0과 권한 부여 코드 방식은 [OAuth 2.0이란 무엇인가](../14_oauth/01_oauth.md)와 [권한 부여 코드 승인 방식](../14_oauth/02_authorization_code_grant.md), 대형 서비스의 사례는 [대형 서비스의 통합 로그인(SSO) 구조](./01_big_tech_sso.md)에서 설명했다.

## 1. 풀어야 할 문제
antimony.kr에는 앱이 여러 개 생긴다.
```
accounts.antimony.kr   로그인 담당
blog.antimony.kr       블로그
app2.antimony.kr       다른 저장소에서 만든 앱
```
사용자가 accounts에서 한 번 로그인하면, blog와 app2도 **이 사용자가 누구인지** 알아야 한다. 그런데 브라우저는 앱마다 다른 출처로 보고, 각 앱 서버는 서로의 메모리를 볼 수 없다. "accounts가 확인한 로그인 결과를 다른 앱에 어떻게 전달하는가"가 문제다.

방법은 크게 두 가지다.
| 방법 | 핵심 아이디어 |
|---|---|
| **공유 쿠키** | 로그인 결과를 담은 쿠키를 **모든 하위 도메인이 함께 받도록** 설정한다 |
| **OIDC** | 각 앱이 필요할 때 accounts에 **물어보고**, accounts가 그 앱 전용의 증명서(토큰)를 준다 |

---

## 2. 공유 쿠키 방식

### 원리
쿠키에 `Domain=antimony.kr` 속성을 붙이면, 브라우저는 그 쿠키를 `antimony.kr`과 **모든 하위 도메인**으로 가는 요청에 붙인다([쿠키](../14_oauth/03_cookie.md) 3장, 5장).
```http
HTTP/1.1 302 
Set-Cookie: AUTH=<로그인 정보>; Domain=antimony.kr; Path=/; HttpOnly; Secure; SameSite=Lax
Location: https://blog.antimony.kr/
```
이후 브라우저의 동작:
```
GET https://blog.antimony.kr/api/articles     Cookie: AUTH=...   ← 붙음
GET https://app2.antimony.kr/                 Cookie: AUTH=...   ← 붙음
GET https://accounts.antimony.kr/             Cookie: AUTH=...   ← 붙음
GET https://아무거나.antimony.kr/              Cookie: AUTH=...   ← 붙음
```
각 앱은 요청에 실려 온 `AUTH` 쿠키를 검사해서 사용자를 알아낸다. 사용자를 다른 곳으로 보낼 필요가 없다.

### 쿠키에 무엇을 담는가: 두 가지 방식
**방식 A. 세션 ID + 공유 세션 저장소**
```
AUTH=8f3a...(임의의 세션 ID)

           ┌──────────── Redis (공유 세션 저장소) ────────────┐
           │ "8f3a..." → { userId: 1, nickname: "...", ... }  │
           └────▲───────────────▲────────────────▲───────────┘
             accounts          blog             app2
```
- 모든 앱이 **같은 세션 저장소**에 접속해서 세션 ID로 사용자 정보를 꺼낸다.
- Spring Session 같은 도구로 구성한다.

**방식 B. 서명된 토큰을 쿠키에 담기**
```
AUTH=eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.Xb3k...(JWT)
```
- accounts가 JWT를 만들어 쿠키에 넣는다.
- 각 앱은 accounts의 공개 키로 서명을 검증한다([JWT의 서명과 검증](../13_jwt/05_jwt_signature_and_validation.md)).
- 공유 저장소가 필요 없다.

### 장점
| 장점 | 설명 |
|---|---|
| 빠르다 | 다른 앱으로 처음 들어갈 때도 리다이렉트가 없다. 첫 요청부터 로그인 상태다 |
| 구조가 단순하다 | 쿠키 하나만 설정하면 된다. 클라이언트 등록, 코드 교환 같은 절차가 없다 |
| 전체 로그아웃이 쉽다 | 공유 쿠키 하나를 지우면 모든 앱에서 로그아웃된다 (방식 A는 세션도 삭제) |

### 단점과 구체적인 위험

**① 모든 하위 도메인이 로그인 쿠키를 받는다**
쿠키는 `*.antimony.kr` **전부**에 전송된다. 내가 관리하는 앱만이 아니다.
- `test.antimony.kr`: 보안 설정을 느슨하게 둔 테스트 서버
- `status.antimony.kr`: 외부 서비스(상태 페이지, 블로그 호스팅 등)에 CNAME으로 연결한 하위 도메인. **외부 회사의 서버가 로그인 쿠키를 받는다**
- `old.antimony.kr`: 예전에 쓰던 서버. 지금은 관리하지 않음

> **CNAME이란?**
> DNS 기록의 한 종류로, "이 도메인 이름은 저 도메인 이름과 같다"고 연결하는 것이다. `status.antimony.kr`을 외부 서비스의 주소에 CNAME으로 연결하면, 그 하위 도메인으로 가는 요청은 외부 서비스의 서버가 받는다.

이 중 하나만 뚫려도 공격자는 그 서버로 들어오는 **모든 사용자의 로그인 쿠키**를 모을 수 있다. `HttpOnly`는 JavaScript가 읽는 것만 막을 뿐, **서버가 받는 것**은 막지 못한다.

**② 하위 도메인 탈취 (Subdomain Takeover)**
`old.antimony.kr`이 외부 호스팅 서비스를 CNAME으로 가리키는데 그 서비스 계정을 해지했다고 하자. DNS 기록은 남아 있다. 공격자가 그 호스팅 서비스에서 같은 이름을 새로 등록하면 `old.antimony.kr`을 차지하게 된다. 이제 공격자의 서버가 공유 쿠키를 받는다.

**③ 쿠키 덮어쓰기 (Cookie Tossing)**
어떤 하위 도메인이든 `Domain=antimony.kr` 쿠키를 **설정할 수도** 있다.
```http
# 공격자가 제어하게 된 test.antimony.kr의 응답
Set-Cookie: AUTH=<공격자 계정의 로그인 값>; Domain=antimony.kr; Path=/api
```
- 피해자 브라우저에 같은 이름의 쿠키가 하나 더 생긴다.
- `Path=/api`처럼 경로를 더 길게 주면 그 경로에서는 공격자 쿠키가 **앞에** 전송된다.
- 그 결과 피해자는 모르는 사이에 **공격자 계정으로 로그인된 상태**에서 글을 쓰거나 정보를 입력하게 된다(세션 고정, 로그인 CSRF).

이것을 막는 `__Host-` 쿠키 접두사는 `Domain` 속성을 금지한다([쿠키](../14_oauth/03_cookie.md) 9장). 즉 **공유 쿠키에는 `__Host-`를 쓸 수 없다.**

**④ `SameSite`가 앱 사이를 지켜 주지 못한다**
`SameSite`는 **사이트** 단위로 동작하고, `*.antimony.kr`은 모두 **같은 사이트**다([쿠키](../14_oauth/03_cookie.md) 6장). 따라서 `app2.antimony.kr`에 XSS가 생기면, 그 스크립트가 `blog.antimony.kr/api/...`로 보내는 요청에도 공유 쿠키가 붙는다. 앱 하나의 취약점이 **모든 앱의 CSRF 위험**이 된다.

**⑤ 앱마다 권한을 나눌 수 없다**
쿠키 하나가 모든 앱에서 통한다.
- blog 서버가 해킹되면, blog가 받은 쿠키로 app2에도 요청할 수 있다.
- "이 앱에는 이메일까지만, 저 앱에는 결제 정보까지" 같은 앱별 범위를 표현할 수 없다.
- 문제가 생긴 앱 하나만 로그인을 끊을 수 없다.

**⑥ 모든 앱이 같은 형식에 묶인다**
- 방식 A: 모든 앱이 **같은 Redis에 접속**하고, **같은 세션 직렬화 형식**을 읽어야 한다. Java로 만든 세션을 Node.js 앱이 읽으려면 형식을 따로 맞춰야 한다.
- 방식 B: 쿠키 이름, 토큰 형식, 클레임 이름을 앱들이 **비공식 약속**으로 맞춰야 한다. 표준이 없으므로 앱을 만들 때마다 직접 구현한다.

**⑦ antimony.kr 밖으로 확장할 수 없다**
쿠키는 다른 도메인으로 전송되지 않는다. `antimony.com`, 모바일 앱, 다른 회사의 서비스에는 같은 방법을 쓸 수 없다.

### 공유 쿠키가 괜찮은 경우
다음을 **모두** 만족하면 공유 쿠키도 합리적인 선택이다.
- 모든 앱이 같은 상위 도메인 아래에 있다.
- 모든 하위 도메인을 직접 관리하고, 외부 서비스에 CNAME으로 연결한 하위 도메인이 없다.
- 모든 앱을 같은 팀이 같은 기술로 만든다.
- 앱별 권한 구분이 필요 없다.
- 외부 도메인, 모바일 앱, 외부 회사와 연동할 계획이 없다.

---

## 3. OpenID Connect (OIDC)

### 정의
**OpenID Connect는 OAuth 2.0 위에 "사용자 인증" 기능을 표준으로 추가한 프로토콜**이다. OpenID Foundation이 2014년에 OpenID Connect Core 1.0으로 발표했다.

[OAuth 2.0이란 무엇인가](../14_oauth/01_oauth.md)에서 본 것처럼 OAuth 2.0은 "이 앱이 이 데이터에 접근해도 된다"는 **인가** 표준이다. "이 사용자가 누구인가"를 앱에 알려 주는 방법은 정해 두지 않았다. 그래서 OAuth 2.0만으로 로그인을 만들면 서비스마다 사용자 정보 API의 주소, 필드 이름, 검증 방법이 제각각이다.

OIDC는 이 부분을 표준으로 정한다.

| OIDC가 추가한 것 | 내용 |
|---|---|
| **ID 토큰** | "이 사용자는 누구이고, 언제, 어떻게 인증했는가"를 담은 **서명된 JWT** |
| `openid` scope | 권한 요청에 이 범위가 있으면 OIDC 요청이 된다 |
| 표준 클레임 | `sub`, `name`, `email`, `email_verified`, `picture` 등 사용자 정보의 이름을 통일 |
| UserInfo 엔드포인트 | 사용자 정보를 돌려주는 표준 API |
| Discovery | `/.well-known/openid-configuration` 설정 문서 |
| 로그아웃 규격 | RP-Initiated Logout, Front-Channel Logout, Back-Channel Logout |
| 세션 확인 | `prompt=none`으로 화면 없이 로그인 여부 확인 |

### 역할 이름
| OAuth 2.0 | OIDC | antimony.kr |
|---|---|---|
| 인가 서버 | **OP** (OpenID Provider) | `accounts.antimony.kr` |
| 클라이언트 | **RP** (Relying Party) | `blog.antimony.kr`, `app2.antimony.kr` |

### 흐름 (권한 부여 코드 방식)
OAuth 2.0의 권한 부여 코드 방식과 같고, 몇 가지가 추가된다.
```
① blog → 302 https://accounts.antimony.kr/oauth2/authorize
           ?response_type=code
           &client_id=blog
           &redirect_uri=https://blog.antimony.kr/login/oauth2/code/accounts
           &scope=openid profile email          ← openid 포함
           &state=...
           &nonce=n-0S6_WzA2Mj                   ← OIDC 추가
           &code_challenge=...&code_challenge_method=S256

② accounts: 세션 확인 → 없으면 로그인 화면 / 있으면 바로 ③
③ accounts → 302 https://blog.antimony.kr/login/oauth2/code/accounts?code=...&state=...
④ blog → POST https://accounts.antimony.kr/oauth2/token  (code, code_verifier, 클라이언트 인증)
⑤ accounts → 200
   {
     "access_token": "eyJ...",
     "token_type": "Bearer",
     "expires_in": 300,
     "refresh_token": "...",
     "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjIwMjYtMTAifQ.eyJpc3Mi...(서명)"   ← OIDC 추가
   }
⑥ blog: ID 토큰 검증 → 로그인 완료
```

### ID 토큰
⑤의 `id_token`을 디코딩한 페이로드 예시:
```json
{
  "iss": "https://accounts.antimony.kr",
  "sub": "1",
  "aud": "blog",
  "exp": 1791000300,
  "iat": 1791000000,
  "auth_time": 1790999950,
  "nonce": "n-0S6_WzA2Mj",
  "amr": ["pwd"],
  "nickname": "antimony",
  "email": "a@antimony.kr",
  "email_verified": true
}
```
| 클레임 | 필수 | 의미 |
|---|---|---|
| `iss` | 필수 | 발급한 OP. `https://accounts.antimony.kr` |
| `sub` | 필수 | 사용자 식별자. OP 안에서 겹치지 않고 바뀌지 않는다 |
| `aud` | 필수 | 이 토큰을 받을 RP의 `client_id`. **이 토큰은 blog 전용**이라는 뜻 |
| `exp`, `iat` | 필수 | 만료 시각, 발급 시각 |
| `auth_time` | 조건부 | 사용자가 실제로 로그인한 시각. SSO로 세션을 재사용했다면 과거 시각이 된다 |
| `nonce` | 요청에 있었으면 필수 | ①에서 RP가 보낸 값 그대로 |
| `acr`, `amr` | 선택 | 인증 수준, 인증 방법(`pwd`, `otp`, `fed` 등) |
| `azp` | 조건부 | 토큰을 요청한 클라이언트. `aud`가 여러 개일 때 |

### ID 토큰 검증 (OpenID Connect Core 3.1.3.7)
RP는 ID 토큰을 받으면 다음을 모두 확인한다.
1. `iss`가 기대하는 OP(`https://accounts.antimony.kr`)와 정확히 같다
2. `aud`에 **자기 `client_id`**가 들어 있다
3. 서명이 OP의 공개 키(JWKS)로 검증된다. 알고리즘은 미리 정한 것(`RS256`)만 허용한다
4. `exp`가 지나지 않았다
5. `nonce`가 ①에서 보낸 값과 같다

2번이 공유 쿠키와의 핵심 차이다. **blog용 ID 토큰은 app2에서 쓸 수 없다.** app2는 `aud`에 `app2`가 없는 토큰을 거부한다.

> **`nonce`란?**
> RP가 권한 요청마다 새로 만드는 임의의 값이다. OP는 이 값을 ID 토큰에 그대로 넣는다. RP는 받은 ID 토큰의 `nonce`가 방금 자기가 보낸 값인지 확인한다. 다른 곳에서 훔친 ID 토큰을 다시 들이미는 공격(재사용, replay)을 막는다. `state`가 "이 응답이 내가 시작한 요청의 응답인가"를 확인한다면, `nonce`는 "이 ID 토큰이 내 요청을 위해 발급된 것인가"를 확인한다.

### ID 토큰과 액세스 토큰의 차이
| | ID 토큰 | 액세스 토큰 |
|---|---|---|
| 받는 쪽 | RP (로그인하는 앱) | 리소스 서버 (API) |
| 질문 | "이 사용자는 누구인가?" | "이 요청이 이 API를 써도 되는가?" |
| `aud` | RP의 `client_id` | API의 식별자 |
| API 호출에 쓰는가 | 쓰지 않는다 | `Authorization: Bearer`로 쓴다 |

### Discovery: 설정 문서
OP는 `https://accounts.antimony.kr/.well-known/openid-configuration`에 설정을 공개한다.
```json
{
  "issuer": "https://accounts.antimony.kr",
  "authorization_endpoint": "https://accounts.antimony.kr/oauth2/authorize",
  "token_endpoint": "https://accounts.antimony.kr/oauth2/token",
  "jwks_uri": "https://accounts.antimony.kr/oauth2/jwks",
  "userinfo_endpoint": "https://accounts.antimony.kr/userinfo",
  "end_session_endpoint": "https://accounts.antimony.kr/connect/logout",
  "scopes_supported": ["openid", "profile", "email"],
  "response_types_supported": ["code"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "code_challenge_methods_supported": ["S256"]
}
```
RP는 `issuer` 주소 하나만 설정하면 이 문서를 읽어 나머지를 채운다. Spring Boot 앱이라면 다음 설정이면 된다.
```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          accounts:
            issuer-uri: https://accounts.antimony.kr
        registration:
          accounts:
            client-id: blog
            client-secret: ${ACCOUNTS_CLIENT_SECRET}
            scope: openid, profile, email
```
다른 언어의 앱도 각자의 OIDC 라이브러리에 같은 `issuer`를 넣으면 된다. 대형 서비스들의 설정 문서는 [대형 서비스의 통합 로그인(SSO) 구조](./01_big_tech_sso.md)에 정리했다.

### OIDC에서 SSO가 되는 원리
SSO의 근거는 **accounts의 로그인 세션**이다. 이 세션 쿠키는 `Domain` 속성 없이 **`accounts.antimony.kr`에만** 저장된다.
```
Set-Cookie: SESSION=...; Path=/; HttpOnly; Secure; SameSite=Lax      (Domain 없음)
```
- blog, app2는 이 쿠키를 받지 못한다. 받을 필요도 없다.
- app2가 ①처럼 사용자를 accounts로 보내면, **그 요청에만** 세션 쿠키가 붙는다.
- accounts는 세션을 확인하고 로그인 화면 없이 app2 전용 코드를 준다.

공유 쿠키 방식에서 "모든 앱이 로그인 쿠키를 받는다"였던 것이, OIDC에서는 "**accounts만** 로그인 세션을 갖고, 각 앱은 **자기 전용** 토큰을 받는다"로 바뀐다.

### 화면 없이 로그인 여부 확인: `prompt=none`
권한 요청에 `prompt=none`을 붙이면 OP는 **어떤 화면도 보여 주지 않는다.**
| accounts 세션 | 결과 |
|---|---|
| 있음 | 바로 코드와 함께 돌아옴 → 로그인 |
| 없음 | `error=login_required`와 함께 돌아옴 → 로그인 버튼 표시 |

앱이 첫 화면에서 "혹시 이미 로그인되어 있나?"를 사용자 모르게 확인하는 데 쓴다.

| 다른 `prompt` 값 | 의미 |
|---|---|
| `login` | 세션이 있어도 다시 로그인 화면 |
| `consent` | 동의 화면을 다시 보여 줌 |
| `select_account` | 계정 선택 화면 |

### 로그아웃 규격
| 규격 | 동작 |
|---|---|
| **RP-Initiated Logout** | 앱이 사용자를 `end_session_endpoint`로 보낸다(`id_token_hint`, `post_logout_redirect_uri` 포함). OP가 자기 세션을 지우고 앱으로 돌려보낸다 |
| **Back-Channel Logout** | OP가 로그아웃 사실을 각 RP 서버에 **서버 간 요청**으로 알린다. 다른 앱의 세션도 지울 수 있다 |
| Front-Channel Logout | OP의 로그아웃 페이지가 각 RP의 로그아웃 주소를 숨은 `iframe`으로 호출한다. 브라우저의 서드 파티 쿠키 차단 때문에 점점 쓰기 어려워지고 있다 |

---

## 4. 두 방식 비교
| 항목 | 공유 쿠키 | OIDC |
|---|---|---|
| 로그인 정보를 받는 곳 | **모든** `*.antimony.kr` | accounts만 (세션), 각 앱은 자기 토큰만 |
| 앱이 다른 앱으로 처음 들어갈 때 | 리다이렉트 없음 | accounts를 거치는 리다이렉트 1회 (화면 없음) |
| 하위 도메인 하나가 뚫리면 | 모든 사용자의 로그인 쿠키 노출 | 그 앱의 토큰만 영향 |
| 쿠키 덮어쓰기 | 가능 (`__Host-` 사용 불가) | accounts 세션 쿠키에 `__Host-` 사용 가능 |
| 앱 간 CSRF | `SameSite`로 막을 수 없음 | 각 앱이 자기 쿠키와 CSRF 방어를 가짐 |
| 앱별 권한, 앱별 차단 | 불가능 | `aud`, `scope`, 클라이언트별 비활성화 |
| 토큰 재사용 | 한 쿠키가 모든 앱에서 유효 | `aud` 검사로 다른 앱의 토큰 거부 |
| 다른 언어·프레임워크 | 형식을 직접 맞춰 구현 | 표준 OIDC 라이브러리 |
| 다른 도메인, 모바일 앱 | 불가능 | 가능 |
| 전체 로그아웃 | 쿠키 하나 삭제 | RP-Initiated / Back-Channel Logout 구현 필요 |
| 구현 난이도 | 낮음 | 높음 (인가 서버 필요. Spring Authorization Server로 줄일 수 있음) |

## 5. 대형 서비스는 두 방식을 함께 쓴다
| 범위 | 방식 |
|---|---|
| 같은 상위 도메인, 직접 관리하는 자사 서비스 | 상위 도메인 쿠키 (네이버의 `*.naver.com`, Google의 `*.google.com`) |
| 상위 도메인이 다른 자사 서비스 | 계정 도메인을 거치는 리다이렉트 교환 (Google의 `youtube.com`) |
| 외부 서비스 | 표준 OIDC |

대형 서비스가 같은 도메인 안에서 쿠키를 공유할 수 있는 것은, 수많은 하위 도메인을 전담 조직이 관리하고 쿠키 보호 장치(서명, 암호화, 여러 쿠키의 조합)를 따로 갖추고 있기 때문이다.

## 6. 왜 antimony.kr은 OIDC인가
| antimony.kr의 조건 | 판단 |
|---|---|
| **다른 저장소에서 만든 앱**도 붙인다 | 앱마다 기술이 다를 수 있다 → 표준이 필요 → OIDC |
| 앱이 계속 늘어난다 | 앱 하나의 취약점이 전체로 번지면 안 된다 → 앱별 토큰(`aud`) |
| 테스트용, 외부 서비스용 하위 도메인이 생길 수 있다 | 모든 하위 도메인이 로그인 쿠키를 받으면 위험 |
| 프론트엔드(SPA), 나중에 모바일 앱 가능성 | 쿠키 공유로는 모바일 앱을 붙일 수 없다 → OIDC + PKCE |
| 구현 비용 | Spring Authorization Server가 OP 기능을 제공한다 |
| 리다이렉트 1회의 비용 | 앱에 처음 들어갈 때만 생기고, 화면이 없어 사용자는 거의 느끼지 못한다 |

**결론:** 공유 쿠키는 빠르고 단순하지만, 모든 하위 도메인을 하나의 보안 경계로 묶는다. antimony.kr은 앱이 늘어나고 다른 저장소의 앱까지 받을 예정이므로, **로그인 세션은 accounts에만 두고 각 앱에는 그 앱 전용 토큰을 주는 OIDC**를 쓴다.

## 정리
| 항목 | 핵심 |
|---|---|
| 공유 쿠키 | `Domain=antimony.kr` 쿠키를 모든 하위 도메인이 받는다. 빠르고 단순하지만 하위 도메인 하나의 문제가 전체로 번진다 |
| OIDC | OAuth 2.0 + ID 토큰 + 표준 클레임 + Discovery + 로그아웃 규격. 로그인을 표준화한 프로토콜 |
| ID 토큰 | 서명된 JWT. `iss`, `sub`, `aud`, `exp`, `nonce`. `aud` 덕분에 앱마다 분리된다 |
| OIDC의 SSO | accounts만 로그인 세션을 갖고, 각 앱은 리다이렉트로 자기 전용 토큰을 받는다 |
| 선택 | antimony.kr은 앱별 분리, 다른 저장소의 앱, 확장성 때문에 OIDC |
