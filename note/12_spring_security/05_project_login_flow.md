---
title: 이 프로젝트의 회원가입, 로그인, 로그아웃 흐름
date: 2026-10-03
tags: [spring-security, project, form-login]
---

# 이 프로젝트의 회원가입, 로그인, 로그아웃 흐름

> 일반적인 흐름은 [회원가입, 로그인, 로그아웃 시 필터의 흐름](./04_signup_login_logout_flow.md)에서 설명했다.
> 이 노트는 블로그 프로젝트(Spring Boot 4.0.1, Spring Security 7.0.2)에 실제로 작성한 코드가 그 흐름의 어디에 들어가는지 정리한다.
> 일반적인 흐름과 가장 크게 다른 점은 **CSRF를 껐다는 것**이다.

## 1. 추가한 파일
| 분류 | 파일 | 역할 |
|---|---|---|
| 의존성 | `build.gradle` | `spring-boot-starter-security`, `thymeleaf-extras-springsecurity6`, `spring-security-test` 추가 |
| 설정 | `config/WebSecurityConfig.java` | 접근 규칙, 폼 로그인, 로그아웃, CSRF, `PasswordEncoder` |
| 엔티티 | `domain/User.java` | 회원. `UserDetails` 구현 |
| 리포지토리 | `repository/UserRepository.java` | `findByEmail()` |
| 서비스 | `service/UserService.java` | 회원가입: 비밀번호 해시 후 저장 |
| 서비스 | `service/UserDetailService.java` | 로그인: 이메일로 회원 조회. `UserDetailsService` 구현 |
| DTO | `dto/AddUserRequest.java` | 회원가입 폼 데이터 (`email`, `password`) |
| 컨트롤러 | `controller/UserViewController.java` | `GET /login`, `GET /signup` 화면 |
| 컨트롤러 | `controller/UserApiController.java` | `POST /user` 회원가입, `GET /logout` |
| 뷰 | `templates/login.html`, `templates/signup.html` | 로그인, 회원가입 화면 |
| 뷰 | `templates/articleList.html` | 로그아웃 버튼 추가 |

추가한 의존성의 역할은 다음과 같다.

| 의존성 | 역할 |
|---|---|
| `spring-boot-starter-security` | Spring Security 본체 |
| `thymeleaf-extras-springsecurity6` | 템플릿에서 `sec:authorize` 같은 속성으로 로그인 정보를 쓸 수 있게 한다. 아직 템플릿에서 쓰지는 않는다. |
| `spring-security-test` | 테스트에서 `@WithMockUser`처럼 로그인한 사용자를 흉내 낼 수 있게 한다. |

## 2. 구성 요소가 Spring Security의 어디에 들어가나
[Spring Security란 무엇인가](./02_spring_security.md)의 구성 요소 그림에 이 프로젝트의 클래스를 대입하면 다음과 같다.

```
UsernamePasswordAuthenticationFilter            (Spring Security 제공)
  ▼
ProviderManager                                 (Spring Security 제공)
  ▼
DaoAuthenticationProvider                       (Spring Security가 자동 생성)
  ├─ UserDetailsService  = UserDetailService    ← 직접 작성
  │     └─ UserRepository.findByEmail()          ← 직접 작성
  │           └─ User implements UserDetails     ← 직접 작성
  └─ PasswordEncoder     = BCryptPasswordEncoder ← WebSecurityConfig에서 빈 등록
```
`UserDetailService`(`@Service`)와 `PasswordEncoder`(`@Bean`)가 빈으로 하나씩 있으므로, Spring Security가 이 둘을 쓰는 `DaoAuthenticationProvider`를 자동으로 만든다. 그리고 `UserDetailsService` 빈이 있으므로 Spring Boot는 임시 사용자(`user` / 콘솔 출력 비밀번호)를 만들지 않는다.

## 3. `WebSecurityConfig` 읽기
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    @Bean
    public WebSecurityCustomizer configure() {
        return (web) -> web.ignoring()
                .requestMatchers(toH2Console())
                .requestMatchers("/static/**");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/signup", "/user").permitAll()
                        .anyRequest().authenticated())
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .defaultSuccessUrl("/articles"))
                .logout(logout -> logout
                        .logoutSuccessUrl("/login")
                        .invalidateHttpSession(true))
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

> **`@EnableWebSecurity`란?**
> Spring Security의 웹 보안 설정을 켜는 어노테이션이다. `HttpSecurity` 빈을 쓸 수 있게 하고, `FilterChainProxy`를 만든다. Spring Boot에서는 자동 구성이 대신 붙여 주므로 생략해도 동작하지만, 설정 클래스임을 드러내기 위해 붙여 두는 경우가 많다.

