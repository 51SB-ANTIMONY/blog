---
title: accounts 인가 서버 구현 과정 (1-1 ~ 1-7)
date: 2026-10-03
tags: [oidc, authorization-server, spring-security, nextjs, implementation]
---

# accounts 인가 서버 구현 과정 (1-1 ~ 1-7)

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
| 1-4 | `d226214` | 아이디·비밀번호 회원가입 정리 |
| 1-5 | `6f04e27` | 소셜 계정을 이메일이 아니라 제공자의 `sub`로 연결 |
| 1-6 | `8202648` | 토큰의 `sub`를 회원 id로 통일, `nickname`·`email` 클레임, 전체 로그아웃 |
| 1-7 | `fd7783b` | 화면(Next.js)이 쓸 API: 이메일 인증 가입, 아이디·비밀번호 찾기, 내 계정, 로그인 상태 유지 |
| 1-7 | `dac7fb9` | 로그인 뒤 API 주소로 이동하던 문제 수정 |
| 1-7 | `39c94ec` | Next.js 화면 (`accounts/frontend`) |

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

## 6. 1-4: 아이디·비밀번호 회원가입 정리

### 정한 것
| 결정 | 내용 |
|---|---|
| `uid` 제약 | `NOT NULL`, `UNIQUE` |
| 소셜 회원의 `uid` | 자동 생성. `제공자_임의값` (예: `google_3f9a2c1b`). 비밀번호가 없으므로 이 아이디로 비밀번호 로그인은 할 수 없다 |
| 입력값 규칙 | `uid` 4~20자 영문 소문자·숫자·밑줄, `email` 형식 검사, `nickname` 2~20자, `password` 8~64자 |
| 중복 | `uid`, `email`, `nickname` 중 하나라도 있으면 409와 필드별 메시지 |

### 바뀐 코드
| 파일 | 내용 |
|---|---|
| `User` | `uid`에 `nullable = false, unique = true` |
| `SignupRequest` (이전 `AddUserRequest`) | Bean Validation 어노테이션(`@NotBlank`, `@Pattern`, `@Email`, `@Size`)으로 입력값 규칙 |
| `SignupResponse` | 회원가입 응답. 비밀번호는 담지 않는다 |
| `SignupApiController` | `POST /api/signup` (JSON). Next.js 화면이 쓸 API |
| `UserService.signup()` | 중복 검사 → 필드별 에러를 모아 `DuplicateUserException`(409) → 비밀번호 BCrypt 저장 |
| `UserService.registerOAuthUser()` | 소셜 가입. `OAuth2UserCustomService`에 있던 로직을 옮겼다. `uid` 자동 생성, 닉네임 중복 시 숫자 4자리 추가 |
| `PasswordEncoderConfig` | `PasswordEncoder` 빈을 보안 설정에서 분리 |
| `exception/` | `ApiException`, `DuplicateUserException`, `ErrorResponse`, `GlobalExceptionHandler`. blog와 같은 `{"message", "errors"}` 형식 (코드를 공유하지 않고 따로 작성) |
| `UserApiController` (`POST /user`) | 임시 Thymeleaf 폼도 같은 `UserService.signup()`을 쓴다. 실패하면 `/signup?error` 또는 `/signup?duplicate` |
| `SecurityConfig` | `/api/signup` 허용. CSRF는 켜 둔 채로 둔다 |

### `PasswordEncoder`를 분리한 이유 (순환 참조)
blog에서 복사해 온 `UserService`는 `PasswordEncoder`를 주입받지 않고 `new BCryptPasswordEncoder()`로 직접 만들고 있었다. 주입받으면 다음 순서로 서로를 기다리는 **순환 참조**가 생기기 때문이었다.
```
SecurityConfig ─(생성자)→ OAuth2UserCustomService ─→ UserService ─→ PasswordEncoder
      ▲                                                                   │
      └──────────────────── PasswordEncoder 빈이 SecurityConfig 안에 있음 ◀─┘
```
> **순환 참조란?**
> 빈 A를 만들려면 빈 B가 필요하고, 빈 B를 만들려면 다시 빈 A가 필요한 상태다. Spring은 어느 쪽도 먼저 만들 수 없어서 시작에 실패한다.

