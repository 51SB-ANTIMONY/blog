---
title: accounts 인가 서버 구현 과정 (1-1 ~ 1-3)
date: 2026-10-03
tags: [oidc, authorization-server, spring-security, implementation]
---

# accounts 인가 서버 구현 과정 (1-1 ~ 1-3)

> 개념은 [인가 서버와 Spring Authorization Server](./04_spring_authorization_server.md), 설계는 [antimony.kr 통합 로그인 설계](./02_antimony_accounts_design.md)에 정리했다.
> 이 노트는 실제로 만든 과정과 코드를 정리한다. 동작 확인 결과와 겪은 문제는 [accounts 동작 확인과 트러블슈팅](./06_accounts_verification_and_troubleshooting.md)에 있다.
> Spring Boot 4.0.1, Spring Security 7.0.2 기준이다.

## 1. 전체 순서
blog 서버에 있던 로그인 코드를 accounts로 옮긴 뒤, 그것을 표준 인가 서버로 바꾸는 순서로 진행했다. "옮기기"와 "구조 바꾸기"를 서로 다른 커밋으로 나눠서, 각 단계에서 무엇이 바뀌었는지 diff로 분명히 보이게 했다.

| 단계 | 커밋 | 내용 |
|---|---|---|
| 준비 | `45829cd` | blog 글 API 정리 (응답 형식 통일, 404/403/400 에러 처리, 입력값 검증) |
| 준비 | `eade9bf` | blog `User`에 `uid` 추가, `nickname` `NOT NULL`, OAuth 첫 로그인 시 닉네임 자동 설정 |
| 준비 | `074debf` | blog 패키지를 `kr.antimony`에서 `kr.antimony.blog`로 변경 |
| 1-1 | `d525572` | `accounts/backend` 독립 프로젝트 뼈대 |
| 1-2 | `2d09b81` | blog의 인증 코드를 그대로 복사 |
| 1-3 | `cde278b` | 토큰 처리를 Spring Authorization Server로 교체 |

## 2. 폴더 구조와 독립성
```
blog/                          ← 저장소
├── accounts/                  ← 나중에 이 폴더만 떼어 새 저장소로 옮긴다
│   ├── backend/               ← Spring Boot 인가 서버 (자체 Gradle)
│   ├── frontend/              ← Next.js 로그인 화면 (예정)
│   └── README.md
├── backend/                   ← blog 서버 (kr.antimony.blog)
├── frontend/                  ← blog 화면 (예정)
└── note/
```

나중에 `accounts/`만 떼어 냈을 때 그대로 빌드되고 실행되도록 다음 규칙을 지켰다.

| 규칙 | 이유 |
|---|---|
| `accounts/backend`에 자체 `settings.gradle`, `build.gradle`, `gradlew`, `.gitignore`를 둔다 | 루트나 blog의 빌드 설정에 기대지 않게 |
| blog 코드를 import하지 않는다. 필요하면 복사해서 패키지를 바꾼다 | 폴더 밖의 코드는 새 저장소에 따라가지 않는다 |
| 커밋 하나에는 `accounts/` 안의 파일만, 또는 밖의 파일만 넣는다 | `git subtree split`으로 accounts 이력만 깔끔하게 뽑아낼 수 있다 |
| blog와 accounts는 코드가 아니라 **설정값(주소, client_id, Secret)과 OIDC 표준**으로만 연결한다 | 두 서버가 서로의 내부를 몰라도 동작 |

나중에 떼어 낼 때 쓸 명령:
```bash
git subtree split --prefix=accounts -b accounts-only   # accounts/ 이력만 뽑은 브랜치
```

## 3. 1-1: 프로젝트 뼈대
| 파일 | 내용 |
|---|---|
| `settings.gradle` | `rootProject.name = 'accounts'` |
| `build.gradle` | Spring Boot 4.0.1, Java 25. blog와 같은 버전으로 맞춰서, 1-2에서 복사한 코드가 수정 없이 동작하게 했다 |
| `gradlew`, `gradle/wrapper/` | blog의 것을 복사 (Gradle 9.2.0) |
| `AccountsApplication.java` | `kr.antimony.accounts` 패키지의 시작 클래스 |
| `application.yml.example` | 비밀 값을 뺀 설정 견본. 실제 `application.yml`은 Git 제외 |