### 설정이 만드는 `SecurityFilterChain`
`FilterChainProxy` 안에는 체인 세 개가 순서대로 들어간다.

| 순서 | 요청 매처 | 필터 | 만든 곳 |
|---|---|---|---|
| 1 | `/h2-console/**` | 0개 | `web.ignoring().requestMatchers(toH2Console())` |
| 2 | `/static/**` | 0개 | `web.ignoring().requestMatchers("/static/**")` |
| 3 | 그 외 모든 요청 | 11개 (아래) | `filterChain(HttpSecurity)` |

### 3번 체인의 필터 목록
[필터 기반 동작 원리](./03_filter_architecture.md)의 일반적인 목록과 비교하면 다음과 같다.

| 순서 | 필터 | 이 프로젝트 | 이유 |
|---|---|---|---|
| 1 | `DisableEncodeUrlFilter` | 있음 | |
| 2 | `WebAsyncManagerIntegrationFilter` | 있음 | |
| 3 | `SecurityContextHolderFilter` | 있음 | |
| 4 | `HeaderWriterFilter` | 있음 | |
| - | `CsrfFilter` | **없음** | `.csrf(AbstractHttpConfigurer::disable)` |
| 5 | `LogoutFilter` | 있음 | `.logout(...)` |
| 6 | `UsernamePasswordAuthenticationFilter` | 있음 | `.formLogin(...)` |
| - | `DefaultLoginPageGeneratingFilter` | **없음** | `.loginPage("/login")`로 직접 만든 로그인 페이지를 쓰기 때문 |
| - | `DefaultLogoutPageGeneratingFilter` | **없음** | 위와 같은 이유 |
| 7 | `RequestCacheAwareFilter` | 있음 | |
| 8 | `SecurityContextHolderAwareRequestFilter` | 있음 | |
| 9 | `AnonymousAuthenticationFilter` | 있음 | |
| 10 | `ExceptionTranslationFilter` | 있음 | |
| 11 | `AuthorizationFilter` | 있음 | `.authorizeHttpRequests(...)` |

### 접근 규칙
| 경로 | 규칙 | 해당 요청 |
|---|---|---|
| `/login` | `permitAll()` | 로그인 화면(GET), 로그인 처리(POST), 실패 후 `/login?error` |
| `/signup` | `permitAll()` | 회원가입 화면 |
| `/user` | `permitAll()` | 회원가입 처리 |
| 그 외 | `authenticated()` | `/articles`, `/new-article`, `/api/articles` 등 |

`/login?error`처럼 쿼리 파라미터가 붙어도 경로 부분(`/login`)만 비교하므로 `permitAll()`이 적용된다.

### CSRF를 끈 결과
1. `CsrfFilter`가 없으므로 `POST /user`, `POST /login`, `article.js`의 `fetch`(POST, PUT, DELETE)에 토큰이 필요 없다.
2. 로그아웃을 `GET /logout`으로도 할 수 있게 된다([필터 기반 동작 원리](./03_filter_architecture.md)의 `LogoutFilter` 참고). 그래서 글 목록의 로그아웃 버튼이 링크 이동(`location.href='/logout'`)으로 동작한다.
3. 화면을 열 때 CSRF 토큰을 만들지 않으므로, 로그인 전에는 세션이 생기지 않는다.
4. `login.html`의 `th:name="${_csrf?.parameterName}"` hidden input은 `_csrf`가 `null`이라 이름과 값 없이 렌더링되고, 아무 값도 전송되지 않는다.

## 4. 회원가입 흐름

### ① 회원가입 화면: `GET /signup`
```
GET /signup
  ▼
[보안 필터 11개]
  · SecurityContextHolderFilter : 세션 없음 → 빈 SecurityContext
  · AnonymousAuthenticationFilter: 익명 사용자
  · AuthorizationFilter          : /signup → permitAll → 허용
  ▼
DispatcherServlet
  ▼
UserViewController.signup() → "signup"
  ▼
ThymeleafViewResolver → templates/signup.html
  ▼
HTTP/1.1 200  (HTML, Set-Cookie 없음)
```

### ② 회원가입 제출: `POST /user`
```http
POST /user HTTP/1.1
Content-Type: application/x-www-form-urlencoded

email=a%40a.com&password=1234
```
```
[보안 필터 11개]
  · CsrfFilter 없음 → 토큰 검사 없음
  · AuthorizationFilter: /user → permitAll → 허용
  ▼
UserApiController.signup(AddUserRequest request)
  · 폼의 email, password가 AddUserRequest의 setter로 들어감
    (그래서 AddUserRequest에 @Setter가 있다)
  ▼
UserService.save(request)
  · passwordEncoder.encode("1234") → "$2a$10$..."
  · userRepository.save(User.builder().email(...).password(해시).build())
    → INSERT INTO users (email, password) VALUES (?, ?)
  ▼
return "redirect:/login"
```
```http
HTTP/1.1 302
Location: /login
```