`PasswordEncoder` 빈을 별도 설정 클래스(`PasswordEncoderConfig`)로 옮겨 고리를 끊고, `UserService`가 정상적으로 주입받게 했다.

### 닉네임 중복 처리
blog 트러블슈팅 9번(이름이 같은 Google 사용자가 가입하면 `nickname`의 `UNIQUE` 제약에 걸려 실패)과 같은 문제가 accounts에도 있었다. 소셜 가입에서는 사용자가 닉네임을 직접 고를 수 없으므로, 이미 있으면 뒤에 숫자 4자리를 붙인다(예: `홍길동` → `홍길동4821`). 이름이 없으면 이메일의 `@` 앞부분을 쓰고, 20자를 넘으면 자른다.

### CSRF
`/api/signup`은 세션 쿠키를 쓰는 서버의 `POST` 요청이므로 CSRF 보호를 끄지 않았다. 지금은 CSRF 토큰을 세션에 저장한다. 1-7에서 Next.js 화면이 이 API를 부를 때는 CSRF 토큰을 쿠키(`XSRF-TOKEN`)로 받아 헤더(`X-XSRF-TOKEN`)로 보내도록 바꾼다.

## 7. 1-5: 소셜 계정을 `sub`로 연결

### 왜 바꿨나
1-4까지는 Google 회원을 **이메일**로 찾았다. 두 가지 문제가 있었다.
| 문제 | 설명 |
|---|---|
| 자동 계정 합치기 | 아이디로 `a@gmail.com`을 가입한 회원이 있으면, 같은 이메일의 Google 계정으로 로그인한 사람이 **그 회원으로 로그인**된다. 이메일이 같다는 것만으로 같은 사람이라고 확신할 수 없다([antimony.kr 통합 로그인 설계](./02_antimony_accounts_design.md) 5장) |
| 이메일 변경 | Google 계정의 이메일이 바뀌면 같은 사람인데도 다른 회원으로 취급된다 |

대형 서비스처럼 **"계정"(users)과 "로그인 수단"(user_identities)을 나누고**, 소셜 계정은 제공자가 주는 **변하지 않는 계정 고유 번호(`sub`)**로 구별하도록 바꿨다.

### `user_identities` 테이블
```sql
create table user_identities (
    id bigint generated by default as identity,
    user_id bigint not null,             -- users.id (외래 키)
    provider varchar(255) not null,      -- google
    provider_subject varchar(255) not null,  -- Google의 sub
    created_at timestamp(6),
    primary key (id),
    unique (provider, provider_subject)  -- 같은 소셜 계정은 한 회원에만 연결
)
```
- 한 회원은 여러 소셜 계정을 가질 수 있다(`@ManyToOne`). 나중에 네이버, 카카오를 추가해도 테이블은 그대로 쓴다.
- `created_at`을 채우려고 `AccountsApplication`에 `@EnableJpaAuditing`을 추가했다.

### 로그인 규칙
| 상황 | 결과 |
|---|---|
| 처음 보는 소셜 계정, 같은 이메일의 회원 없음 | 자동 가입 (1-4의 규칙: `uid` 자동 생성, 닉네임 중복 시 숫자) + 연결 행 생성 |
| 이미 연결된 소셜 계정 | 연결된 회원으로 로그인. 이메일이 바뀌었어도 같은 회원 |
| 처음 보는 소셜 계정, **같은 이메일의 회원이 있음** | **거부** (`email_already_registered`) |
| **아이디로 로그인한 상태**에서 소셜 로그인 | 그 회원에 소셜 계정을 **연결** (명시적 연결) |
| 연결하려는 소셜 계정이 이미 다른 회원에 연결됨 | 거부 (`linked_to_another_user`) |

### 바뀐 코드
| 파일 | 내용 |
|---|---|
| `UserIdentity`, `UserIdentityRepository` | 연결 엔티티. 조회할 때 회원도 함께 읽는다(`@EntityGraph`) |
| `UserService.loginWithSocial()` | `(provider, sub)`로 연결을 찾고, 없으면 가입. 같은 이메일의 회원이 있으면 `SocialLoginException` |
| `UserService.linkSocial()` | 로그인한 회원(`uid`)에 소셜 계정 연결 |
| `OAuth2UserCustomService.resolveUser()` | 현재 로그인 상태를 보고 결정. `Authentication`의 `principal`이 우리 `User`(폼 로그인)면 연결, 아니면 소셜 로그인 |
| `SocialLoginException` | 오류 코드(`email_already_registered`, `linked_to_another_user`)와 메시지 |
| `SecurityConfig` | 소셜 로그인 실패 처리. `/login?social_error=<오류 코드>`로 보낸다 |
| `login.html` | 오류 코드별 메시지 |

