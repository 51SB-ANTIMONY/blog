---
title: 쿠키 (Cookie)
date: 2026-10-03
tags: [cookie, http, security, samesite, oauth]
---

# 쿠키 (Cookie)

> HTTP 메시지와 헤더의 형식은 [토큰 기반 인증의 흐름과 HTTP 메시지 형식](../13_jwt/02_token_auth_flow_and_http_message.md)에서 설명했다.
> 쿠키의 기본 개념과 세션 방식은 [인증과 인가](../12_spring_security/01_authentication_and_authorization.md) 6장에서 간단히 다뤘다. 이 노트는 그 내용을 자세히 다룬다.

## 1. 쿠키란
**서버가 브라우저에 저장하도록 보내는 `이름=값` 데이터이고, 브라우저는 저장한 쿠키를 조건에 맞는 이후 요청마다 자동으로 다시 보낸다.** RFC 6265에 정의되어 있다.

HTTP는 요청끼리 연결되지 않는다(무상태). 쿠키는 브라우저 쪽에 작은 상태를 남겨서 요청들을 이어 주는 수단이다.

| 용도 | 예시 |
|---|---|
| 로그인 상태 유지 | 세션 ID (`JSESSIONID`), 리프레시 토큰 |
| 진행 중인 작업 정보 | OAuth2 권한 요청 정보, 장바구니 |
| 사용자 설정 | 언어, 다크 모드 |
| 추적 | 광고, 방문 분석 |

## 2. 쿠키가 오가는 과정
```
① 서버 → 브라우저: 응답 헤더 Set-Cookie
   HTTP/1.1 200
   Set-Cookie: refresh_token=eyJ0eXAi...; Max-Age=1209600; Path=/; HttpOnly; Secure; SameSite=Lax

② 브라우저: 쿠키 저장소(cookie jar)에 기록
   이름, 값, 도메인, 경로, 만료 시각, 속성들

③ 브라우저 → 서버: 이후 요청마다 조건에 맞는 쿠키를 Cookie 헤더에 붙임
   GET /api/articles HTTP/1.1
   Cookie: refresh_token=eyJ0eXAi...
```
JavaScript 코드가 아무것도 하지 않아도 ③이 일어난다. 이것이 쿠키의 가장 큰 특징이자, CSRF 같은 공격이 생기는 원인이다.

## 3. `Set-Cookie` 헤더의 형식
```
Set-Cookie: <이름>=<값> *( "; " <속성> )
```
**응답 하나에 쿠키 하나씩** 쓴다. 쿠키가 여러 개면 `Set-Cookie` 헤더를 여러 줄 보낸다.
```http
HTTP/1.1 302 
Set-Cookie: refresh_token=eyJ0eXAi...; Max-Age=1209600; Path=/
Set-Cookie: oauth2_auth_request=; Max-Age=0; Path=/
Location: /articles?token=...
```

