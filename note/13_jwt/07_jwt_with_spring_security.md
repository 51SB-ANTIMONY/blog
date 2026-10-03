---
title: JWT와 Spring Security의 연결, 그리고 프로젝트 코드
date: 2026-10-03
tags: [jwt, spring-security, filter, project]
---

# JWT와 Spring Security의 연결, 그리고 프로젝트 코드

> Spring Security의 필터 구조는 [필터 기반 동작 원리](../12_spring_security/03_filter_architecture.md), 구성 요소는 [Spring Security란 무엇인가](../12_spring_security/02_spring_security.md)에서 설명했다.
> 이 노트의 프로젝트 코드는 Spring Boot 4.0.1, Spring Security 7.0.2, jjwt 0.9.1 기준이다.

## 1. 연결 지점: "인증 정보를 어디서 꺼내 오는가"
Spring Security에서 인증된 요청이란 **`SecurityContextHolder`에 인증된 `Authentication`이 들어 있는 요청**이다. 그 `Authentication`을 어디서 가져오느냐만 다르다.

| 방식 | `Authentication`을 넣는 필터 | 가져오는 곳 |
|---|---|---|
| 폼 로그인 + 세션 | `SecurityContextHolderFilter` | HTTP 세션의 `SPRING_SECURITY_CONTEXT` |
| JWT | **직접 만든 토큰 필터** (또는 `BearerTokenAuthenticationFilter`) | 요청의 `Authorization: Bearer` 헤더 |

토큰 필터가 `SecurityContextHolder`에 `Authentication`을 넣어 두기만 하면, **그 뒤의 필터들은 원래대로 동작한다.**
- `AnonymousAuthenticationFilter`: 이미 인증 정보가 있으므로 아무것도 하지 않는다.
- `AuthorizationFilter`: `authenticated()`, `hasRole()` 같은 접근 규칙을 그대로 적용한다.
- 컨트롤러: `SecurityContextHolder`나 `@AuthenticationPrincipal`로 사용자 정보를 꺼낸다.

즉, JWT를 붙인다는 것은 **"인증 정보를 세션 대신 토큰에서 만들어 내는 필터 하나를 필터 체인에 끼워 넣는 것"**이다.

## 2. 토큰 필터가 들어가는 위치
```
SecurityFilterChain
 ├ DisableEncodeUrlFilter
 ├ WebAsyncManagerIntegrationFilter
 ├ SecurityContextHolderFilter         (요청 끝에 SecurityContextHolder를 비움)
 ├ HeaderWriterFilter
 ├ LogoutFilter
 ├ ★ TokenAuthenticationFilter          ← 토큰 검증 → SecurityContextHolder에 Authentication 저장
 ├ UsernamePasswordAuthenticationFilter
 ├ RequestCacheAwareFilter
 ├ SecurityContextHolderAwareRequestFilter
 ├ AnonymousAuthenticationFilter        (이미 인증됨 → 통과)
 ├ ExceptionTranslationFilter
 └ AuthorizationFilter                  (접근 규칙 검사)
```
```java
http.addFilterBefore(new TokenAuthenticationFilter(tokenProvider),
                     UsernamePasswordAuthenticationFilter.class);
```
`addFilterBefore(A, B)`는 "필터 A를 필터 B 바로 앞에 넣으라"는 뜻이다. `AnonymousAuthenticationFilter`와 `AuthorizationFilter`보다 앞이기만 하면 동작하지만, 다른 인증 필터들과 같은 자리라는 의미로 `UsernamePasswordAuthenticationFilter` 앞에 두는 것이 관례다.

## 3. 토큰 방식에 맞게 바꿔야 하는 설정
세션 방식의 기본 설정을 그대로 두면 토큰 방식과 맞지 않는 부분이 있다.

### (1) 세션을 만들지 않기
```java
http.sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```
| 정책 | 의미 |
|---|---|
| `IF_REQUIRED` (기본) | 필요하면 세션을 만든다. 로그인 성공 시 세션에 인증 정보 저장 |
| `STATELESS` | Spring Security가 세션을 만들지도, 세션에서 인증 정보를 읽지도 않는다 |

`STATELESS`면 매 요청의 인증 정보는 오직 토큰 필터가 만든 것뿐이다. 요청이 끝나면 `SecurityContextHolderFilter`가 비우고, 다음 요청에서 토큰 필터가 다시 만든다. 이것이 Spring Security에서 무상태성이 드러나는 모습이다.