로컬 설정에서 정한 것:
```yaml
server:
  port: 9000
  servlet:
    session:
      cookie:
        name: ACCOUNTS_SESSION
```
**세션 쿠키 이름을 바꾼 이유:** 쿠키는 포트를 구분하지 않는다([쿠키](../14_oauth/03_cookie.md) 5장). accounts(`localhost:9000`)와 blog(`localhost:8080`)가 둘 다 기본 이름 `JSESSIONID`를 쓰면, 같은 `localhost` 쿠키를 서로 덮어써서 로그인이 계속 풀린다.

## 4. 1-2: blog 코드를 그대로 복사
blog(`kr.antimony.blog`)의 인증 관련 파일 29개를 accounts(`kr.antimony.accounts`)로 복사하고 **패키지 이름만** 바꿨다.

| 분류 | 복사한 파일 |
|---|---|
| 보안 설정 | `WebOAuthSecurityConfig`, `TokenAuthenticationFilter` |
| JWT | `TokenProvider`, `JwtProperties` |
| OAuth | `OAuth2UserCustomService`, `OAuth2SuccessHandler`, `OAuth2AuthorizationRequestBasedOnCookieRepository`, `CookieUtil` |
| 회원 | `User`, `UserRepository`, `UserService`, `UserDetailService`, `AddUserRequest`, `UserApiController`, `UserViewController` |
| 토큰 재발급 | `RefreshToken`, `RefreshTokenRepository`, `RefreshTokenService`, `TokenService`, `TokenApiController`, 요청/응답 DTO |
| 화면 | `oauthLogin.html`, `login.html`, `signup.html`, `google.png` |
| 테스트 | `TokenProviderTest`, `TokenApiControllerTest`, `JwtFactory` |

**고친 곳은 한 곳뿐이다.** `OAuth2SuccessHandler`가 로그인 후 `/articles?token=...`으로 보냈는데, accounts에는 글 화면이 없다. 보낼 주소를 설정값으로 뺐다.
```java
@Value("${accounts.login-success-redirect-url}")   // http://localhost:8080/articles
private String loginSuccessRedirectUrl;
```

**복사하지 않은 것:** 글 기능 전부, 주석으로만 남아 있던 `WebSecurityConfig`, blog 화면용 `token.js`. blog의 원본은 지우지 않았다. blog는 2단계에서 accounts에 연결되기 전까지 지금의 Google 로그인으로 계속 동작해야 하기 때문이다.

**이 시점의 accounts:** blog의 로그인을 떼어 낸 "토큰 발급 서버"다. OIDC도 SSO도 아니고, blog의 트러블슈팅 문제(로그아웃, 쿠키 역직렬화, URL 토큰 노출 등)를 그대로 가지고 있다. 리프레시 토큰이 accounts DB에 저장되므로 blog의 `/api/token` 재발급은 실패한다.

## 5. 1-3: Spring Authorization Server로 교체

### 의존성
```gradle
// 추가
implementation 'org.springframework.boot:spring-boot-starter-oauth2-authorization-server'
// 삭제
implementation 'io.jsonwebtoken:jjwt:0.9.1'
implementation 'javax.xml.bind:jaxb-api:2.3.1'
```
이 스타터를 추가하면 다음이 함께 들어온다(`./gradlew dependencies`로 확인).
| 라이브러리 | 역할 |
|---|---|
| `spring-security-oauth2-authorization-server` 7.0.2 | 인가 서버 필터들 |
| `spring-security-oauth2-resource-server` 7.0.2 | `/userinfo`의 액세스 토큰 검증 |
| `spring-security-oauth2-jose` 7.0.2 | JWT 생성, 검증 |
| `nimbus-jose-jwt` 10.4 | JWT, JWK를 실제로 처리하는 라이브러리 |

