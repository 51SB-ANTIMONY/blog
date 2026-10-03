---
title: 인가 서버와 Spring Authorization Server
date: 2026-10-03
tags: [oauth, oidc, authorization-server, spring-security, filter-chain]
---

# 인가 서버와 Spring Authorization Server

> 이 노트를 읽기 전에 알면 좋은 내용
> - OAuth의 네 가지 역할: [OAuth 2.0이란 무엇인가](../14_oauth/01_oauth.md)
> - 권한 부여 코드 방식의 흐름: [권한 부여 코드 승인 방식](../14_oauth/02_authorization_code_grant.md)
> - Spring Security 필터와 필터 체인: [필터 기반 동작 원리](../12_spring_security/03_filter_architecture.md)
> - OIDC와 ID 토큰: [공유 쿠키 방식과 OpenID Connect](./03_shared_cookie_vs_oidc.md)
>
> 코드와 클래스 이름은 Spring Boot 4.0, Spring Security 7.0 기준이다.

## 1. 다시 보는 OAuth의 역할
OAuth에서 로그인과 토큰 발급에 참여하는 쪽은 역할이 정해져 있다.

| 역할 | 하는 일 | 예 |
|---|---|---|
| **클라이언트** (Client) | 사용자를 인가 서버로 보내 로그인시키고, 결과로 토큰을 받아 쓰는 **앱** | Google 로그인 버튼이 있는 블로그 |
| **인가 서버** (Authorization Server) | 사용자를 로그인시키고, 앱에게 토큰을 **발급**하는 서버 | `accounts.google.com` |
| **리소스 서버** (Resource Server) | 토큰을 받아 **검증**하고 데이터를 주는 API 서버 | Google 사용자 정보 API |

OpenID Connect에서는 인가 서버를 **OP**(OpenID Provider), 클라이언트를 **RP**(Relying Party)라고도 부른다.

### accounts.antimony.kr은 두 역할을 함께 가진다
```
Google (인가 서버)
   ▲  "Google로 로그인"
   │
accounts.antimony.kr
   ├ Google에게는 → 클라이언트
   └ blog, app2에게는 → 인가 서버
          ▲
          │  "accounts로 로그인"
   blog.antimony.kr, app2.antimony.kr (클라이언트)
```
- accounts가 Google에 로그인을 요청할 때 accounts는 **클라이언트**다.
- blog가 accounts에 로그인을 요청할 때 accounts는 **인가 서버**다.

## 2. 인가 서버가 하는 일
인가 서버는 **"어떤 앱이, 어떤 사용자의 정보에, 어디까지 접근해도 되는지"를 확인하고, 그 사실을 증명하는 토큰을 발급하고 관리하는 서버**다. 하는 일을 순서대로 나누면 다음과 같다.

### ① 앱 관리
로그인을 요청할 수 있는 앱을 **미리 등록**해 둔다. 등록하지 않은 앱의 요청은 거절한다.

| 등록 정보 | 의미 | 예 |
|---|---|---|
| `client_id` | 앱을 구별하는 이름 | `blog` |
| Client Secret | 앱이 자신을 증명하는 비밀 값 | (임의의 긴 문자열) |
| `redirect_uri` | 코드를 돌려보내도 되는 주소 | `https://blog.antimony.kr/login/oauth2/code/accounts` |
| 허용 범위 (scope) | 이 앱이 요청할 수 있는 정보 | `openid`, `profile`, `email` |
| 토큰 수명 | 이 앱에 발급할 토큰의 유효 시간 | 액세스 토큰 5분, 리프레시 토큰 14일 |

### ② 권한 요청 검사 (`/oauth2/authorize`)
앱이 사용자를 인가 서버로 보내면서 붙인 값들이 올바른지 확인한다.
- `client_id`가 등록된 앱인가
- `redirect_uri`가 그 앱의 등록된 주소와 **한 글자도 다르지 않게** 같은가
- 요청한 범위가 그 앱에 허용된 것인가
- (PKCE를 쓰면) `code_challenge`가 있는가

`redirect_uri` 검사가 특히 중요하다. 이 검사가 없으면 공격자가 `redirect_uri`를 자기 서버 주소로 바꿔서, 사용자의 코드를 가로챌 수 있다.

### ③ 사용자가 로그인했는지 확인
- 로그인하지 않았으면 **로그인 화면으로 보낸다.**
- 이미 로그인했으면(인가 서버에 로그인 세션이 있으면) 로그인 화면 없이 바로 다음 단계로 간다. 이것이 **SSO**다.