### 현재 로그인 상태를 어떻게 아는가
`OAuth2UserCustomService.loadUser()`는 Google 로그인 처리 도중(`OAuth2LoginAuthenticationFilter` 안에서) 호출된다. 이 필터는 `SecurityContextHolderFilter`보다 뒤에 있으므로, 이 시점의 `SecurityContextHolder`에는 **세션에 저장된 기존 로그인 정보**가 들어 있다([필터 기반 동작 원리](../12_spring_security/03_filter_architecture.md) 3장). 아이디로 로그인한 상태였다면 그 `Authentication`의 `principal`이 `UserDetailService`가 돌려준 `User`다. 이것으로 "연결"인지 "로그인"인지 구별한다.

### 소셜 로그인 실패를 화면에 보여 주는 방법
`OAuth2UserCustomService`가 `SocialLoginException`을 `OAuth2AuthenticationException`(오류 코드 포함)으로 바꿔 던지면, Spring Security가 로그인 실패로 처리하고 실패 처리기를 부른다. 실패 처리기는 오류 코드를 주소에 붙여 로그인 화면으로 보낸다.
```
/login?social_error=email_already_registered
```
메시지 대신 **코드**를 주소에 붙인 이유: 1-7의 Next.js 화면도 같은 코드를 보고 자기 문구를 고를 수 있다.

## 8. 1-6: 토큰의 `sub`를 회원 id로 통일

### 왜 바꿨나
1-5까지는 ID 토큰의 `sub`가 로그인 방식에 따라 달랐다. 아이디로 로그인하면 `uid`(`tester01`), Google로 로그인하면 Google이 준 번호였다. 같은 사람이 어떤 방법으로 로그인했는지에 따라 blog가 받는 `sub`가 달라지면, blog는 두 사람으로 여긴다.

> **`sub` 클레임이란?**
> ID 토큰 안의 "이 토큰이 누구에 대한 것인가"를 나타내는 값이다. OIDC 규칙상 **한 인가 서버 안에서 한 사람에게 하나뿐이고 바뀌지 않는 값**이어야 한다. 앱(blog)은 이 값으로 자기 DB의 회원과 연결한다.

`uid`는 사람이 고르는 값이라 나중에 바꾸게 할 수도 있다. 그래서 바뀌지 않는 **회원 id(`users.id`)**를 `sub`로 쓴다.

### 로그인 결과에서 회원 id 꺼내기: `AccountPrincipal`
로그인 방식마다 Spring Security가 세션에 넣는 사용자 객체(`principal`)가 다르다.

| 로그인 방식 | `principal` | 만드는 곳 |
|---|---|---|
| 아이디·비밀번호 | `AccountUserDetails` | `UserDetailService` |
| Google | `AccountOAuth2User` | `OAuth2UserCustomService` |

두 클래스가 모두 `AccountPrincipal` 인터페이스(`getUserId()` 하나)를 구현하게 했다. 토큰을 만드는 쪽은 로그인 방식을 몰라도 `AccountPrincipal`로 회원 id를 꺼낼 수 있다. 1-5까지는 `User` 엔티티가 `UserDetails`를 직접 구현했는데, 엔티티에서 보안 코드를 떼어 내 `AccountUserDetails`로 옮겼다.

> **`principal`이란?**
> 로그인한 사용자를 나타내는 객체로, `Authentication` 안에 들어 있다. 어떤 클래스를 넣을지는 로그인을 처리하는 코드가 정한다.

### 토큰에 값 넣기: `OAuth2TokenCustomizer`
Spring Authorization Server는 토큰을 서명하기 직전에 `OAuth2TokenCustomizer<JwtEncodingContext>` 빈을 호출한다. 여기서 클레임을 바꾸거나 더할 수 있다.