`User` 엔티티의 테이블 이름이 `users`인 이유: `user`는 H2에서 예약어라 테이블 이름으로 쓸 수 없다.

## 5. 로그인 흐름

### ① 로그인 화면: `GET /login`
```
[보안 필터 11개]
  · UsernamePasswordAuthenticationFilter: GET이므로 통과
  · AuthorizationFilter: /login → permitAll → 허용
  ▼
UserViewController.login() → "login" → templates/login.html
  ▼
HTTP/1.1 200
```

### ② 로그인 제출: `POST /login`
`login.html`의 폼은 이렇게 생겼다.
```html
<form action="/login" method="POST">
    <input type="email" name="username">
    <input type="password" name="password">
</form>
```
입력칸 이름이 `email`이 아니라 `username`인 이유: `UsernamePasswordAuthenticationFilter`가 기본으로 `username`, `password`라는 이름의 파라미터를 읽기 때문이다.

```http
POST /login HTTP/1.1
Content-Type: application/x-www-form-urlencoded

username=a%40a.com&password=1234
```
```
[보안 필터]
  · SecurityContextHolderFilter : 빈 SecurityContext
  · LogoutFilter                : 통과
  · UsernamePasswordAuthenticationFilter: POST /login → 처리 시작 ★
      │
      ├ UsernamePasswordAuthenticationToken.unauthenticated("a@a.com", "1234")
      ├ ProviderManager → DaoAuthenticationProvider
      │   ├ UserDetailService.loadUserByUsername("a@a.com")
      │   │   └ UserRepository.findByEmail("a@a.com")
      │   │       → SELECT ... FROM users WHERE email = ?
      │   │   ← User
      │   ├ User.isAccountNonLocked() / isEnabled() / isAccountNonExpired() → 모두 true
      │   ├ BCryptPasswordEncoder.matches("1234", user.getPassword()) → true
      │   ├ User.isCredentialsNonExpired() → true
      │   └ 인증 후 Authentication
      │       principal  : User(a@a.com)
      │       authorities: [user]       ← User.getAuthorities()
      │
      ├ 세션 저장: 아직 세션이 없으므로 새 세션 생성 → SPRING_SECURITY_CONTEXT 저장
      └ 성공 처리
          · 저장된 요청 없음 → defaultSuccessUrl("/articles")
  ▼
체인 종료 (컨트롤러 없음)
```
```http
HTTP/1.1 302
Set-Cookie: JSESSIONID=BBBB2222; Path=/; HttpOnly
Location: /articles
```
이 프로젝트는 로그인 전에는 세션을 만들지 않으므로, 여기서 처음으로 세션 쿠키가 내려온다.

**로그인 실패 시**
| 상황 | 내부에서 일어나는 일 | 결과 |
|---|---|---|
| 비밀번호 틀림 | `matches()` → `false` → `BadCredentialsException` | `302 /login?error` |
| 없는 이메일 | `UserDetailService`가 `IllegalArgumentException`을 던짐 → `DaoAuthenticationProvider`가 `InternalAuthenticationServiceException`으로 감쌈 | `302 /login?error` |

두 경우 모두 사용자에게 보이는 결과는 같다. 다만 Spring Security 규칙은 사용자를 찾지 못하면 `UsernameNotFoundException`을 던지는 것이다. 이 예외를 던져야 Spring Security가 "아이디 없음"을 "비밀번호 틀림"과 구분되지 않게 처리하는 기능이 의도대로 동작한다.

### ③ 로그인 후 이동: `GET /articles`
```http
GET /articles HTTP/1.1
Cookie: JSESSIONID=BBBB2222
```
```
[보안 필터]
  · SecurityContextHolderFilter: 세션 BBBB2222 → Authentication(a@a.com) 복원
  · AuthorizationFilter        : anyRequest().authenticated() → 허용
  ▼
BlogViewController.getArticles() → "articleList" → templates/articleList.html
  ▼
HTTP/1.1 200
```

### ④ 로그인 후 글 작성, 수정, 삭제
`article.js`의 `fetch`로 `/api/articles`를 호출할 때도 브라우저가 `JSESSIONID` 쿠키를 자동으로 붙인다. 같은 사이트로 가는 `fetch` 요청은 기본으로 쿠키를 포함하기 때문이다. 그래서 JS 코드를 바꾸지 않아도 로그인 상태로 API가 호출된다.
```
fetch PUT /api/articles/1  (Cookie: JSESSIONID=BBBB2222)
  ▼
[보안 필터] SecurityContextHolderFilter 복원 → AuthorizationFilter 허용
  ▼
BlogApiController.updateArticle()
```
로그인하지 않은 상태에서 API를 호출하면 `ExceptionTranslationFilter`가 JSON 대신 `302 /login`을 응답한다.