> **세션이란?**
> 서버가 "이 브라우저는 누구로 로그인했는지"를 기억해 두는 저장 공간이다. 브라우저는 세션을 가리키는 값(세션 ID)을 쿠키로 들고 다닌다([인증과 인가](../12_spring_security/01_authentication_and_authorization.md) 6장).

**주의:** 인가 서버 기능에는 **로그인 화면과 비밀번호 확인 자체는 들어 있지 않다.** 인가 서버는 "로그인됐는가?"만 확인하고, 실제 로그인은 일반 Spring Security 기능(`formLogin()`, `oauth2Login()`)이 한다. 그래서 로그인 방법(아이디·비밀번호, Google)은 우리가 정하고 만든다.

### ④ 동의 받기 (필요한 경우)
"이 앱에 이 정보를 줘도 되는가"를 사용자에게 묻는다. Google 로그인 때 보는 "Blog에서 다음 정보에 액세스하려고 합니다" 화면이다. 자사 앱(blog)에는 이 화면을 생략하도록 설정할 수 있다.

### ⑤ 권한 부여 코드 발급
짧은 시간만 쓸 수 있는 **일회용 코드**를 만들어 앱의 `redirect_uri`로 돌려보낸다.
```http
HTTP/1.1 302
Location: https://blog.antimony.kr/login/oauth2/code/accounts?code=Xk3f...&state=...
```
코드는 브라우저 주소창을 거치므로 노출될 수 있다. 그래서 코드만으로는 아무것도 할 수 없게 만들고(⑥), 수명을 짧게 한다.

### ⑥ 앱 인증과 토큰 발급 (`/oauth2/token`)
앱의 **서버**가 브라우저를 거치지 않고 코드를 들고 직접 찾아온다.
```http
POST /oauth2/token HTTP/1.1
Host: accounts.antimony.kr
Authorization: Basic YmxvZzpzZWNyZXQ=          ← client_id:Client Secret
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=Xk3f...&redirect_uri=https://blog.antimony.kr/...
```
인가 서버는 다음 순서로 확인한다.
1. **앱을 인증한다.** `Authorization` 헤더의 `client_id`와 Client Secret이 등록된 값과 같은가
2. 코드가 존재하고, 만료되지 않았고, 처음 쓰이는 것인가
3. 코드가 **이 앱에게** 발급된 것인가
4. `redirect_uri`가 ②와 같은가

모두 맞으면 토큰을 발급한다.
```json
{
  "access_token": "eyJ...",
  "refresh_token": "...",
  "id_token": "eyJ...",
  "token_type": "Bearer",
  "expires_in": 300
}
```
| 토큰 | 용도 |
|---|---|
| ID 토큰 | 앱에게 "이 사용자는 누구다"를 알림 (OIDC) |
| 액세스 토큰 | 앱이 API를 호출할 때 사용 |
| 리프레시 토큰 | 액세스 토큰이 만료되면 새것을 받을 때 사용 |

여기서 처음으로 **사용자가 아니라 앱을 인증**한다는 점에 주목한다. 지금까지 본 로그인은 모두 사용자 인증이었다.

### ⑦ 토큰 서명과 공개 키 공개 (`/oauth2/jwks`)
토큰을 **개인 키로 서명**하고, 앱들이 검증할 수 있도록 **공개 키를 공개**한다([JWT의 서명과 검증](../13_jwt/05_jwt_signature_and_validation.md) 3장).

### ⑧ 토큰 관리
| 일 | 주소 |
|---|---|
| 리프레시 토큰으로 새 액세스 토큰 발급 | `/oauth2/token` (`grant_type=refresh_token`) |
| 토큰 취소 | `/oauth2/revoke` |
| 토큰이 아직 유효한지 조회 | `/oauth2/introspect` |

### ⑨ 정보 제공
| 일 | 주소 |
|---|---|
| 액세스 토큰을 받고 사용자 정보 응답 | `/userinfo` |
| 인가 서버의 주소와 기능 목록 공개 | `/.well-known/openid-configuration` |

### ⑩ 로그아웃 (`/connect/logout`)
인가 서버의 로그인 세션을 끝낸다. 그러면 다른 앱들도 다음에 로그인을 요청할 때 다시 로그인해야 한다.

