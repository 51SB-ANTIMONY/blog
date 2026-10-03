---
title: blog를 accounts의 OIDC 클라이언트로 바꾸기 (2단계)
date: 2026-10-03
tags: [oidc, oauth2-client, spring-security, session, csrf, sso]
---

# blog를 accounts의 OIDC 클라이언트로 바꾸기 (2단계)

> 1단계에서 만든 accounts(인가 서버)는 [accounts 인가 서버 구현 과정](./05_accounts_implementation.md)에 정리했다.
> 이 노트는 blog가 로그인을 직접 처리하지 않고 accounts에 맡기도록 바꾼 과정, 확인 결과, 겪은 문제를 정리한다.
> Spring Boot 4.0.1, Spring Security 7.0.2 기준이다.

## 1. 무엇을 바꿨나

| | 바꾸기 전 | 바꾼 뒤 |
|---|---|---|
| 로그인 화면 | blog의 Google 로그인 버튼 | accounts 로그인 화면 (아이디·비밀번호, Google) |
| 로그인 처리 | blog가 Google과 직접 통신 | blog는 accounts와만 통신 |
| 로그인 상태 | blog가 만든 JWT를 브라우저 `localStorage`에 저장, 매 요청 `Authorization` 헤더 | blog 서버의 **세션**. 브라우저에는 `BLOG_SESSION` 쿠키만 |
| 회원 구별 | 이메일 | accounts가 준 `sub`(accounts 회원 id) |
| CSRF 보호 | 꺼짐 | 켜짐 |
| 로그아웃 | 세션만 지움 (토큰은 브라우저에 남음) | blog 세션 + accounts 로그인 함께 종료 |

> **OIDC 클라이언트란?**
> 로그인을 인가 서버(여기서는 accounts)에 맡기고, 결과를 ID 토큰으로 받아 "누가 로그인했는지" 확인하는 앱이다. OIDC 문서에서는 RP(Relying Party)라고도 부른다.

> **BFF(Backend For Frontend)란?**
> 인가 서버에서 받은 토큰을 브라우저가 아니라 서버(blog backend)가 가지고 있는 방식이다. 브라우저에는 서버와의 세션 쿠키만 있다. JavaScript가 토큰을 읽을 수 없으므로, 화면에 악성 스크립트가 들어와도 토큰을 가져가지 못한다.

## 2. blog의 JWT를 지운 이유
2단계에서 blog가 직접 JWT를 만들고 검사하던 코드(`TokenProvider`, `TokenAuthenticationFilter`, 리프레시 토큰, 재발급 API, `jjwt`)를 지웠다. 하지만 **시스템에서 JWT가 사라진 것은 아니다.** JWT를 만드는 곳이 blog에서 accounts로 옮겨 갔다.

### 지운 것과 남은 것
| 구분 | 내용 | 상태 |
|---|---|---|
| blog가 발급하던 JWT | `TokenProvider`(HS256 서명), 액세스 토큰(1일), 리프레시 토큰(14일)과 `refresh_token` 테이블, 재발급 API(`/api/token`), `TokenAuthenticationFilter` | 삭제 |
| accounts가 발급하는 JWT | ID 토큰, 액세스 토큰. RS256으로 서명하고, 검증용 공개 키는 `/oauth2/jwks`에 공개 | 사용 중 |
| blog의 JWT 검증 | accounts가 준 ID 토큰의 서명, `iss`, `aud`, `exp`, `nonce` 검사 | 사용 중 (Spring Security가 처리하므로 직접 짠 코드는 없음) |

blog는 JWT를 **만드는 쪽**에서 **검사하는 쪽**으로 바뀌었다.

### 이유 1: 로그인을 accounts가 맡게 되었다
예전에는 blog가 로그인을 직접 처리했으므로, "이 사람이 로그인했다"는 증명(JWT)도 blog가 만들어야 했다. 이제는 accounts가 로그인을 처리하고, 그 결과로 **ID 토큰**을 blog에 준다. 로그인했다는 증명을 accounts가 만들어 주므로 blog가 따로 만들 필요가 없다.