## 6. 로그아웃 흐름
`articleList.html`의 로그아웃 버튼:
```html
<button type="button" class="btn btn-secondary" onclick="location.href='/logout'">로그아웃</button>
```
```http
GET /logout HTTP/1.1
Cookie: JSESSIONID=BBBB2222
```
```
[보안 필터]
  · SecurityContextHolderFilter: Authentication(a@a.com) 복원
  · LogoutFilter: CSRF가 꺼져 있어 GET /logout도 로그아웃 요청으로 인식 → 처리 ★
      ├ SecurityContextLogoutHandler
      │   · 세션 BBBB2222 무효화   ← .invalidateHttpSession(true)
      │   · SecurityContextHolder 비우기
      ├ LogoutSuccessEventPublishingLogoutHandler: 로그아웃 이벤트 발행
      └ LogoutSuccessHandler → /login   ← .logoutSuccessUrl("/login")
  ▼
체인 종료
```
```http
HTTP/1.1 302
Location: /login
```

### `UserApiController.logout()`은 실행되지 않는다
```java
@GetMapping("/logout")
public String logout(HttpServletRequest request, HttpServletResponse response) {
    new SecurityContextLogoutHandler().logout(request, response,
            SecurityContextHolder.getContext().getAuthentication());
    return "redirect:/login";
}
```
`GET /logout` 요청은 `LogoutFilter`가 먼저 처리하고 체인을 끝내므로, DispatcherServlet까지 가지 않는다. 그래서 이 메서드는 호출되지 않는다. 메서드가 하려던 일(세션 무효화 후 `/login`으로 이동)은 `WebSecurityConfig`의 `logout(...)` 설정이 이미 똑같이 하고 있다.

이 메서드가 실행되는 경우는 `LogoutFilter`가 `/logout`을 처리하지 않을 때뿐이다. 예를 들어 CSRF를 다시 켜면 `LogoutFilter`는 `POST /logout`만 처리하므로, `GET /logout`이 이 메서드까지 오게 된다.

## 7. 그 밖에 알아 둘 점

### `/static/**` 무시 설정
Spring Boot는 `src/main/resources/static/` 안의 파일을 `/static` 없이 제공한다. `static/js/article.js`의 주소는 `/js/article.js`다. 그래서 `web.ignoring().requestMatchers("/static/**")`와 맞는 요청이 없고, `/js/article.js`는 3번 체인을 거쳐 `anyRequest().authenticated()`의 적용을 받는다.

지금은 `article.js`를 로그인 후 화면에서만 불러오기 때문에 문제가 드러나지 않는다. 로그인 전 화면에서 정적 파일을 쓰게 되면 `permitAll()` 목록에 실제 경로(`/js/**`, `/css/**` 등)를 넣어야 한다.

### 권한 이름
`User.getAuthorities()`는 `"user"` 권한을 돌려준다. 현재 접근 규칙은 `authenticated()`만 쓰므로 권한 이름은 사용되지 않는다. 나중에 `hasRole("USER")`를 쓰려면 권한 이름이 `ROLE_USER`여야 한다. `hasRole()`은 앞에 `ROLE_`을 붙여서 비교하기 때문이다.

## 8. 한눈에 보기
| 요청 | 거치는 보안 처리 | 컨트롤러 | 뷰 | 응답 |
|---|---|---|---|---|
| `GET /signup` | 인가: `permitAll` | `UserViewController.signup()` | `signup.html` | 200 |
| `POST /user` | 인가: `permitAll` | `UserApiController.signup()` → `UserService.save()` | - | 302 `/login` |
| `GET /login` | 인가: `permitAll` | `UserViewController.login()` | `login.html` | 200 |
| `POST /login` | **`UsernamePasswordAuthenticationFilter`가 처리** → `UserDetailService` → `BCryptPasswordEncoder` | 없음 | - | 302 `/articles` 또는 `/login?error` |
| `GET /articles` (로그인 후) | 세션에서 인증 정보 복원 → 인가: `authenticated` | `BlogViewController.getArticles()` | `articleList.html` | 200 |
| `GET /articles` (로그인 전) | 인가 실패 → **`ExceptionTranslationFilter`** | 없음 | - | 302 `/login` |
| `/api/articles` (로그인 후) | 세션에서 인증 정보 복원 → 인가: `authenticated` | `BlogApiController` | - | JSON |
| `GET /logout` | **`LogoutFilter`가 처리** | 없음 (`UserApiController.logout()`은 실행되지 않음) | - | 302 `/login` |