### 정리: 인가 서버가 하는 일
```
앱 등록 → 요청 검사 → 사용자 로그인 확인 → (동의) → 코드 발급
       → 앱 인증 → 토큰 발급 → (서명, 공개 키) → 재발급·취소·조회 → 로그아웃
```

## 3. Spring Security의 OAuth 관련 모듈
Spring Security는 OAuth의 역할마다 모듈을 따로 제공한다.

| 모듈 | 역할 | 설정 메서드 | 이 프로젝트에서 |
|---|---|---|---|
| `spring-security-oauth2-client` | **클라이언트** | `oauth2Login()` | Google 로그인에 사용 중 |
| `spring-security-oauth2-resource-server` | **리소스 서버** | `oauth2ResourceServer()` | 아직 사용 안 함 |
| `spring-security-oauth2-authorization-server` | **인가 서버** | `oauth2AuthorizationServer()` | 1-3 단계에서 사용 |
| `spring-security-oauth2-jose` | JWT, 키(JWK) 처리 | | 위 모듈들이 내부에서 사용 |

> **Spring Authorization Server란?**
> Spring 팀이 만든 OAuth 2.1 / OpenID Connect **인가 서버 구현**이다. 원래 별도 프로젝트였는데 Spring Security 7부터 Spring Security 안의 모듈(`spring-security-oauth2-authorization-server`)이 되었다. Spring Boot 4에서는 `spring-boot-starter-oauth2-authorization-server`로 추가한다. 2장에서 본 일을 대부분 해 준다.

## 4. Spring Authorization Server는 필터 체인 위에서 동작한다
Spring Authorization Server는 따로 실행되는 프로그램이 아니다. **Spring Security 필터 체인에 "인가 서버용 필터들"을 추가하는 기능**이다.

지금까지 본 Spring Security 기능들도 모두 같은 방식이었다. 설정 메서드를 호출하면 필터가 체인에 들어간다.

| 설정 메서드 | 추가되는 필터 | 그 필터가 직접 처리하는 요청 |
|---|---|---|
| `formLogin()` | `UsernamePasswordAuthenticationFilter` | `POST /login` |
| `logout()` | `LogoutFilter` | `/logout` |
| `oauth2Login()` | `OAuth2AuthorizationRequestRedirectFilter`, `OAuth2LoginAuthenticationFilter` | `/oauth2/authorization/google`, `/login/oauth2/code/google` |
| **`oauth2AuthorizationServer()`** | **아래 표의 필터들** | **`/oauth2/authorize`, `/oauth2/token` 등** |

`POST /login`을 처리하는 컨트롤러가 없어도 `UsernamePasswordAuthenticationFilter`가 로그인을 처리했던 것처럼, **`/oauth2/token`을 처리하는 컨트롤러도 만들지 않는다.** 필터가 요청을 받아 처리하고 응답까지 끝낸다.

### 인가 서버가 추가하는 필터
| 필터 | 처리하는 요청 | 2장의 어느 일인가 |
|---|---|---|
| `AuthorizationServerContextFilter` | 모든 요청 | 인가 서버 설정(issuer 등)을 요청에 연결 |
| `OAuth2AuthorizationEndpointFilter` | `/oauth2/authorize` | ② 요청 검사, ③ 로그인 확인, ④ 동의, ⑤ 코드 발급 |
| `OAuth2ClientAuthenticationFilter` | `/oauth2/token`, `/oauth2/revoke`, `/oauth2/introspect` | ⑥ **앱** 인증 |
| `OAuth2TokenEndpointFilter` | `/oauth2/token` | ⑥ 토큰 발급, ⑧ 재발급 |
| `NimbusJwkSetEndpointFilter` | `/oauth2/jwks` | ⑦ 공개 키 |
| `OAuth2TokenRevocationEndpointFilter` | `/oauth2/revoke` | ⑧ 토큰 취소 |
| `OAuth2TokenIntrospectionEndpointFilter` | `/oauth2/introspect` | ⑧ 토큰 조회 |
| `OidcUserInfoEndpointFilter` | `/userinfo` | ⑨ 사용자 정보 |
| `OidcProviderConfigurationEndpointFilter` | `/.well-known/openid-configuration` | ⑨ 설정 문서 |
| `OidcLogoutEndpointFilter` | `/connect/logout` | ⑩ 로그아웃 |

## 5. 왜 필터 체인을 두 개 쓰는가