### 이름과 값에 쓸 수 있는 문자
| 부분 | 규칙 |
|---|---|
| 이름 | 영문자, 숫자, 일부 기호. 공백, `=`, `;`, `,`, `"`, 괄호 등은 안 된다. |
| 값 | 공백, `"`, `,`, `;`, `\`, 제어 문자는 안 된다. 필요하면 큰따옴표로 감싸거나 인코딩한다. |

JWT는 영문자, 숫자, `-`, `_`, `.`만 쓰므로 그대로 넣을 수 있다. 이 프로젝트의 `CookieUtil.serialize()`가 객체를 **Base64URL**로 바꿔 쿠키에 넣는 것도 이 규칙 때문이다. 일반 Base64의 `+`, `/`, `=`도 대부분 허용되지만, URL과 겹치는 문자를 피하려고 Base64URL을 쓴다.

### 속성
| 속성 | 예시 | 의미 | 없을 때 |
|---|---|---|---|
| `Expires` | `Expires=Sat, 17 Oct 2026 03:00:00 GMT` | 이 시각에 삭제 | |
| `Max-Age` | `Max-Age=1209600` | 지금부터 이 초가 지나면 삭제. `0` 이하면 **즉시 삭제** | `Expires`도 없으면 **세션 쿠키** (브라우저를 닫으면 삭제) |
| `Domain` | `Domain=blog.com` | 이 도메인과 **하위 도메인**(`api.blog.com`)으로 가는 요청에 보냄 | 쿠키를 준 **그 호스트에만** 보냄 (host-only) |
| `Path` | `Path=/api` | 경로가 이것으로 시작하는 요청에만 보냄 | 요청 경로의 디렉터리 부분 (`/api/articles/1` → `/api/articles`) |
| `Secure` | `Secure` | **HTTPS 요청에만** 보냄 | HTTP에도 보냄 |
| `HttpOnly` | `HttpOnly` | **JavaScript(`document.cookie`)로 읽을 수 없음** | JavaScript로 읽고 쓸 수 있음 |
| `SameSite` | `SameSite=Lax` | 다른 사이트에서 시작된 요청에 보낼지 (6장) | 브라우저 기본값 (Chrome은 `Lax`로 취급) |

`Expires`와 `Max-Age`가 둘 다 있으면 `Max-Age`가 우선한다.

**이 프로젝트에서 `Path`를 `/`로 정하는 이유:** `Path`를 생략하면 쿠키를 준 요청 경로를 기준으로 범위가 정해진다. `/login/oauth2/code/google` 요청에서 받은 쿠키는 `/login/oauth2/code` 아래로만 가고 `/api/token`에는 가지 않는다. `CookieUtil.addCookie()`가 항상 `setPath("/")`를 하는 이유다.

## 4. `Cookie` 헤더의 형식
```
Cookie: <이름>=<값> *( "; " <이름>=<값> )
```
```http
GET /api/token HTTP/1.1
Cookie: refresh_token=eyJ0eXAi...; oauth2_auth_request=rO0ABXNy...
```
- 요청에는 **이름과 값만** 간다. `Max-Age`, `Path`, `HttpOnly` 같은 속성은 서버에 다시 전달되지 않는다. 그래서 서버는 받은 쿠키가 언제 만료되는지, `HttpOnly`인지 알 수 없다.
- 여러 쿠키가 `; `로 이어진 **한 줄**로 간다.
- 같은 이름의 쿠키가 `Path`나 `Domain`만 다르게 여러 개 저장되어 있으면 모두 전송된다. 경로가 더 긴 것이 앞에 온다.

## 5. 브라우저가 쿠키를 보낼지 정하는 규칙
요청을 보낼 때, 저장된 쿠키마다 다음 조건을 **모두** 만족하면 `Cookie` 헤더에 넣는다.

| 검사 | 조건 |
|---|---|
| 만료 | 아직 만료되지 않았다 |
| 도메인 | host-only면 요청 호스트가 정확히 같다. `Domain`이 있으면 요청 호스트가 그 도메인이거나 하위 도메인이다 |
| 경로 | 요청 경로가 쿠키의 `Path`로 시작한다 (`Path=/api`면 `/api`, `/api/token`은 해당, `/apix`는 해당 안 됨) |
| `Secure` | `Secure` 쿠키면 요청이 HTTPS다 (`localhost`는 예외로 HTTP도 허용하는 브라우저가 많다) |
| `SameSite` | 6장의 규칙을 만족한다 |

**포트는 쿠키 범위에 포함되지 않는다.** `localhost:8080`이 준 쿠키는 `localhost:3000`으로 가는 요청에도 붙는다. 프론트엔드와 백엔드를 같은 호스트의 다른 포트로 띄울 때 알아 두어야 한다.

## 6. SameSite

### 사이트(Site)와 출처(Origin)
| 개념 | 구성 | `http://localhost:8080`과 `http://localhost:3000` | `https://blog.com`과 `https://api.blog.com` |
|---|---|---|---|
| 출처 (Origin) | 스킴 + 호스트 + 포트 | **다른 출처** | **다른 출처** |
| 사이트 (Site) | 스킴 + 등록 가능한 도메인 (`blog.com`) | **같은 사이트** | **같은 사이트** |

> **등록 가능한 도메인이란?**
> 도메인 등록 기관에서 살 수 있는 단위의 도메인이다. `api.blog.com`의 등록 가능한 도메인은 `blog.com`이다. `.com`, `.co.kr` 같은 공개 접미사 바로 앞까지를 말한다.

**다른 사이트에서 시작된 요청(cross-site request)**이란, 사용자가 보고 있는 페이지의 사이트와 요청이 가는 사이트가 다른 경우다. 예를 들어 `accounts.google.com` 페이지에서 `localhost:8080`으로 리다이렉트되는 것이 여기에 해당한다.