### 지운 코드와 대체한 기능
| 지운 코드 (1-2에서 복사) | 대신하는 것 |
|---|---|
| `TokenProvider`, `JwtProperties` (HS256) | `JWKSource` (RS256 키), 인가 서버의 토큰 생성기 |
| `OAuth2SuccessHandler` (`?token=`으로 리다이렉트) | 로그인 성공 후 저장해 둔 `/oauth2/authorize` 요청으로 돌아가는 기본 동작 |
| `RefreshToken`, `RefreshTokenRepository`, `RefreshTokenService`, `TokenService`, `TokenApiController`, DTO 2개 | `/oauth2/token` (`grant_type=refresh_token`), `OAuth2AuthorizationService` |
| `TokenAuthenticationFilter` | (accounts에는 필요 없음. 토큰 검증은 각 앱의 일) |
| `OAuth2AuthorizationRequestBasedOnCookieRepository`, `CookieUtil` | 세션에 저장하는 기본 방식 |
| `oauthLogin.html` | `login.html`에 Google 버튼을 합침 |
| `TokenProviderTest`, `TokenApiControllerTest`, `JwtFactory` | `AuthorizationServerTest` |
| `WebOAuthSecurityConfig` (체인 1개, `STATELESS`) | `AuthorizationServerConfig` + `SecurityConfig` (체인 2개, 세션) |

### 체인 ①: `AuthorizationServerConfig`
```java
@Bean
@Order(1)
public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    http
            .oauth2AuthorizationServer(authorizationServer -> {
                http.securityMatcher(authorizationServer.getEndpointsMatcher());
                authorizationServer.oidc(Customizer.withDefaults());
            })
            .authorizeHttpRequests(auth -> auth
                    .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions
                    .defaultAuthenticationEntryPointFor(
                            new LoginUrlAuthenticationEntryPoint("/login"),
                            new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            .oauth2ResourceServer(resourceServer -> resourceServer
                    .jwt(Customizer.withDefaults()));
    return http.build();
}
```
| 코드 | 의미 |
|---|---|
| `oauth2AuthorizationServer(...)` | 인가 서버 필터들을 이 체인에 추가한다 |
| `http.securityMatcher(authorizationServer.getEndpointsMatcher())` | 이 체인은 인가 서버 주소(`/oauth2/**`, `/.well-known/**`, `/userinfo`, `/connect/**`)만 담당한다 |
| `oidc(Customizer.withDefaults())` | OpenID Connect 기능(ID 토큰, `/userinfo`, 설정 문서, 로그아웃)을 켠다 |
| `anyRequest().authenticated()` | 이 주소들은 인증이 필요하다. 단, 토큰 주소는 사용자가 아니라 **앱**을 인증한다 |
| `defaultAuthenticationEntryPointFor(..., TEXT_HTML)` | **브라우저 요청**(HTML을 원하는 요청)이 로그인 안 된 상태면 `/login`으로 보낸다. 서버 간 요청은 401 |
| `oauth2ResourceServer().jwt()` | `/userinfo`는 액세스 토큰(`Authorization: Bearer`)으로 인증한다 |

> **`@Order(1)`이란?**
> 필터 체인이 여러 개일 때 검사 순서다. 숫자가 작을수록 먼저 검사한다. 체인 ①은 담당 주소가 좁으므로 먼저, 모든 주소를 담당하는 체인 ②는 나중에 검사해야 한다.

> **`MediaTypeRequestMatcher(TEXT_HTML)`이란?**
> 요청의 `Accept` 헤더에 `text/html`이 있는 요청, 즉 브라우저가 보낸 요청과 맞는 조건이다. 브라우저 사용자는 로그인 화면으로 보내고, 다른 서버에는 302 대신 401을 주기 위해 쓴다.