### (2) CSRF 끄기
CSRF 공격은 **브라우저가 쿠키를 자동으로 붙이는 것**을 악용한다. `Authorization` 헤더는 JavaScript가 직접 넣어야 하고, 다른 사이트의 페이지는 이 헤더를 넣을 수 없다. 그래서 헤더로만 토큰을 보내는 API는 CSRF 보호가 필요 없다.
```java
http.csrf(AbstractHttpConfigurer::disable);
```
단, **리프레시 토큰을 쿠키로 보낸다면** 재발급 API는 쿠키 자동 전송의 대상이 되므로 `SameSite=Strict` 같은 대비가 필요하다.

### (3) 인증 실패 시 302 대신 401
폼 로그인의 기본 동작은 로그인 페이지로 `302` 리다이렉트다. API 클라이언트에게는 `401`이 필요하다.
```java
http.exceptionHandling(e -> e
        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
```
[필터 기반 동작 원리](../12_spring_security/03_filter_architecture.md)의 `ExceptionTranslationFilter`가 익명 사용자의 접근 거부를 `AuthenticationEntryPoint`로 넘기는데, 이 Entry Point를 401을 응답하는 것으로 바꾸는 것이다.

### (4) 폼 로그인, HTTP Basic 끄기 (토큰만 쓸 때)
```java
http.formLogin(AbstractHttpConfigurer::disable)
    .httpBasic(AbstractHttpConfigurer::disable);
```
토큰 발급은 직접 만든 로그인 API(`POST /api/login` 등)나 OAuth2 로그인이 맡는다.

## 4. 토큰 필터의 일반적인 모양
```java
public class TokenAuthenticationFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1. Authorization 헤더에서 "Bearer " 뒤의 토큰 꺼내기
        // 2. 토큰 검증 (서명, 만료)
        // 3. 검증되면 토큰의 클레임으로 Authentication 만들기
        // 4. SecurityContextHolder에 넣기
        // 5. 검증 여부와 상관없이 다음 필터로
        chain.doFilter(request, response);
    }
}
```
5번이 중요하다. 토큰이 없거나 틀려도 **여기서 막지 않는다.** 판단은 뒤의 `AuthorizationFilter`가 한다. 그래야 `permitAll()`인 경로(로그인, 회원가입, 재발급 API)는 토큰 없이도 지나갈 수 있다.

> **`OncePerRequestFilter`란?**
> 요청 하나당 정확히 한 번만 실행되도록 보장하는 Spring의 필터 부모 클래스다. 서버 안에서 요청이 다른 경로로 넘겨질(forward) 때 필터가 다시 실행되는 것을 막는다.

### `Authentication`을 무엇으로 만드는가
폼 로그인에서는 `DaoAuthenticationProvider`가 비밀번호를 확인한 뒤 인증된 `Authentication`을 만들었다. 토큰 방식에서는 **서명 검증이 곧 인증**이므로, 필터가 직접 인증된 `Authentication`을 만든다.
```java
new UsernamePasswordAuthenticationToken(principal, credentials, authorities)
//  인자 3개 생성자 → authenticated = true
```
| 인자 | 토큰 방식에서 넣는 값 |
|---|---|
| `principal` | 토큰의 `sub` 등으로 만든 사용자 객체 |
| `credentials` | 토큰 문자열 (또는 `null`) |
| `authorities` | 토큰의 권한 클레임 또는 고정 권한 |

DB를 조회하지 않고 토큰 정보만으로 만드는 것이 무상태 방식이다. 최신 회원 정보가 필요하면 `sub`로 DB를 조회할 수도 있지만, 그만큼 요청마다 DB 접근이 생긴다.

## 5. 참고: Spring Security가 제공하는 JWT 지원
직접 필터를 만드는 대신 Spring Security의 **OAuth2 Resource Server** 기능을 쓸 수도 있다.
```gradle
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
```
```java
http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
```
| 직접 구현 | Spring Security 제공 |
|---|---|
| `TokenAuthenticationFilter` | `BearerTokenAuthenticationFilter` |
| `TokenProvider.validToken()` | `JwtDecoder` (`NimbusJwtDecoder`) |
| `TokenProvider.getAuthentication()` | `JwtAuthenticationProvider` + `JwtAuthenticationConverter` |
| `UsernamePasswordAuthenticationToken` | `JwtAuthenticationToken` |
| (직접 설정) 401 응답 | `BearerTokenAuthenticationEntryPoint`가 RFC 6750 형식의 `WWW-Authenticate` 헤더까지 응답 |

알고리즘 고정, 시계 오차, `iss`/`aud` 검증 같은 처리가 이미 들어 있다. 이 프로젝트는 원리를 익히기 위해 직접 구현하는 방식을 택했다.

## 6. 프로젝트 코드에서의 모습