### 세 가지 값
| 값 | 다른 사이트에서 시작된 요청에 쿠키를 보내는가 |
|---|---|
| `Strict` | **보내지 않는다.** 다른 사이트의 링크를 눌러 들어와도 보내지 않는다. |
| `Lax` | 최상위 페이지 이동이면서 `GET`인 요청(링크 클릭, 리다이렉트)에는 **보낸다.** `<img>`, `<iframe>`, `fetch`, `POST` 폼 제출에는 보내지 않는다. |
| `None` | **항상 보낸다.** 반드시 `Secure`와 함께 써야 한다. |

`SameSite`를 지정하지 않으면 Chrome 등은 `Lax`로 취급한다.

### OAuth 로그인과 SameSite
⑤에서 Google이 `localhost:8080/login/oauth2/code/google`로 리다이렉트하는 요청은 **다른 사이트(`accounts.google.com`)에서 시작된 최상위 `GET` 이동**이다.
| `oauth2_auth_request` 쿠키의 `SameSite` | 결과 |
|---|---|
| `Lax` 또는 미지정 | 쿠키가 전송된다 → `state` 검증 성공 |
| `Strict` | 쿠키가 **전송되지 않는다** → 저장된 권한 요청을 찾지 못해 로그인 실패 |

그래서 OAuth 권한 요청을 담는 쿠키에는 `Strict`를 쓰면 안 된다.

## 7. 쿠키 삭제
쿠키를 지우는 별도의 명령은 없다. **같은 이름, 같은 `Domain`, 같은 `Path`**로 만료된 쿠키를 다시 보내면 브라우저가 지운다.
```http
Set-Cookie: refresh_token=; Max-Age=0; Path=/
```
`Path`가 다르면 다른 쿠키로 취급되어 지워지지 않는다. `CookieUtil.deleteCookie()`가 `setPath("/")`, `setMaxAge(0)`을 하는 이유다.

JavaScript에서 지울 때도 같은 원리다.
```javascript
document.cookie = 'refresh_token=; expires=Thu, 01 Jan 1970 00:00:01 GMT;';
```
`HttpOnly` 쿠키는 JavaScript로 지울 수 없다. 서버가 `Set-Cookie`로 지워야 한다.

## 8. 크기와 개수 제한
RFC 6265는 브라우저가 **최소한** 다음을 지원하도록 요구한다.

| 항목 | 최소 지원 |
|---|---|
| 쿠키 하나의 크기 (이름 + 값 + 속성) | 4096바이트 |
| 도메인당 쿠키 수 | 50개 |
| 전체 쿠키 수 | 3000개 |

실제 브라우저는 도메인당 약 180개(Chrome), 쿠키 하나당 약 4KB를 허용한다. 4KB를 넘는 쿠키는 **조용히 무시**된다. 저장이 안 됐다는 오류가 서버에 전달되지 않으므로 주의한다.

쿠키는 조건에 맞는 **모든 요청**에 붙는다. 큰 쿠키는 이미지, JS 파일 요청에까지 따라붙어 네트워크 사용량을 늘린다.

## 9. 쿠키 이름 접두사
| 접두사 | 브라우저가 강제하는 조건 |
|---|---|
| `__Secure-` | `Secure` 속성이 있어야 하고, HTTPS에서 설정해야 함 |
| `__Host-` | `Secure` 필수, `Domain` 금지(host-only), `Path=/` 필수 |

`__Host-refresh_token`처럼 이름을 지으면, 하위 도메인이나 HTTP 페이지가 같은 이름의 쿠키를 덮어쓰는 공격을 막을 수 있다.

## 10. 쿠키와 보안
| 공격 | 쿠키와의 관계 | 막는 방법 |
|---|---|---|
| **XSS** (악성 스크립트 실행) | 스크립트가 `document.cookie`로 쿠키를 읽어 공격자에게 보낸다 | `HttpOnly` |
| **CSRF** (요청 위조) | 다른 사이트가 사용자 브라우저로 요청을 보내게 하면 쿠키가 자동으로 붙는다 | `SameSite=Lax`/`Strict`, CSRF 토큰 |
| **도청** | HTTP로 전송되는 쿠키를 네트워크에서 엿본다 | `Secure` + HTTPS |
| **쿠키 조작** | 사용자가 쿠키 값을 마음대로 바꾼다 | 서버가 쿠키 값을 그대로 믿지 않는다. 서명하거나, 의미 없는 ID만 담고 실제 정보는 서버에 둔다 |