> **ID 토큰이란?**
> 로그인이 끝났을 때 인가 서버(accounts)가 앱(blog)에 주는 JWT다. 누가(`sub`), 언제, 어느 앱을 위해(`aud`) 로그인했는지가 들어 있다.

### 이유 2: 로그인한 뒤에는 세션을 쓴다
blog는 ID 토큰을 **로그인할 때 한 번만** 검사한다. 그 뒤로는 서버 세션에 로그인 정보를 저장하고, 브라우저는 세션 쿠키(`BLOG_SESSION`)만 보낸다. 매 요청마다 토큰을 보내고 검사하는 과정이 없으므로, blog가 자기 토큰을 발급하고 재발급할 이유도 없다.

이 방식은 토큰을 브라우저에 두지 않는다는 결정(BFF)과도 맞다. 예전 방식은 액세스 토큰을 `localStorage`에, 리프레시 토큰을 JavaScript가 읽을 수 있는 쿠키에 두어서, 화면에 악성 스크립트가 들어오면 토큰을 가져갈 수 있었다. blog README 트러블슈팅의 5번(로그아웃해도 토큰이 남음), 7번(토큰이 URL과 쿠키로 노출), 10번(토큰 유효 시간이 경로마다 다름)이 모두 blog의 자체 JWT에서 생긴 문제였고, 이번 변경으로 해결됐다.

### 토큰 형식과는 상관없다
"accounts가 JWT로 발급하니까 blog의 JWT가 필요 없다"는 절반만 맞다. 지운 이유는 토큰 **형식**이 아니라, 토큰을 **발급하는 역할**이 accounts로 옮겨 갔기 때문이다.

| accounts가 주는 것 | 형식 | blog의 사용 |
|---|---|---|
| ID 토큰 | 항상 JWT (OIDC 규칙) | 로그인할 때 한 번 검사 → 회원 확인 |
| 액세스 토큰 | JWT (Spring Authorization Server 기본값. 설정에 따라 JWT가 아닌 형식도 가능) | 쓰지 않음 |

accounts가 액세스 토큰을 JWT가 아닌 형식으로 바꾸더라도 blog는 그대로 동작한다. blog가 쓰는 것은 ID 토큰뿐이고, ID 토큰은 언제나 JWT이기 때문이다.

> **액세스 토큰과 ID 토큰의 차이**
> ID 토큰은 "누가 로그인했는지"를 앱에 알려 주는 토큰이다. 액세스 토큰은 "이 토큰을 가진 쪽이 API를 써도 된다"는 허가증으로, API 서버에 보내는 용도다. blog는 다른 서버의 API를 부르지 않으므로 액세스 토큰을 받기만 하고 쓰지 않는다.

### JWT로 API를 부르는 방식이 다시 필요해지면
모바일 앱이나 다른 서버가 blog API를 직접 부르게 되면, 요청에 토큰을 붙이는 방식이 다시 필요하다. 이때는 예전처럼 blog가 JWT를 만들지 않고, **accounts가 발급한 액세스 토큰을 blog가 검사**하게 한다(Spring Security의 Resource Server). 토큰 발급은 계속 accounts 한곳에서 관리된다.

> **Resource Server란?**
> 요청에 붙어 온 액세스 토큰(`Authorization: Bearer ...`)을 검사해서 API를 허용하는 서버다. 토큰을 만들지는 않고, 인가 서버의 공개 키로 서명만 확인한다.

예전 코드는 Git 기록(`77e2349` TokenProvider·재발급 API, `51e8aa2` Google 로그인 + JWT)과 [JWT 노트](../13_jwt)에 남아 있다.

## 3. 순서와 커밋

| 단계 | 커밋 | 내용 |
|---|---|---|
| 2-1 | `06cbebc` | accounts를 로그인 제공자로 등록, 세션 로그인. Google 로그인과 JWT 코드 삭제 |
| 2-2 | `359ca6e` | blog 회원을 `sub`로 연결, 글 작성자를 회원 id로 |
| 2-3 | `b7fd8ea` | CSRF 보호 켜기 |
| 2-4 | `57ebdd1` | 로그아웃할 때 accounts 로그인도 끝내기 |
| 2-5 | `8664d11` | 세 서버를 함께 띄워 확인하다 찾은 문제 수정 (서버끼리 부르는 주소 분리) |
| 2-6 | `d444ec8` | README |