### 구성 요소 지도
```
application.yml (jwt.issuer, jwt.secret_key)
  ▼
JwtProperties ─────────────▶ TokenProvider ◀──────────────────┐
                              │ generateToken()  토큰 생성       │
                              │ validToken()     서명/만료 검증  │
                              │ getAuthentication() → Authentication
                              │ getUserId()      id 클레임       │
                              ▼                                  │
                    TokenAuthenticationFilter             TokenService
                    (요청마다 액세스 토큰 검증)            (리프레시 → 새 액세스 토큰)
                                                                 ▲
                                                 RefreshTokenService, UserService
                                                                 ▲
                                                         TokenApiController
                                                         POST /api/token
```

### 설정값: `application.yml`, `JwtProperties`
```yaml
jwt:
  issuer: issue@antimony.kr # temp
  secret_key: secret_key # temp
```
```java
@Setter @Getter
@Component
@ConfigurationProperties("jwt")
public class JwtProperties {
    private String issuer;      // → iss 클레임
    private String secretKey;   // → HS256 비밀 키
}
```
`@ConfigurationProperties("jwt")`는 `jwt.` 아래의 값을 같은 이름의 필드에 setter로 넣는다. `secret_key`와 `secretKey`는 Spring Boot가 같은 이름으로 취급한다.

### 토큰 생성: `TokenProvider.makeToken()`
```java
Jwts.builder()
        .setHeaderParam(Header.TYPE, Header.JWT_TYPE)   // 헤더 "typ":"JWT"
        .setIssuer(jwtProperties.getIssuer())           // "iss"
        .setIssuedAt(now)                               // "iat" (초 단위로 변환됨)
        .setExpiration(expiry)                          // "exp"
        .setSubject(user.getEmail())                    // "sub"
        .claim("id", user.getId())                      // 비공개 클레임 "id"
        .signWith(SignatureAlgorithm.HS256, jwtProperties.getSecretKey())  // 헤더 "alg":"HS256" + 서명
        .compact();                                     // Base64URL 인코딩 후 "." 으로 연결
```
[JWT의 구조](./04_jwt_structure.md) 7장의 1~8단계가 이 한 문장에서 일어난다. 결과 토큰의 헤더는 `{"typ":"JWT","alg":"HS256"}`, 페이로드는 `{"iss":...,"iat":...,"exp":...,"sub":...,"id":...}` 순서가 된다.

**jjwt 0.9.1의 비밀 키 해석:** `signWith(SignatureAlgorithm, String)`과 `setSigningKey(String)`은 문자열을 **Base64로 인코딩된 키**로 보고 디코딩한 바이트를 키로 쓴다. 지금 값 `secret_key`(10글자)를 디코딩하면 7바이트 이하가 되어, HS256 권장 길이 32바이트에 크게 못 미친다. 실제로 쓸 때는 `openssl rand -base64 32`로 만든 값을 넣고, 설정 파일을 Git에 올리지 않거나 환경 변수로 주입해야 한다.

### 토큰 검증: `TokenProvider.validToken()`
```java
try {
    Jwts.parser().setSigningKey(jwtProperties.getSecretKey()).parseClaimsJws(token);
    return true;
} catch (Exception e) {
    return false;
}
```
[JWT의 서명과 검증](./05_jwt_signature_and_validation.md) 4장의 1~7단계 중 형식, 서명, `exp` 검사를 `parseClaimsJws()`가 한다. `iss` 검사는 하지 않는다(필요하면 `.requireIssuer(...)`를 추가). 모든 예외를 `false`로 바꾸므로 실패 원인(만료, 위조)은 구분되지 않는다.

### 인증 정보 생성: `TokenProvider.getAuthentication()`
```java
Set<SimpleGrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"));
return new UsernamePasswordAuthenticationToken(
        new org.springframework.security.core.userdetails.User(claims.getSubject(), "", authorities),
        token,
        authorities);
```
- `principal`: Spring Security가 제공하는 `User`(이메일, 빈 비밀번호, 권한). DB를 조회하지 않는다.
- `credentials`: 토큰 문자열
- `authorities`: 모든 사용자에게 고정으로 `ROLE_USER`
- 인자 3개 생성자이므로 **인증 완료** 상태

### 요청마다 검증: `TokenAuthenticationFilter`
```java
String authorizationHeader = request.getHeader("Authorization");   // "Bearer eyJ..."
String token = getAccessToken(authorizationHeader);                // "eyJ..." 또는 null

if (tokenProvider.validToken(token)) {
    Authentication authentication = tokenProvider.getAuthentication(token);
    SecurityContextHolder.getContext().setAuthentication(authentication);
}
filterChain.doFilter(request, response);
```
4장의 일반적인 모양과 같다. 토큰이 없거나 틀려도 다음 필터로 넘긴다.