### 필터 체인을 여러 개 둘 수 있다는 것
[필터 기반 동작 원리](../12_spring_security/03_filter_architecture.md) 2장에서 본 것처럼, Spring Security의 입구인 `FilterChainProxy`는 `SecurityFilterChain`을 **여러 개** 가질 수 있다. 요청이 오면 등록된 순서대로 각 체인의 **요청 매처**(이 체인이 담당할 주소 조건)를 보고, **처음 맞는 체인 하나만** 실행한다.

### 인가 서버 주소와 로그인 주소는 규칙이 다르다
요청마다 **누가 보내는지, 무엇을 인증하는지, 실패하면 어떻게 응답해야 하는지**가 다르다.

| 요청 | 보내는 쪽 | 인증하는 대상 | 인증 방법 | 실패하면 |
|---|---|---|---|---|
| `GET /oauth2/authorize` | **브라우저** | 사용자 | 세션 | `/login`으로 302 (로그인 화면으로 이동) |
| `POST /oauth2/token` | **blog 서버** (브라우저 없음) | **앱** | `Authorization: Basic` (Client ID + Secret) | `401` + JSON 에러 |
| `GET /userinfo` | blog 서버 | 액세스 토큰 | `Authorization: Bearer` | `401` + `WWW-Authenticate: Bearer` |
| `GET /oauth2/jwks`, `/.well-known/...` | 누구나 | 없음 | 없음 | (공개) |
| `GET /login`, `POST /login` | 브라우저 | 사용자 | 폼 로그인, Google 로그인 | 로그인 화면에 에러 표시 |

### 한 체인에 모두 넣으면 생기는 문제
한 체인에는 실패 처리, CSRF, 접근 규칙을 **하나씩만** 정할 수 있다. 그런데 위 요청들은 그 규칙이 서로 반대다.

**문제 1. 인증 실패 처리**
| 하나로 정했을 때 | 결과 |
|---|---|
| "로그인 안 됐으면 `/login`으로 302" | blog 서버가 부르는 `/oauth2/token`이 실패해도 302와 로그인 화면 HTML이 돌아간다. blog 서버는 이 응답을 해석할 수 없다 |
| "로그인 안 됐으면 401" | 브라우저 사용자가 `/oauth2/authorize`에서 로그인 화면으로 가지 못하고 401 에러만 본다 |

**문제 2. CSRF**
> **CSRF 보호란?**
> 다른 사이트가 사용자 브라우저를 이용해 몰래 요청을 보내는 공격을 막기 위해, 서버가 준 임의의 값(CSRF 토큰)이 함께 와야만 `POST` 같은 요청을 받아 주는 기능이다([인증과 인가](../12_spring_security/01_authentication_and_authorization.md) 9장).

| 주소 | CSRF 보호 |
|---|---|
| `POST /login` (브라우저 폼) | **켜야 한다.** 세션 쿠키로 인증하므로 위조 요청을 막아야 한다 |
| `POST /oauth2/token` (서버 간 요청) | **꺼야 한다.** blog 서버는 CSRF 토큰을 받을 방법이 없고, 쿠키가 아니라 Client Secret으로 인증하므로 CSRF 공격 대상도 아니다 |

**문제 3. 접근 규칙**
| 하나로 정했을 때 | 결과 |
|---|---|
| `anyRequest().authenticated()` (모든 요청에 사용자 로그인 요구) | `/oauth2/token`에도 **사용자** 로그인을 요구하게 된다. 이 요청은 blog 서버가 보내므로 사용자 세션이 없어 항상 실패한다 |
| `anyRequest().permitAll()` (모두 허용) | 지켜야 할 주소까지 열린다 |

### 체인을 나누면
```
FilterChainProxy
 │  요청 주소를 보고 처음 맞는 체인 하나만 실행
 │
 ├ 체인 ① 인가 서버                   담당 주소: /oauth2/**, /.well-known/**, /userinfo, /connect/**
 │    · oauth2AuthorizationServer()  인가 서버 필터들
 │    · 앱 인증(Client Secret), 액세스 토큰 인증(/userinfo)
 │    · 브라우저 요청이 로그인 안 됐으면 /login으로 302, 서버 요청은 401
 │    · 토큰 주소는 CSRF 보호 제외
 │
 └ 체인 ② 로그인                      담당 주소: 그 외 전부
      · formLogin()   아이디·비밀번호 로그인
      · oauth2Login() Google 로그인
      · 세션 사용, CSRF 보호
```
각 체인이 **자기 주소에 맞는 규칙만** 가지므로 규칙끼리 부딪히지 않는다. Spring Authorization Server는 체인 ①이 담당할 주소 목록(`getEndpointsMatcher()`)과 위 규칙들의 기본값을 제공한다. 공식 문서의 기본 예제도 이 두 체인 구조다.