```java
context.getClaims().subject(String.valueOf(account.getUserId()));   // 모든 토큰의 sub = 회원 id
if (ID 토큰이면) {
    if (profile 범위) { nickname, preferred_username(=uid) }
    if (email 범위)   { email }
}
```

> **클레임이란?**
> 토큰 안에 담긴 "이름: 값" 한 쌍이다. `sub`, `iss`, `aud`, `exp`는 표준 클레임이고, `nickname`처럼 OIDC가 이름만 정해 둔 것도 있다.

> **scope(범위)란?**
> 앱이 권한 요청에서 "이 정보까지 주세요"라고 적는 값이다. `openid`는 OIDC를 쓰겠다는 뜻, `profile`은 이름·닉네임, `email`은 이메일. 인가 서버는 허락된 범위의 정보만 토큰에 담는다.

### 전체 로그아웃: `/connect/logout`
blog가 로그아웃할 때 accounts의 로그인 세션도 끝내야 다음에 다시 아이디·비밀번호를 묻는다. OIDC는 이 주소를 **RP-Initiated Logout**이라는 이름으로 정해 두었고, Spring Authorization Server에서는 `/connect/logout`이다.

> **RP-Initiated Logout이란?**
> RP(Relying Party, 인가 서버에 로그인을 맡기는 앱. 여기서는 blog)가 사용자를 인가 서버의 로그아웃 주소로 보내 인가 서버의 세션까지 끝내는 방식이다. 끝나면 앱이 미리 등록한 주소(`post_logout_redirect_uri`)로 돌려보낸다.

기본 동작은 세션만 지운다. 1-7에서 "로그인 상태 유지" 쿠키를 추가했으므로, 로그아웃 처리기(`OidcLogoutAuthenticationSuccessHandler`)에 세션 삭제와 쿠키 삭제를 묶은 `CompositeLogoutHandler`를 넣었다. 쿠키가 남으면 로그아웃했는데도 다음 요청에서 다시 로그인되기 때문이다.

## 9. 1-7 (1): 화면이 쓸 API

### 화면과 서버의 역할 나누기
화면(Next.js)은 입력칸과 안내 문구를 그리고, 판단은 Spring이 한다. 화면은 브라우저 안에서 JSON API를 부른다. 로그인과 로그아웃만은 API가 아니라 **폼 전송**으로 한다. 로그인 성공 뒤 어디로 갈지(원래 요청한 `/oauth2/authorize`로 돌아가기 등)를 Spring Security가 리다이렉트로 정하기 때문이다.

### 같은 주소에서 동작하게 하기
브라우저는 `localhost:3001`(Next.js)만 본다. Next.js의 `rewrites` 설정이 `/api/**`, `/login/password`, `/oauth2/**` 같은 경로를 `localhost:9000`(Spring)으로 넘긴다.

> **rewrites란?**
> Next.js 서버가 받은 요청을 다른 주소로 대신 보내고, 받은 응답을 그대로 브라우저에 돌려주는 설정이다. 브라우저의 주소창은 바뀌지 않는다.

이렇게 하면 화면과 API가 같은 출처(origin)라서 세션 쿠키가 그대로 오가고 CORS 설정이 필요 없다. Spring이 만드는 리다이렉트 주소와 issuer도 `localhost:3001`이어야 하므로 두 가지를 맞췄다.

- `server.forward-headers-strategy: framework`: Next.js가 붙여 주는 `X-Forwarded-Host: localhost:3001` 헤더를 보고, Spring이 자기 주소를 `localhost:3001`로 여긴다. 리다이렉트 `Location`이 `http://localhost:3001/...`로 나간다.
- `accounts.issuer: http://localhost:3001`: 토큰의 `iss`와 설정 문서의 주소가 화면 주소와 같아진다. blog는 이 주소 하나만 알면 된다.

> **`X-Forwarded-Host`란?**
> 요청을 대신 전달하는 서버(프록시)가 "원래 브라우저가 요청한 호스트는 이것이었다"고 알려 주는 헤더다. Spring은 이 설정이 있을 때만 이 헤더를 믿는다.

Google 콘솔의 승인된 리디렉션 URI도 `http://localhost:3001/login/oauth2/code/google`이 된다.