### 재발급: `TokenApiController` → `TokenService`
```java
@PostMapping("/api/token")
public ResponseEntity<CreateAccessTokenResponse> createNewAccessToken(@RequestBody CreateAccessTokenRequest request) {
    String newAccessToken = tokenService.createNewAccessToken(request.getRefreshToken());
    return ResponseEntity.status(HttpStatus.CREATED).body(new CreateAccessTokenResponse(newAccessToken));
}
```
```java
public String createNewAccessToken(String refreshToken) {
    if (!tokenProvider.validToken(refreshToken)) {                       // ⑤-1 서명, 만료
        throw new IllegalArgumentException("Unexpected token");
    }
    Long userId = refreshTokenService.findByRefreshToken(refreshToken)   // ⑤-2 저장소에 있는가
            .getUserId();
    User user = userService.findById(userId);                            // ⑤-3 회원 조회
    return tokenProvider.generateToken(user, Duration.ofHours(2));       // ⑤-4 새 액세스 토큰
}
```
[리프레시 토큰](./06_refresh_token.md) 4장의 ⑤ 단계와 그대로 대응한다. 교체(Rotation)는 하지 않는다.

```http
POST /api/token HTTP/1.1
Content-Type: application/json

{"refreshToken":"eyJ0eXAi..."}
```
```http
HTTP/1.1 201 
Content-Type: application/json

{"accessToken":"eyJ0eXAi..."}
```

### 리프레시 토큰 저장: `RefreshToken`
```java
@Column(name = "user_id", nullable = false, unique = true)
private Long userId;
@Column(name = "refresh_token", nullable = false)
private String refreshToken;

public RefreshToken update(String newRefreshToken) { ... }
```
회원당 하나, 원문 JWT를 그대로 저장한다. `update()`는 다시 로그인했을 때 토큰을 교체하는 용도다.

## 7. 현재 프로젝트에서 아직 연결되지 않은 부분
지금 코드는 부품이 준비된 단계이고, 다음 연결이 남아 있다.

| 남은 연결 | 현재 상태 | 영향 |
|---|---|---|
| 토큰 필터 등록 | `WebSecurityConfig`에 `addFilterBefore(...)`가 없고 `@Component`도 아님 | `Authorization: Bearer` 헤더를 아무도 읽지 않는다. API 인증은 여전히 세션으로만 된다. |
| 토큰 발급 | `RefreshToken`을 만드는 코드가 테스트에만 있음 | 로그인해도 토큰을 받을 수 없다 |
| 재발급 API 접근 | `/api/token`이 `permitAll()` 목록에 없음 | 로그인 세션 없이 호출하면 `302 /login` |
| 액세스/리프레시 구분 | 같은 키, 같은 형식 | 리프레시 토큰으로도 API 인증이 된다(필터를 등록한 뒤) |

`TokenApiControllerTest`는 `@BeforeEach`에서 `MockMvcBuilders.webAppContextSetup(context).build()`로 MockMvc를 새로 만든다. 이렇게 만든 MockMvc에는 Spring Security 필터가 없으므로(`.apply(springSecurity())`를 하지 않았으므로), 위 "재발급 API 접근" 문제는 테스트에서 드러나지 않는다.

연결할 때의 설정은 대략 다음과 같은 모양이 된다(참고용, 아직 적용하지 않음).
```java
http
    .addFilterBefore(new TokenAuthenticationFilter(tokenProvider),
                     UsernamePasswordAuthenticationFilter.class)
    .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/signup", "/user", "/api/token").permitAll()
            .anyRequest().authenticated());
```
폼 로그인과 세션을 유지한 채 토큰 필터를 추가하면 두 방식이 함께 동작한다. 세션이 있으면 `SecurityContextHolderFilter`가, 토큰이 있으면 `TokenAuthenticationFilter`가 인증 정보를 넣는다. 토큰만 쓰려면 3장의 설정(`STATELESS`, 401 Entry Point, 폼 로그인 끄기)을 함께 적용한다.

## 정리
| 항목 | 핵심 |
|---|---|
| 연결 원리 | 토큰에서 `Authentication`을 만들어 `SecurityContextHolder`에 넣는 필터를 끼워 넣는다 |
| 위치 | `addFilterBefore(토큰 필터, UsernamePasswordAuthenticationFilter.class)` |
| 함께 바꿀 설정 | `STATELESS`, CSRF 끄기, 401 Entry Point, 폼 로그인 끄기 |
| 인증 판단 | 토큰 필터는 넣기만 하고, 허용/거부는 `AuthorizationFilter`가 한다 |
| 프로젝트 | `TokenProvider`(생성/검증) + `TokenAuthenticationFilter`(요청마다) + `TokenService`(재발급). 필터 등록과 토큰 발급은 아직 없음 |