### 두 체인은 같은 세션을 쓴다
체인이 나뉘어 있어도 **세션은 하나**다. 그래서 체인 ②에서 로그인한 결과를 체인 ①이 알아본다. 이 때문에 인가 서버에서는 세션을 끄면(`STATELESS`) 안 된다.

### 설정의 모양
```java
@Bean
@Order(1)   // 먼저 검사
public SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
    http.oauth2AuthorizationServer(authorizationServer -> {
        http.securityMatcher(authorizationServer.getEndpointsMatcher());   // 인가 서버 주소만 담당
        authorizationServer.oidc(Customizer.withDefaults());             // OIDC 기능 사용
    });
    // 브라우저 요청이 로그인 안 됐으면 /login으로 보내는 설정 등
    return http.build();
}

@Bean
@Order(2)   // 나머지 전부
public SecurityFilterChain loginChain(HttpSecurity http) throws Exception {
    http.formLogin(...)
        .oauth2Login(...)
        .authorizeHttpRequests(...);
    return http.build();
}
```
> **`@Order`란?**
> 같은 종류의 빈이 여러 개일 때 순서를 정하는 어노테이션이다. 숫자가 작을수록 먼저 온다. 여기서는 `FilterChainProxy`가 체인을 검사하는 순서가 된다. 체인 ②는 모든 주소를 담당하므로, 담당 주소가 좁은 체인 ①을 먼저 검사해야 한다.

위 코드는 구조를 보여 주기 위한 모양이다. 실제 코드는 구현하면서 확정한다.

## 6. 한 번의 로그인이 두 체인을 오가는 모습
blog에서 처음 로그인할 때 accounts에서 일어나는 일이다.
```
① GET /oauth2/authorize?client_id=blog&redirect_uri=...&scope=openid...
   → 체인 ①  OAuth2AuthorizationEndpointFilter
             요청 검사 통과, 그런데 로그인 안 됨
             → 원래 요청을 세션에 저장 → 302 /login

② GET /login
   → 체인 ②  로그인 화면

③ POST /login (아이디·비밀번호)   또는   Google 로그인
   → 체인 ②  UsernamePasswordAuthenticationFilter / OAuth2LoginAuthenticationFilter
             로그인 성공 → 세션에 로그인 정보 저장
             → 저장해 둔 ①의 요청으로 302

④ GET /oauth2/authorize?client_id=blog&...   (다시)
   → 체인 ①  세션에 로그인 정보 있음
             → 권한 부여 코드 발급 → 302 blog.antimony.kr/...?code=...

⑤ POST /oauth2/token   (blog 서버가 직접, 브라우저 없음)
   → 체인 ①  OAuth2ClientAuthenticationFilter: blog 앱 인증
             OAuth2TokenEndpointFilter: 코드 확인 → 토큰 응답
```
그 다음 app2가 ①을 보내면, 세션이 이미 있으므로 ②③을 건너뛰고 바로 ④로 간다. 이것이 SSO다.

## 7. 구성 요소
Spring Authorization Server를 쓸 때 등장하는 이름들이다.

| 구성 요소 | 하는 일 | 2장의 어느 일인가 |
|---|---|---|
| `RegisteredClientRepository` | 등록된 앱 정보를 저장하고 찾는다. 메모리 또는 DB | ① 앱 관리 |
| `RegisteredClient` | 앱 하나의 등록 정보 (`client_id`, Secret, `redirect_uri`, scope, 설정) | ① |
| `ClientSettings` | 앱별 설정. 동의 화면 사용 여부, PKCE 필수 여부 | ①④ |
| `TokenSettings` | 앱별 토큰 설정. 액세스 토큰과 리프레시 토큰의 수명 | ① |
| `OAuth2AuthorizationService` | 발급한 코드와 토큰을 저장한다. 코드 일회용 확인, 리프레시 토큰 재발급, 취소에 쓰인다 | ⑤⑥⑧ |
| `OAuth2AuthorizationConsentService` | 사용자가 어떤 앱에 어떤 범위를 동의했는지 저장한다 | ④ |
| `JWKSource` | 토큰 서명에 쓸 키(RSA 키 쌍)를 제공한다. 공개 키는 `/oauth2/jwks`로 공개된다 | ⑦ |
| `AuthorizationServerSettings` | 인가 서버 전체 설정. issuer 주소, 각 엔드포인트 주소 | ⑨ |
| `OAuth2TokenCustomizer` | 발급하는 토큰에 클레임을 추가한다. 예: `nickname` | ⑥ |