처음 계획은 "accounts 등록 → 회원 연결 → … → 마지막에 옛 코드 삭제"였다. 실제로는 2-1에서 **등록과 삭제를 한 커밋에** 했다. 옛 `OAuth2SuccessHandler`가 `@Component`라서, 보안 설정에서 빼도 Spring이 계속 만들려고 했고, 그러려면 함께 지울 쿠키 저장소 빈이 필요했다. 옛 코드를 남겨 둔 채로는 서버가 켜지지 않으므로, "교체"를 한 단위로 묶었다.

## 4. 2-1: accounts를 로그인 제공자로 등록

### 지운 것
| 지운 코드 | 하던 일 | 이제 누가 하나 |
|---|---|---|
| `TokenProvider`, `JwtProperties`, `TokenAuthenticationFilter` | JWT 발급, 검증 | 세션이 로그인 상태를 기억 |
| `RefreshToken`, `TokenService`, `TokenApiController` | 리프레시 토큰 저장, 재발급 | 필요 없음 (blog가 토큰을 쓰지 않음) |
| `OAuth2UserCustomService`, `OAuth2SuccessHandler` | Google 사용자 저장, JWT 발급 | `AccountsOidcUserService` (2-2) |
| `OAuth2AuthorizationRequestBasedOnCookieRepository`, `CookieUtil` | `STATELESS`라서 권한 요청 정보를 쿠키에 저장 | 세션에 저장 (Spring Security 기본값) |
| `UserViewController`, `UserApiController`, `login.html`, `signup.html`, `oauthLogin.html`, `token.js` | 로그인·회원가입 화면, 토큰 저장 | accounts 화면 |
| `jjwt`, `jaxb-api` 의존성, `application.yml`의 `jwt`, Google 설정 | | |

### 등록: `AccountsClientConfig`
보통은 `application.yml`에 이렇게 쓴다.
```yaml
spring.security.oauth2.client.provider.accounts.issuer-uri: http://localhost:3001
```
`issuer-uri`를 쓰면 Spring Boot가 **서버를 켤 때** `http://localhost:3001/.well-known/openid-configuration`을 받아 와서 나머지 주소를 채운다. 그래서 accounts가 꺼져 있으면 blog도 켜지지 않는다. 테스트도 accounts 없이는 돌지 않는다.

그래서 `ClientRegistration`을 Java 코드로 직접 만들었다.
```java
ClientRegistration.withRegistrationId("accounts")
        .clientId("blog").clientSecret(secret)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope("openid", "profile", "email")
        .issuerUri(issuer)                                   // ID 토큰의 iss 검사
        .authorizationUri(issuer + "/oauth2/authorize")      // 브라우저가 가는 주소
        .tokenUri(backchannelUri + "/oauth2/token")          // blog 서버가 부르는 주소 (2-5)
        .jwkSetUri(backchannelUri + "/oauth2/jwks")
        .userNameAttributeName("sub")                        // Authentication.getName() = sub
        .providerConfigurationMetadata(Map.of("end_session_endpoint", issuer + "/connect/logout"))
        .build();
```
주소들은 Spring Authorization Server의 기본 주소다. `issuerUri`를 넣어 두면 Spring Security가 ID 토큰의 `iss`가 이 값과 같은지 검사한다.

> **`ClientRegistration`이란?**
> "로그인을 맡길 곳 하나"에 대한 정보 묶음이다. Client ID와 Secret, 요청할 범위, 인가 서버의 주소들이 들어 있다. `oauth2Login()`은 이 정보를 보고 로그인 요청을 만든다.