### CSRF: 쿠키로 내려 주고 헤더로 받기
`csrf.spa()` 한 줄로 설정했다. 서버는 CSRF 토큰을 `XSRF-TOKEN` 쿠키로 내려 주고(JavaScript가 읽을 수 있음), 요청에서는 두 가지 방법 중 하나로 받는다.

| 보내는 방법 | 값 | 쓰는 곳 |
|---|---|---|
| `X-XSRF-TOKEN` 헤더 | 쿠키 값 그대로 | `fetch`로 부르는 API |
| `_csrf` 폼 값 | `GET /api/csrf`가 돌려준 `token` | 로그인, 로그아웃 폼 전송 |

> **CSRF란?**
> 다른 사이트가 사용자의 브라우저를 이용해 이 사이트로 요청을 보내게 하는 공격이다. 브라우저는 쿠키를 자동으로 붙이므로, 서버는 쿠키만으로는 요청이 이 사이트 화면에서 온 것인지 알 수 없다. 그래서 다른 사이트는 알 수 없는 값(CSRF 토큰)을 요청에 함께 넣게 한다.

폼 값이 쿠키 값과 다른 이유: 폼으로 보내는 토큰은 응답마다 다른 값으로 **가려서(XOR)** 내려 준다. 응답 길이로 토큰을 알아내는 공격(BREACH)을 막기 위한 것이다. 헤더로 보내는 쪽은 쿠키 값을 그대로 받는다.

### 이메일 인증 가입
프로토타입대로 "가입 신청 → 메일로 받은 6자리 입력 → 가입 완료" 순서다.

1. `POST /api/signup`: 입력값과 중복을 검사하고, 아직 회원이 아니라 `pending_signups`에 신청을 저장한다. 인증번호를 메일로 보내고 202를 돌려준다.
2. `POST /api/signup/verify`: 인증번호가 맞으면 신청을 `users`로 옮긴다. 201.

인증번호 규칙(`VerificationCodeService`):

| 규칙 | 값 | 이유 |
|---|---|---|
| 자릿수 | 6자리 숫자 | 프로토타입 |
| 유효 시간 | 3분 | 화면의 "남은 시간 03:00" |
| 저장 | SHA-256 해시 | DB가 새어도 번호를 알 수 없게 |
| 틀린 횟수 | 5번이면 무효 | 6자리를 하나씩 넣어 보는 공격 방지 |

틀린 횟수를 저장하는 메서드에는 `@Transactional(noRollbackFor = VerificationException.class)`를 붙였다. 예외를 던지면 트랜잭션이 되돌아가서 늘린 횟수까지 사라지기 때문이다.

> **`noRollbackFor`란?**
> `@Transactional` 메서드에서 지정한 예외가 나도 변경 내용을 되돌리지 않고 저장하게 하는 옵션이다.

인증 전 신청을 `users`에 바로 넣지 않은 이유: 다른 사람의 이메일로 가입 신청만 해 두면 진짜 주인이 그 이메일로 가입하지 못하게 된다. 신청은 1시간 동안만 아이디·닉네임을 잡아 둔다.

메일은 `VerificationMailSender` 인터페이스로 보낸다. `spring.mail.host`가 없으면 `LoggingVerificationMailSender`가 서버 로그에 번호를 찍고, 있으면 `SmtpVerificationMailSender`가 실제로 보낸다.

### 아이디·비밀번호 찾기
| 단계 | 아이디 찾기 | 비밀번호 찾기 |
|---|---|---|
| 1 | `POST /api/find-id/code` (이메일) | `POST /api/password-reset/code` (아이디, 이메일) |
| 2 | `POST /api/find-id/verify` → `an******01`처럼 가린 아이디 | `POST /api/password-reset/verify` → 일회용 `resetToken` |
| 3 | | `POST /api/password-reset/complete` (`resetToken`, 새 비밀번호), 10분 안 |

1단계는 가입되지 않은 이메일이어도 같은 응답을 돌려준다. 응답이 다르면 "이 이메일이 가입되어 있는지"를 남이 알아낼 수 있기 때문이다.