## 8. 제공되는 것과 직접 만드는 것
| Spring Authorization Server가 제공 | 우리가 직접 만듦 |
|---|---|
| `/oauth2/authorize` 요청 검사, 코드 발급 | 로그인 방법: 아이디·비밀번호(`formLogin()`), Google(`oauth2Login()`) |
| `/oauth2/token` 앱 인증, 토큰 발급, 재발급 | 회원 저장: `User`, `UserRepository`, `UserDetailService`, 회원가입 |
| 토큰 서명, `/oauth2/jwks` | 등록할 앱 정보: `blog`의 `client_id`, Secret, `redirect_uri`, 토큰 수명 |
| 설정 문서, `/userinfo`, 취소, 조회, 로그아웃 | 토큰에 넣을 사용자 정보: `sub` = 회원 id, `nickname`, `email` |
| PKCE, `state`, `nonce` 처리 | 화면: 로그인, 회원가입 |

## 9. 지금 코드와의 비교
1-2 단계에서 blog에서 복사해 온 accounts의 코드는 인가 서버의 일 일부를 **직접 만든 코드**로 하고 있다.

| 일 | 지금 (직접 만든 코드) | Spring Authorization Server |
|---|---|---|
| 토큰을 받을 앱 | blog 하나 (`/articles?token=`으로 고정) | 등록된 여러 앱 |
| 토큰 전달 | 로그인 직후 **URL에 토큰**을 붙여 보냄 | URL에는 코드만, 토큰은 서버 간 요청으로 |
| 토큰 종류 | 자체 형식 JWT 2개 | 표준 ID 토큰, 액세스 토큰, 리프레시 토큰 |
| 서명 | HS256 (비밀 키를 blog와 공유) | RS256 (개인 키는 accounts에만) |
| 받을 앱 표시 (`aud`) | 없음 | 있음 |
| 리프레시 토큰 | `RefreshToken` 테이블 + `/api/token` | `/oauth2/token` (`grant_type=refresh_token`) |
| 취소, 조회, 사용자 정보, 설정 문서, 로그아웃 | 없음 | 있음 |
| 세션 | `STATELESS` (없음) | 사용 (SSO의 근거) |
| 필터 체인 | 1개 | 2개 |

### 1-3 단계에서 바뀌는 코드
| 지금 코드 | 1-3 이후 |
|---|---|
| `TokenProvider`, `JwtProperties`, jjwt 라이브러리 | 삭제 → `JWKSource` (RSA 키) |
| `OAuth2SuccessHandler` | 삭제 → 로그인 후 원래 `/oauth2/authorize` 요청으로 돌아가는 기본 동작 |
| `RefreshToken*`, `TokenService`, `TokenApiController` | 삭제 → `/oauth2/token` |
| `TokenAuthenticationFilter` | 삭제 (토큰 검증은 각 앱의 일) |
| `OAuth2AuthorizationRequestBasedOnCookieRepository`, `CookieUtil` | 삭제 → 세션에 저장 |
| `WebOAuthSecurityConfig` (체인 1개, `STATELESS`) | 체인 2개 (인가 서버 / 로그인), 세션 사용, CSRF 보호 |
| (없음) | `RegisteredClientRepository`에 `blog` 등록, `AuthorizationServerSettings` |

## 정리
| 질문 | 답 |
|---|---|
| 인가 서버가 하는 일 | 앱을 등록받고, 사용자가 로그인했는지 확인해서, 그 앱 전용 토큰을 발급하고 관리한다. 로그인 화면 자체는 일반 Spring Security 기능에 맡긴다 |
| Spring Authorization Server | 인가 서버 기능을 Spring Security 필터 체인에 추가하는 모듈. `/oauth2/token` 같은 주소를 컨트롤러 없이 필터가 처리한다 |
| 체인을 두 개 쓰는 이유 | 브라우저 사용자를 세션으로 인증하는 주소와, 다른 서버(앱)를 Secret이나 토큰으로 인증하는 주소는 실패 처리, CSRF, 접근 규칙이 반대라서. 세션은 두 체인이 함께 쓴다 |