### 등록된 앱: `RegisteredClientRepository`
```java
RegisteredClient blog = RegisteredClient.withId("blog")
        .clientId("blog")
        .clientSecret(passwordEncoder.encode(blogClientSecret))
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri(blogRedirectUri)
        .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE).scope(OidcScopes.EMAIL)
        .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .requireProofKey(true)
                .build())
        .tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(30))
                .refreshTokenTimeToLive(Duration.ofDays(14))
                .reuseRefreshTokens(false)
                .build())
        .build();
return new InMemoryRegisteredClientRepository(blog);
```
| 설정 | 값 | 이유 |
|---|---|---|
| `clientSecret` | 설정값을 BCrypt로 암호화해서 저장 | 인가 서버는 토큰 요청 때 받은 Secret을 `PasswordEncoder`로 비교한다. 이 프로젝트의 `PasswordEncoder`가 BCrypt이므로 암호화된 값을 넣어야 한다 |
| `clientAuthenticationMethod` | `client_secret_basic` | Spring Security 클라이언트(2단계의 blog)의 기본 방식과 맞춤 |
| `authorizationGrantType` | `authorization_code`, `refresh_token` | 권한 부여 코드 방식과 리프레시 토큰 재발급만 허용 |
| `redirectUri` | `http://localhost:8080/login/oauth2/code/accounts` | blog가 2단계에서 쓸 주소. Spring Security 클라이언트의 기본 형식 `{baseUrl}/login/oauth2/code/{registrationId}` |
| `scope` | `openid`, `profile`, `email` | |
| `requireAuthorizationConsent(false)` | 동의 화면 생략 | 자사 앱이므로 |
| `requireProofKey(true)` | PKCE 필수 | Spring Security 7 인가 서버의 기본값을 명시 ([트러블슈팅](./06_accounts_verification_and_troubleshooting.md) 2번) |
| 액세스 토큰 수명 | 30분 | |
| 리프레시 토큰 수명 | 14일 | |
| `reuseRefreshTokens(false)` | 재발급할 때마다 리프레시 토큰도 교체 | [리프레시 토큰](../13_jwt/06_refresh_token.md) 5장의 교체(Rotation) |

지금은 앱 등록 정보를 메모리(`InMemoryRegisteredClientRepository`)에 둔다. 앱이 늘어나면 DB(`JdbcRegisteredClientRepository`)로 바꾼다.

### 서명 키: `JWKSource`
```java
@Bean
public JWKSource<SecurityContext> jwkSource() {
    KeyPair keyPair = generateRsaKey();             // RSA 2048비트
    RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
            .privateKey((RSAPrivateKey) keyPair.getPrivate())
            .keyID(UUID.randomUUID().toString())    // 토큰 헤더의 kid
            .build();
    return new ImmutableJWKSet<>(new JWKSet(rsaKey));
}
```
- 개인 키로 토큰을 서명하고, 공개 키는 `/oauth2/jwks`로 공개된다.
- `keyID`는 토큰 헤더의 `kid`가 된다. 앱은 `kid`로 어떤 공개 키로 검증할지 고른다.
- 여기서 `SecurityContext`는 **Nimbus 라이브러리의 타입**(`com.nimbusds.jose.proc.SecurityContext`)이다. Spring Security의 `SecurityContext`와 이름만 같다. blog 테스트에서 이 둘이 잘못 import되어 에러가 났던 적이 있다.
- **한계:** 서버를 시작할 때마다 새 키를 만든다. 재시작하면 이전에 발급한 토큰을 검증할 수 없다. 배포 전에 키를 저장해 두고 읽도록 바꿔야 한다.

### `/userinfo`용 토큰 검증: `JwtDecoder`
```java
@Bean
public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
    jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    return new NimbusJwtDecoder(jwtProcessor);
}
```
체인 ①의 `oauth2ResourceServer().jwt()`가 이 디코더로 `/userinfo` 요청의 액세스 토큰을 검증한다. 알고리즘을 `RS256`으로 고정해서, 토큰 헤더의 `alg`를 바꾸는 공격([JWT의 서명과 검증](../13_jwt/05_jwt_signature_and_validation.md) 5장)을 막는다.