### 내 계정, 로그인 상태 유지
- `GET /api/me`: 닉네임, 아이디, 이메일, 비밀번호 유무, 연결된 소셜 계정. 로그인하지 않았으면 401.
- `DELETE /api/me/identities/{provider}`: 소셜 계정 연결 해제. 비밀번호가 없는 회원의 마지막 소셜 계정은 해제할 수 없다(409). 해제하면 로그인할 방법이 사라지기 때문이다.
- `GET /api/auth/providers`: `{"google": true, "kakao": false, "naver": false}`. `application.yml`에 등록한 제공자만 `true`라서, 화면이 카카오·네이버를 "준비 중"으로 보여 준다.
- 로그인 상태 유지: `TokenBasedRememberMeServices`. 체크하고 로그인하면 `ACCOUNTS_REMEMBER_ME` 쿠키(14일)를 주고, 세션이 끝나도 이 쿠키로 다시 로그인된다.

> **remember-me 쿠키란?**
> 아이디, 만료 시각, 그리고 이 둘과 비밀번호 해시·서버 비밀 키로 만든 서명을 담은 쿠키다. 서버는 서명을 다시 계산해 맞으면 로그인시킨다. 비밀번호를 바꾸면 서명이 달라져 기존 쿠키는 무효가 된다.

### API는 로그인 후 돌아갈 곳으로 기억하지 않는다
로그인하지 않은 요청이 막히면 Spring Security는 그 요청을 세션에 저장해 두었다가(`RequestCache`), 로그인 성공 뒤 그 주소로 보낸다. 이 덕분에 `/oauth2/authorize` → 로그인 → 다시 `/oauth2/authorize`가 된다.

문제는 내 계정 화면이 로그인 여부를 알려고 부른 `/api/me`도 저장된다는 것이었다. 그 뒤 로그인하면 `/api/me`의 JSON 화면으로 이동했다. `HttpSessionRequestCache`에 "`/api/**`가 아닌 요청만 저장"하는 조건(`NegatedRequestMatcher`)을 걸어 해결했다(트러블슈팅 13).

## 10. 1-7 (2): Next.js 화면

### 구성
```
accounts/frontend/
├── next.config.ts        rewrites (Spring으로 넘길 경로), / → /account
├── app/
│   ├── layout.tsx        글꼴, 테마(라이트·다크) 초기화
│   ├── globals.css       프로토타입의 색·크기
│   ├── login/            로그인 (+ 오류·로그아웃 안내)
│   ├── signup/           회원가입, signup/verify 이메일 인증
│   ├── find-id/          아이디 찾기 (3단계)
│   ├── reset-password/   비밀번호 찾기 (4단계)
│   └── account/          내 계정
├── components/           AuthShell(화면 틀), 비밀번호 칸, 인증번호 칸, 단계 막대 등
└── lib/                  api.ts (API 호출, CSRF), validation.ts (입력 규칙), useCountdown.ts
```

### 프로토타입을 그대로 옮긴 방법
프로토타입은 화면마다 PC·모바일 × 라이트·다크 4장이다. 4장을 비교하면 구조는 같고 다음만 다르다.

| 차이 | 구현 |
|---|---|
| 색 | CSS 변수(`--bg`, `--text`, `--line` 등)를 `[data-theme="dark"]`에서 바꿔 끼운다 |
| PC: 왼쪽 소개 영역 + 오른쪽 본문 / 모바일: 위쪽 헤더(작은 로고) | 화면 폭 1023px 이하에서 소개 영역을 숨기고 헤더 로고를 보인다 |
| 제목 크기 32px / 26px | 같은 미디어 쿼리 |

> **CSS 변수란?**
> `--text: #23343b`처럼 이름을 붙여 둔 값이다. `color: var(--text)`로 쓰고, 상위 요소에서 값을 바꾸면 그 아래 전부가 바뀐다.

> **미디어 쿼리란?**
> `@media (max-width: 1023px) { ... }`처럼 화면 폭 같은 조건이 맞을 때만 적용되는 CSS다.

입력값 검사 문구(`아이디를 입력해 주세요.` 등), 비밀번호 강도 막대, 인증번호 타이머, 단계 막대는 프로토타입의 스크립트에 있던 규칙을 그대로 옮겼다. 서버가 같은 규칙으로 다시 검사하고, 서버가 돌려준 필드별 오류(`errors.uid` 등)는 해당 칸 아래에 보여 준다.