**쿠키 값은 사용자가 바꿀 수 있다.** 브라우저 개발자 도구에서 누구나 쿠키를 편집할 수 있다. 그래서 서버는 쿠키를 다음처럼 다뤄야 한다.
- 세션 ID: 추측할 수 없는 난수이고, 실제 정보는 서버에 있다. 바꾸면 존재하지 않는 세션이 된다.
- JWT: 서명이 있으므로 바꾸면 검증에서 걸린다.
- **서명 없는 직렬화 객체:** 바뀐 값을 서버가 그대로 복원한다. 이 프로젝트의 `oauth2_auth_request` 쿠키가 여기에 해당한다(12장).

## 11. Java와 Spring에서 쿠키 다루기

### `jakarta.servlet.http.Cookie`
```java
Cookie cookie = new Cookie("refresh_token", token);
cookie.setPath("/");
cookie.setMaxAge(1209600);
cookie.setHttpOnly(true);
cookie.setSecure(true);
cookie.setAttribute("SameSite", "Lax");   // Servlet 6.0부터
response.addCookie(cookie);
```
읽을 때:
```java
Cookie[] cookies = request.getCookies();              // 없으면 null
Cookie c = WebUtils.getCookie(request, "refresh_token");  // Spring 도우미
```

### Spring의 `ResponseCookie`
```java
ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
        .path("/")
        .maxAge(Duration.ofDays(14))
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .build();
response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
```
빌더로 속성을 한눈에 볼 수 있고, `SameSite`를 직접 지원한다.

## 12. 이 프로젝트의 쿠키
| 쿠키 | 만드는 곳 | 값 | 속성 | 쓰는 곳 |
|---|---|---|---|---|
| `oauth2_auth_request` | `OAuth2AuthorizationRequestBasedOnCookieRepository` | OAuth2 권한 요청 객체를 Java 직렬화 → Base64URL | `Path=/`, `Max-Age=18000` | ⑥에서 `state` 검증. 로그인 성공 후 삭제 |
| `refresh_token` | `OAuth2SuccessHandler` | 리프레시 토큰(JWT) | `Path=/`, `Max-Age=1209600`(14일) | `article.js`가 `document.cookie`로 읽어 `/api/token`에 보냄 |
| `JSESSIONID` | (Tomcat) | 세션 ID | `Path=/`, `HttpOnly` | `STATELESS` 설정 이후에는 Spring Security가 세션을 만들지 않아 거의 쓰이지 않음 |

**OAuth2 권한 요청을 쿠키에 두는 이유:** 권한 요청(①②)과 코드 수신(⑥)은 서로 다른 HTTP 요청이다. ⑥에서 `state`를 비교하려면 ②의 정보를 어딘가에 남겨 둬야 한다. Spring Security의 기본 저장 위치는 세션인데, 이 프로젝트는 `STATELESS`라서 세션을 쓰지 않는다. 그래서 브라우저 쿠키에 저장한다.

**현재 구성에서 개선할 점**
- `refresh_token`에 `HttpOnly`가 없다. `article.js`가 이 쿠키를 직접 읽는 구조이기 때문인데, 그 대신 XSS로 탈취될 수 있다. 재발급 요청을 쿠키 자동 전송으로 바꾸면 `HttpOnly`를 붙일 수 있다.
- 두 쿠키 모두 `Secure`, `SameSite`가 지정되지 않았다. 배포 환경(HTTPS)에서는 `Secure`를 붙인다.
- `oauth2_auth_request`는 서명 없는 Java 직렬화 데이터다. 사용자가 바꾼 값을 서버가 역직렬화하므로 위험하다(README 트러블슈팅 6번).

## 정리
| 항목 | 핵심 |
|---|---|
| 정의 | 서버가 `Set-Cookie`로 주고, 브라우저가 `Cookie`로 자동으로 돌려보내는 `이름=값` |
| 범위 | `Domain`(호스트), `Path`(경로), `Secure`(HTTPS), `SameSite`(사이트). 포트는 포함되지 않음 |
| 수명 | `Max-Age`/`Expires`. 없으면 브라우저 종료 시 삭제 |
| 삭제 | 같은 이름, 도메인, 경로로 `Max-Age=0` |
| 보안 | `HttpOnly`(XSS), `SameSite`(CSRF), `Secure`(도청). 값은 사용자가 바꿀 수 있다 |
| OAuth | `state` 쿠키는 다른 사이트에서 돌아오는 리다이렉트에 실려야 하므로 `Strict` 금지 |