> **`{baseUrl}`이란?**
> 요청을 받은 서버 주소(`http://localhost:8080`)로 바뀌는 자리 표시다. `{registrationId}`는 `accounts`로 바뀐다. 그래서 redirect_uri는 `http://localhost:8080/login/oauth2/code/accounts`가 되고, 1-3에서 accounts에 등록한 값과 같다.

### 보안 설정: `SecurityConfig`
```java
http
    .authorizeHttpRequests(auth -> auth
            .requestMatchers("/new-article", "/api/**").authenticated()
            .anyRequest().permitAll())
    .oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/articles"))
    .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), /api/** 매처))
```
- `sessionCreationPolicy(STATELESS)`를 지웠다. 기본값은 필요할 때 세션을 만든다.
- 로그인 화면(`loginPage`)을 지정하지 않았다. 등록된 제공자가 하나뿐이면 Spring Security는 로그인 화면 대신 바로 `/oauth2/authorization/accounts`로 보낸다.
- 세션 쿠키 이름은 `application.yml`의 `server.servlet.session.cookie.name: BLOG_SESSION`으로 정했다. 쿠키는 포트를 구분하지 않으므로, 같은 `localhost`의 accounts 세션 쿠키(`ACCOUNTS_SESSION`)와 이름이 겹치면 서로 덮어쓴다.

### PKCE는 설정하지 않아도 붙는다
blog는 Client Secret을 가진 클라이언트(confidential client)다. Spring Security 6까지는 이런 클라이언트에 PKCE를 쓰려면 따로 설정해야 했지만, Spring Security 7은 기본으로 붙인다. 테스트에서 권한 요청 주소에 `code_challenge`와 `code_challenge_method=S256`이 있는 것을 확인했다.

> **PKCE란?**
> 로그인을 시작할 때 무작위 값(`code_verifier`)을 만들어 그 해시(`code_challenge`)만 인가 서버에 보내고, 나중에 code를 토큰으로 바꿀 때 원래 값을 보내는 방식이다. 중간에서 code를 가로챈 사람은 원래 값을 모르므로 토큰을 받을 수 없다. accounts는 1-3부터 PKCE가 없는 요청을 거절한다.

## 5. 2-2: blog 회원을 `sub`로 연결

### `users` 테이블
```
users
├── id            PK
├── accounts_sub  NOT NULL, UNIQUE   ← ID 토큰의 sub
└── nickname      NOT NULL           ← accounts의 닉네임 복사본
```
아이디, 비밀번호, 이메일은 accounts에만 둔다. 같은 정보를 두 곳에 두면 한쪽만 바뀌었을 때 어느 값이 맞는지 알 수 없기 때문이다. 닉네임은 글 목록에 보여 줘야 해서 복사해 두고, 로그인할 때마다 accounts의 값으로 바꾼다. 닉네임 중복 검사는 accounts가 하므로 blog에서는 `UNIQUE`를 없앴다.

> **왜 `sub`인가?**
> 1-6에서 accounts는 로그인 방식(아이디·비밀번호, Google)과 상관없이 `sub`를 회원 id로 통일했다. 같은 사람은 어떤 방법으로 로그인하든 같은 `sub`를 받는다. 이메일은 바뀔 수 있고 없을 수도 있어서 회원을 구별하는 값으로 쓰지 않는다.

### 로그인 직후 회원 저장: `AccountsOidcUserService`
OIDC 로그인에서는 `OidcUserService`가 ID 토큰으로 사용자 객체(`OidcUser`)를 만든다. 이것을 상속해서, 만든 뒤에 blog 회원을 찾거나 만든다.
```java
public OidcUser loadUser(OidcUserRequest userRequest) {
    OidcUser oidcUser = super.loadUser(userRequest);   // ID 토큰 검증은 이미 끝난 상태
    userService.syncFromAccounts(oidcUser.getSubject(), nicknameOf(oidcUser));
    return oidcUser;
}
```
`SecurityConfig`에서 `.userInfoEndpoint(u -> u.oidcUserService(accountsOidcUserService))`로 등록한다.