프로토타입에 없던 화면은 **프로토타입에 먼저 추가한 뒤** 구현했다.
- 로그인 · 오류 안내: 아이디·비밀번호 실패, 같은 이메일로 가입된 계정, 다른 회원에 연결된 소셜 계정 등을 위쪽 빨간 상자로 보여 준다.
- 내 계정: 계정 정보, 로그인 방식별 연결·해제, 로그아웃.

### 테마
`<html data-theme="light|dark">`로 정한다. 저장된 선택이 없으면 운영체제 설정을 따른다. 화면이 그려지기 전에 정해야 깜빡이지 않으므로, `layout.tsx`의 `<head>`에 작은 스크립트를 넣어 가장 먼저 실행한다. 버튼은 이 속성을 바꾸고 `localStorage`에 저장한다.

### 로그인과 로그아웃은 폼 전송
`fetch`로 로그인하면 Spring이 보내는 리다이렉트(원래 요청한 `/oauth2/authorize`로 돌아가기)를 브라우저 주소에 반영할 수 없다. 그래서 입력값 검사를 마친 뒤 `<form method="post" action="/login/password">`를 만들어 보낸다(`lib/api.ts`의 `submitForm`). `_csrf`는 `/api/csrf`에서 받아 넣는다.

로그인 실패로 `/login?error=bad_credentials`에 돌아오면 아이디 칸을 다시 채운다. 보내기 직전에 아이디를 `sessionStorage`에 잠깐 저장해 두기 때문이다(비밀번호는 저장하지 않는다).

### 화면별 흐름
| 화면 | 흐름 |
|---|---|
| 로그인 | 소셜 버튼: 사용 가능하면 `/oauth2/authorization/{google}`로 이동, 아니면 "준비 중인 로그인 방식이에요." |
| 회원가입 | 중복확인(`uid-availability`) → 가입 신청 → 이메일과 만료 시각을 `sessionStorage`에 두고 `/signup/verify`로 |
| 이메일 인증 | 남은 시간 표시, 재전송, 완료 화면 |
| 아이디·비밀번호 찾기 | 한 화면 안에서 단계(`step`)를 바꾼다 |
| 내 계정 | `/api/me`가 401이면 `/login`으로. 연결하기 → 소셜 로그인 화면 → 돌아오면 "Google 계정을 연결했어요." |

내 계정에서 소셜 연결이 실패하면 Spring은 `/login?social_error=...`로 보낸다. 로그인 화면은 이미 로그인한 상태(`/api/me` 200)이면 `/account?error=...`로 넘겨, 내 계정 화면에서 오류를 보여 준다.

## 11. 1-7 이후의 구조
```
브라우저 ── localhost:3001 (Next.js, accounts/frontend)
              │ 화면: /login /signup /signup/verify /find-id /reset-password /account
              │ rewrites ↓ (/api/**, /login/password, /oauth2/**, /login/oauth2/**, /logout,
              │             /.well-known/**, /userinfo, /connect/**)
            localhost:9000 (Spring, accounts/backend)
              ├ 체인 ① AuthorizationServerConfig   OIDC (issuer http://localhost:3001)
              │    · 등록된 앱: blog (client_secret_basic, PKCE 필수)
              │    · 토큰: sub = 회원 id, nickname·preferred_username·email
              │    · /connect/logout: 세션 + 로그인 상태 유지 쿠키 삭제
              └ 체인 ② SecurityConfig               그 외
                   · 폼 로그인 POST /login/password (+ 로그인 상태 유지)
                   · Google 로그인 / 연결 (user_identities)
                   · API: 가입(이메일 인증), 계정 찾기, 내 계정
                   · 세션 ACCOUNTS_SESSION, CSRF XSRF-TOKEN 쿠키
```

## 12. 아직 남은 것
| 남은 것 | 단계 |
|---|---|
| blog를 accounts의 OIDC 앱(클라이언트)으로 바꾸기 | 2단계 |
| 카카오·네이버 등록 | 이후 |
| 서명 키 저장, 앱 등록 정보·발급 토큰·회원을 영구 DB에 저장 | 배포 전 |
| 약관 내용, 왼쪽 소개 문구 | 배포 전 |
