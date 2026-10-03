---
title: accounts 인가 서버 구현 과정 (1-1 ~ 1-5)
date: 2026-10-03
tags: [oidc, authorization-server, spring-security, implementation]
---

# accounts 인가 서버 구현 과정 (1-1 ~ 1-5)

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

## 8. 1-5 이후의 구조
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
      · Google 로그인             → OAuth2UserCustomService.resolveUser()
                                     로그인 상태면 linkSocial(), 아니면 loginWithSocial()  (user_identities)
      · 회원가입 POST /api/signup → UserService.signup()
      · 세션 (ACCOUNTS_SESSION), CSRF
```

## 9. 아직 남은 것
| 남은 것 | 단계 |
|---|---|
| ID 토큰의 `sub`를 회원 id로 통일, `nickname`·`email` 클레임, 전체 로그아웃 | 1-6 |
| 화면을 Next.js로 교체 | 1-7 |
| 서명 키 저장, 앱 등록 정보와 발급 토큰을 DB에 저장 | 배포 전 |