> **`OidcUserService`와 `DefaultOAuth2UserService`의 차이**
> 요청 범위에 `openid`가 있으면 Spring Security는 OIDC 로그인으로 처리하고 `OidcUserService`를 부른다. 없으면 OAuth 2.0 로그인으로 처리하고 `DefaultOAuth2UserService`를 부른다. 옛 blog의 Google 로그인은 범위가 `email`, `profile`뿐이라 `DefaultOAuth2UserService`를 상속했다. 지금은 `openid`가 있으므로 `userService(...)`가 아니라 `oidcUserService(...)`에 등록해야 호출된다.

`userInfoUri`는 등록하지 않았다. 등록하면 로그인할 때마다 `/userinfo`를 한 번 더 부르는데, 필요한 값(`sub`, `nickname`)은 ID 토큰에 이미 있기 때문이다.

### 글 작성자
`article.author`(이메일 문자열)를 `author_id`(회원 외래 키)로 바꿨다.
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id", nullable = false)
private User author;
```
글 목록과 상세 응답에 작성자 닉네임을 넣으므로, 조회 메서드에 `@EntityGraph(attributePaths = "author")`를 붙여 작성자까지 한 번에 읽는다.

작성자 확인은 로그인한 사용자의 이름(`sub`)과 글 작성자의 `accountsSub`를 비교한다.

## 6. 2-3: CSRF 보호 켜기

바꾸기 전에는 로그인 정보가 `Authorization` 헤더에 있었다. 브라우저는 이 헤더를 자동으로 붙이지 않으므로 CSRF 공격이 통하지 않아서 꺼 두었다. 이제는 세션 쿠키로 로그인하고, 쿠키는 브라우저가 자동으로 붙이므로 다시 켜야 한다.

> **CSRF란?**
> 다른 사이트가 사용자의 브라우저를 이용해 이 사이트로 요청을 보내게 하는 공격이다. 브라우저는 그 요청에도 세션 쿠키를 붙이므로, 서버는 쿠키만 보고는 이 사이트 화면에서 온 요청인지 알 수 없다. 그래서 다른 사이트는 읽을 수 없는 값(CSRF 토큰)을 함께 보내게 한다.

accounts와 같은 방식(`csrf.spa()`)을 썼다.
- 서버는 CSRF 토큰을 `XSRF-TOKEN` 쿠키로 준다. 쿠키가 아직 없으면 `GET /api/csrf`(`CsrfApiController`)로 받는다.
- `article.js`는 POST, PUT, DELETE 요청에 이 쿠키 값을 `X-XSRF-TOKEN` 헤더로 붙인다.
- 로그아웃 폼은 Thymeleaf의 `th:action`이 `_csrf` 숨은 입력칸을 자동으로 넣는다.

> **다른 사이트는 왜 이 값을 붙일 수 없나**
> 브라우저는 다른 사이트의 JavaScript가 이 사이트의 쿠키를 읽지 못하게 막는다. 쿠키를 읽을 수 있는 것은 같은 사이트의 화면뿐이므로, 헤더에 쿠키와 같은 값이 들어 있으면 이 사이트 화면에서 보낸 요청이다.

## 7. 2-4: 로그아웃

blog 세션만 지우면 accounts의 로그인은 남는다. 그 상태에서 다시 로그인을 누르면 accounts가 비밀번호를 묻지 않고 바로 code를 돌려주므로(SSO), 사용자 입장에서는 로그아웃이 안 된 것처럼 보인다.

Spring Security의 `OidcClientInitiatedLogoutSuccessHandler`를 썼다.
```
POST /logout
  → blog 세션 삭제
  → 302 accounts /connect/logout?id_token_hint=<ID 토큰>&post_logout_redirect_uri=http://localhost:8080/
  → accounts 세션, 로그인 상태 유지 쿠키 삭제 (1-6에서 만든 처리)
  → 302 http://localhost:8080/ → /articles
```
- `id_token_hint`: 어느 로그인을 끝낼지 accounts에 알려 주는 값. 로그인할 때 받은 ID 토큰을 그대로 보낸다.
- `post_logout_redirect_uri`: 로그아웃 후 돌아올 주소. accounts에 미리 등록한 값(`http://localhost:8080/`)과 같아야 한다. 다르면 accounts가 그 주소로 보내지 않는다.
- `end_session_endpoint`: 로그아웃 주소. 설정 문서를 받아 오지 않으므로 `ClientRegistration`에 직접 넣었다.