### issuer: `AuthorizationServerSettings`
```java
AuthorizationServerSettings.builder().issuer(issuer).build();   // http://localhost:9000
```
토큰의 `iss` 클레임과 설정 문서의 `issuer`가 된다. 앱은 이 값과 토큰의 `iss`가 같은지 확인한다.

### 체인 ②: `SecurityConfig`
```java
@Bean
@Order(2)
public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/signup", "/user", "/error").permitAll()
                    .anyRequest().authenticated())
            .formLogin(formLogin -> formLogin
                    .loginPage("/login"))
            .oauth2Login(oauth2 -> oauth2
                    .loginPage("/login")
                    .userInfoEndpoint(userInfoEndpoint -> userInfoEndpoint.userService(oAuth2UserCustomService)))
            .build();
}
```
1-2의 설정과 비교하면 다음이 바뀌었다.

| 항목 | 1-2 | 1-3 |
|---|---|---|
| 세션 | `STATELESS` | 사용 (기본값). SSO의 근거 |
| CSRF | 끔 | **켬** (기본값). 세션 쿠키를 쓰므로 필요 |
| 폼 로그인 | 끔 | 켬. `UserDetailService`가 `uid`로 회원을 찾는다 |
| 로그아웃 필터 | 끔 | 켬 (기본값, `POST /logout`) |
| 토큰 필터 | `TokenAuthenticationFilter` | 없음 |
| Google 로그인 성공 처리 | `OAuth2SuccessHandler` | 기본 동작 (저장된 원래 요청으로 이동) |

### 로그인 화면: `login.html`
- 아이디·비밀번호 폼과 Google 버튼을 한 화면에 모았다. `UserViewController`의 `/login`이 `oauthLogin` 대신 `login`을 반환한다.
- 입력칸을 이메일에서 **ID**(`uid`)로 바꿨다. 폼의 이름은 Spring Security 기본값인 `username`이다.
- CSRF가 켜졌으므로 폼에 숨겨진 `_csrf` 값이 함께 전송된다.
- `?error`이면 "아이디 또는 비밀번호를 확인해 주세요.", `?logout`이면 "로그아웃되었습니다."를 보여 준다.

### 설정 파일
```yaml
accounts:
  issuer: http://localhost:9000
  clients:
    blog:
      client-secret: <blog 클라이언트의 Client Secret>
      redirect-uri: http://localhost:8080/login/oauth2/code/accounts
```
1-2의 `jwt.*`, `accounts.login-success-redirect-url`은 지웠다. 로컬 설정의 blog Client Secret은 임의의 값을 새로 만들어 넣었다. 2단계에서 blog 서버 설정에도 같은 값을 넣는다.

## 6. 1-3 이후의 구조
```
accounts.antimony.kr (지금은 localhost:9000)
 │
 ├ 체인 ① AuthorizationServerConfig    /oauth2/**, /.well-known/**, /userinfo, /connect/**
 │    · 인가 서버 필터 (Spring Authorization Server)
 │    · 등록된 앱: blog (client_secret_basic, PKCE 필수)
 │    · 서명: RS256 (JWKSource), 공개 키 /oauth2/jwks
 │
 └ 체인 ② SecurityConfig                그 외
      · 폼 로그인 (uid + 비밀번호)  → UserDetailService
      · Google 로그인             → OAuth2UserCustomService (첫 로그인 시 회원 생성)
      · 세션 (ACCOUNTS_SESSION), CSRF
```

## 7. 아직 남은 것
| 남은 것 | 단계 |
|---|---|
| 회원가입 API(`POST /api/signup`), `uid` 제약(`NOT NULL`, `UNIQUE`), 입력값 검증, `UserService`의 `PasswordEncoder` 주입 | 1-4 |
| Google 계정을 이메일이 아니라 Google의 `sub`로 연결 (`user_identities`) | 1-5 |
| ID 토큰의 `sub`를 회원 id로 통일, `nickname`·`email` 클레임, 전체 로그아웃 | 1-6 |
| 화면을 Next.js로 교체 | 1-7 |
| 서명 키 저장, 앱 등록 정보와 발급 토큰을 DB에 저장 | 배포 전 |