`/`에는 화면이 없어서 `/articles`로 보내는 주소를 추가했다. 글 목록에는 로그인 전에는 로그인 버튼, 후에는 닉네임과 로그아웃 버튼을 보여 준다.

## 8. 확인

### 자동 테스트 (20개 통과)
accounts를 켜지 않고 실행한다. 로그인한 상태는 `spring-security-test`의 `oidcLogin()`으로 만든다.

> **`oidcLogin()`이란?**
> MockMvc 요청을 "OIDC로 로그인한 사용자"의 요청으로 만드는 도구다. `.idToken(t -> t.subject("1001"))`처럼 ID 토큰의 값을 정할 수 있고, `.clientRegistration(...)`으로 어느 제공자로 로그인했는지도 정할 수 있다.

| 테스트 | 확인하는 것 |
|---|---|
| `AccountsLoginTest` | 로그인이 필요한 화면 → `/oauth2/authorization/accounts`, 권한 요청 주소(3001, `client_id=blog`, 범위 3개, PKCE, `state`, `nonce`), 브라우저용·서버용 주소, 로그인 안 한 API 401, 로그아웃 → `3001/connect/logout`(`id_token_hint`, `post_logout_redirect_uri`), `/` → `/articles`, 로그인 상태 표시 |
| `CsrfTest` | `/api/csrf`가 `XSRF-TOKEN` 쿠키를 줌, 로그인했어도 토큰 없으면 403, 쿠키 값을 헤더로 보내면 201 |
| `BlogApiControllerTest` | 글 CRUD, 작성자 = 로그인한 회원, 응답의 `author` = 닉네임, 다른 회원의 글 수정·삭제 403 |
| `UserServiceTest` | 처음 보는 `sub`면 회원 생성, 같은 `sub`면 같은 회원이고 닉네임 갱신 |

`CsrfTest`에는 `@DirtiesContext(classMode = BEFORE_CLASS)`를 붙였다. 다른 테스트의 `.with(csrf())`는 테스트용 CSRF 저장소로 바꿔 끼우는데, 테스트 클래스들은 Spring 컨텍스트를 함께 쓰므로 그 영향이 남을 수 있다([accounts 트러블슈팅 11](./06_accounts_verification_and_troubleshooting.md)). 새 컨텍스트에서 실행하면 실행 순서와 상관없이 실제 설정을 확인한다.

> **`@DirtiesContext`란?**
> 테스트가 Spring 컨텍스트를 바꿔 놓았으니 다시 쓰지 말라고 표시하는 어노테이션이다. `BEFORE_CLASS`는 이 클래스를 실행하기 전에 컨텍스트를 새로 만들게 한다. 컨텍스트를 새로 만드는 데 시간이 들어서 꼭 필요한 곳에만 쓴다.

### 세 서버를 함께 띄운 확인
accounts backend(9000), accounts frontend(3001), blog(8080)를 띄우고 `curl`로 브라우저처럼 요청했다. 쿠키 저장 파일은 하나만 썼다. 브라우저도 `localhost`의 쿠키를 포트와 상관없이 한곳에 두기 때문이다.

| 순서 | 요청 | 결과 |
|---|---|---|
| 1 | blog `GET /new-article` (로그인 전) | 302 → `http://localhost:8080/oauth2/authorization/accounts` |
| 2 | 위 주소 | 302 → `http://localhost:3001/oauth2/authorize?response_type=code&client_id=blog&scope=openid%20profile%20email&...` |
| 3 | 위 주소 | 302 → `http://localhost:3001/login` |
| 4 | accounts 로그인 `POST /login/password` | 302 → 2의 권한 요청 주소 (저장된 요청) |
| 5 | 위 주소 | 302 → `http://localhost:8080/login/oauth2/code/accounts?code=...&state=...` |
| 6 | 위 주소 (blog가 code를 토큰으로 교환) | 302 → `http://localhost:8080/new-article?continue` |
| 7 | blog `GET /articles` | "에스에스오님", 로그아웃 버튼 |
| 8 | blog `POST /api/articles` (+ `X-XSRF-TOKEN`) | 201, `"author":"에스에스오"` |
| 9 | 틀린 CSRF 토큰으로 같은 요청 | 403 |
| 10 | **SSO**: blog 세션 쿠키만 지우고 다시 1~2 | accounts가 로그인 화면 없이 바로 code를 줌 → 로그인됨 |
| 11 | blog `POST /logout` | 302 → `http://localhost:3001/connect/logout?id_token_hint=eyJ...&post_logout_redirect_uri=http://localhost:8080/` |
| 12 | 위 주소 | 302 → `http://localhost:8080/`. 글 목록에 로그인 버튼 |
| 13 | accounts `GET /api/me` | 401 (accounts 로그인도 끝남) |
| 14 | 다시 blog 로그인 시작 | `http://localhost:3001/login` (비밀번호를 다시 물음) |

처음 확인에서는 6번이 `/login?error`로 끝났다(트러블슈팅 3).

## 9. 트러블슈팅

### 1. 로그인이 필요한 화면인데 테스트에서는 401
- **문제**: 로그인하지 않고 `GET /new-article`을 보내는 테스트가, accounts 로그인으로 보내는 302가 아니라 401을 받았다.
- **원인**: Spring Security는 로그인이 필요할 때 어떻게 응답할지(entry point)를 요청 종류마다 고른다. `oauth2Login()`이 등록하는 "로그인 화면으로 보내기"는 **HTML을 원하는 요청**(`Accept: text/html`)에만 쓰인다. 브라우저는 이 헤더를 보내지만 MockMvc 요청에는 `Accept`가 없다. 그래서 직접 등록한 다른 처리기(401)가 쓰였다.
  > **entry point(`AuthenticationEntryPoint`)란?**
  > 로그인하지 않은 사용자가 로그인이 필요한 주소에 왔을 때 응답을 만드는 객체다. 로그인 화면으로 302를 보내거나, 401을 보낸다.
- **해결**: 테스트 요청에 `.accept(MediaType.TEXT_HTML)`을 붙였다. 리다이렉트 주소는 MockMvc에서 상대 주소(`/oauth2/authorization/accounts`)로 나온다([accounts 트러블슈팅 4](./06_accounts_verification_and_troubleshooting.md)).

### 2. 로그인하지 않은 API 요청인데 401이 아니라 403
- **문제**: CSRF 보호를 켜자, 로그인하지 않고 `POST /api/articles`를 보내는 테스트가 403을 받았다.
- **원인**: 필터 체인에서 `CsrfFilter`가 `AuthorizationFilter`보다 앞에 있다. CSRF 토큰이 없는 쓰기 요청은 로그인 여부를 보기 전에 403으로 거절된다.
- **해결**: 의도한 동작이다. "로그인 안 됨 → 401"만 확인하는 테스트에는 `.with(csrf())`로 토큰을 넣었다.

### 3. accounts 로그인 후 blog로 돌아오면 `/login?error`
- **문제**: 세 서버를 함께 띄운 확인에서, accounts 로그인까지는 성공했지만 blog로 돌아오자 `/login?error`로 이동했다. Spring Security의 기본 오류 화면에는 `Invalid credentials`만 나왔고, 서버 로그에도 오류가 없었다.
- **원인 찾기**: 6번 단계에서 blog 서버는 두 곳을 부른다. code를 토큰으로 바꾸는 `/oauth2/token`, 서명 공개 키를 받는 `/oauth2/jwks`. 둘 다 `http://localhost:3001`, 즉 accounts 화면을 띄운 **Next.js 서버**로 보내고 있었다.
  Spring의 HTTP 클라이언트는 Java 기본 `HttpClient`를 쓴다. 이 클라이언트는 `http://` 주소에 요청할 때 "HTTP/2로 바꾸자"는 헤더(`Upgrade: h2c`)를 함께 보낸다. `curl --http2`로 같은 헤더를 보내 확인했다.

  | 요청 | 결과 |
  |---|---|
  | `curl http://localhost:3001/.well-known/openid-configuration` | 200 |
  | `curl --http2 http://localhost:3001/...` (업그레이드 헤더 포함) | 빈 응답, 연결 끊김 |
  | `curl --http2 http://localhost:9000/...` (Spring에 직접) | 200 (HTTP/1.1로 응답) |

  Next.js는 `Upgrade` 헤더가 있는 요청을 WebSocket 연결로 처리하려 해서, 일반 요청으로 응답하지 않았다.
  > **`Upgrade` 헤더란?**
  > 지금 연결을 다른 통신 방식(HTTP/2, WebSocket 등)으로 바꾸자고 서버에 제안하는 헤더다. 서버가 받아들이지 않으면 원래 방식(HTTP/1.1)으로 응답하는 것이 규칙이지만, 서버 구현에 따라 다르게 처리한다.
- **해결**: 주소를 두 종류로 나눴다.

  | 종류 | 누가 부르나 | 설정 | 로컬 값 |
  |---|---|---|---|
  | front-channel | 브라우저 (권한 요청, 로그아웃) | `accounts.issuer` | `http://localhost:3001` |
  | back-channel | blog 서버 (토큰 교환, 공개 키) | `accounts.backchannel-uri` | `http://localhost:9000` |

  ID 토큰의 `iss`는 accounts가 정한 issuer(`http://localhost:3001`)로 고정되어 있어서, 어느 주소로 토큰을 받든 검사는 그대로 통과한다. `backchannel-uri`가 없으면 issuer를 쓰므로, 배포 환경에서 같은 주소를 쓴다면 설정하지 않아도 된다.
  > **front-channel, back-channel이란?**
  > front-channel은 브라우저를 거쳐 오가는 통신(리다이렉트)이고, back-channel은 서버끼리 직접 주고받는 통신이다. Client Secret과 토큰은 back-channel로만 오간다.
- **배운 것**: Spring Security의 OAuth2 로그인 실패는 기본 화면과 로그에 원인이 나오지 않는다. 원인 후보(서버가 부르는 주소)를 하나씩 `curl`로 재현해서 찾았다.

### 4. 옛 코드를 남겨 둔 채로 보안 설정만 바꿀 수 없었다
- **문제**: 2-1에서 보안 설정만 바꾸고 옛 클래스는 다음 커밋에서 지우려 했는데, 그 상태로는 서버가 켜지지 않는다.
- **원인**: `OAuth2SuccessHandler`가 `@Component`라서 보안 설정에서 쓰지 않아도 Spring이 빈으로 만든다. 이 클래스는 생성자로 쿠키 저장소 빈을 받는데, 그 빈은 바꾼 보안 설정에서 지웠다.
- **해결**: 로그인 방식 교체(등록 + 옛 코드 삭제)를 한 커밋으로 묶었다. 지운 코드와 대체한 기능은 4장 표에 정리했다.

## 10. 2단계 이후의 구조
```
브라우저
  ├─ localhost:8080  blog (Spring)
  │     · oauth2Login → accounts
  │     · 세션 BLOG_SESSION, CSRF XSRF-TOKEN
  │     · users(accounts_sub, nickname), article(author_id)
  │        │ back-channel: 토큰 교환, 공개 키
  │        ▼
  │   localhost:9000  accounts backend (Spring Authorization Server)
  │        ▲ rewrites
  └─ localhost:3001  accounts frontend (Next.js)  ← front-channel: 로그인, 로그아웃
```

## 11. 남은 것
| 남은 것 | 비고 |
|---|---|
| blog 화면을 Next.js로 | 3단계. 지금은 임시 Thymeleaf 화면 |
| 배포 준비 | accounts 서명 키·DB 영구 저장, 메일 발송, HTTPS 주소, `Secure` 쿠키 |
| 다른 antimony.kr 앱 연결 | accounts에 앱을 등록하면 같은 방법으로 연결 |
