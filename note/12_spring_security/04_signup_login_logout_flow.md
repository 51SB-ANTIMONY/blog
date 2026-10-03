---
title: 회원가입, 로그인, 로그아웃 시 필터의 흐름
date: 2026-10-03
tags: [spring-security, filter, form-login, session, http]
---

# 회원가입, 로그인, 로그아웃 시 필터의 흐름

> 필터 각각의 역할은 [필터 기반 동작 원리](./03_filter_architecture.md)에서 설명했다.
> 이 노트는 **폼 로그인 + 세션 방식, CSRF 켜짐(기본값)** 구성을 기준으로, 사용자가 실제로 겪는 순서대로 요청 하나하나를 따라간다.
> 각 요청마다 **HTTP 원문 → 거치는 필터 → 컨트롤러/뷰 → 응답 원문** 순서로 적는다.

> **`Location` 헤더 예시에 대해**
> 이 노트의 `Location: /login` 같은 예시는 **Spring Security가 요청한 값**이다. 실제로 Spring Boot 내장 Tomcat으로 실행하면, `server.tomcat.use-relative-redirects`의 기본값이 `false`라서 Tomcat이 이것을 `Location: http://localhost:8080/login`처럼 **절대 주소로 바꿔서** 응답한다. MockMvc 테스트에서는 Tomcat을 거치지 않으므로 `/login` 그대로 보인다. 자세한 내용은 [accounts 동작 확인과 트러블슈팅](../16_accounts_separation/05_step1_accounts_verification_and_troubleshooting.md) 4번.

## 등장하는 구성 요소
| 종류 | 이름 | 하는 일 |
|---|---|---|
| 보안 설정 | `SecurityFilterChain` | `/login`, `/signup`, `/user`는 누구나, 나머지는 로그인 필요 |
| 뷰 컨트롤러 | `UserViewController` | `GET /login` → `login.html`, `GET /signup` → `signup.html` |
| 컨트롤러 | `UserApiController` | `POST /user` → 회원가입 처리 |
| 서비스 | `UserService` | 비밀번호 해시 후 회원 저장 |
| 서비스 | `UserDetailService` | 로그인 시 이메일로 회원 조회 (`UserDetailsService` 구현) |
| 뷰 | `login.html`, `signup.html`, `articleList.html` | Thymeleaf 템플릿 |

> **뷰 리졸버(ViewResolver)란?**
> 컨트롤러가 반환한 문자열(뷰 이름)을 실제 템플릿 파일로 연결하는 Spring MVC 구성 요소다. Thymeleaf를 쓰면 `ThymeleafViewResolver`가 `"login"`을 `templates/login.html`로 연결하고 HTML을 만든다.

아래 그림에서 필터 이름은 줄여 쓴다.

| 줄임말 | 필터 |
|---|---|
| `ContextHolder` | `SecurityContextHolderFilter` |
| `Header` | `HeaderWriterFilter` |
| `Csrf` | `CsrfFilter` |
| `Logout` | `LogoutFilter` |
| `UsernamePassword` | `UsernamePasswordAuthenticationFilter` |
| `RequestCache` | `RequestCacheAwareFilter` |
| `Anonymous` | `AnonymousAuthenticationFilter` |
| `ExceptionTranslation` | `ExceptionTranslationFilter` |
| `Authorization` | `AuthorizationFilter` |

`DisableEncodeUrlFilter`, `WebAsyncManagerIntegrationFilter`, `SecurityContextHolderAwareRequestFilter`는 모든 요청에서 하는 일이 같고 흐름에 영향이 없으므로 그림에서 생략한다.

---

## 1단계. 회원가입 화면 열기: `GET /signup`

### 요청
```http
GET /signup HTTP/1.1
Host: localhost:8080
```

### 흐름
```
ContextHolder         세션 없음 → 빈 SecurityContext
Header                보안 헤더 추가 예약
Csrf                  GET → 검사 생략. 단, 화면에 넣을 CSRF 토큰을 준비
Logout                /logout 아님 → 통과
UsernamePassword      POST /login 아님 → 통과
RequestCache          ?continue 없음 → 통과
Anonymous             인증 정보 없음 → 익명 사용자 넣기
ExceptionTranslation  try {
Authorization           /signup → permitAll() → 허용
                      }
  ▼
DispatcherServlet
  ▼
UserViewController.signup()  → "signup"
  ▼
ThymeleafViewResolver → templates/signup.html 렌더링
  · th:action이 있는 <form>에 CSRF 토큰 hidden input 자동 삽입
```

### 응답
```http
HTTP/1.1 200
Set-Cookie: JSESSIONID=AAAA1111; Path=/; HttpOnly
Content-Type: text/html;charset=UTF-8
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY

<!DOCTYPE html>
...
<form action="/user" method="POST">
    <input type="hidden" name="_csrf" value="Xk3f...9aQ"/>
    <input type="email" name="email">
    <input type="password" name="password">
    <button type="submit">Submit</button>
</form>
...
```
- CSRF 토큰을 세션에 저장해야 하므로 세션이 만들어지고 `Set-Cookie`가 내려온다.
- 이후 응답들에도 같은 보안 헤더가 붙지만, 아래에서는 생략한다.

---

## 2단계. 회원가입 제출: `POST /user`

### 요청
```http
POST /user HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded
Cookie: JSESSIONID=AAAA1111

_csrf=Xk3f...9aQ&email=a%40a.com&password=1234
```
> **`application/x-www-form-urlencoded`란?**
> HTML `<form>`이 기본으로 쓰는 본문 형식이다. `이름=값`을 `&`로 잇고, `@`처럼 URL에 그대로 쓸 수 없는 문자는 `%` 뒤에 16진수 코드를 붙여 바꾼다(퍼센트 인코딩). `@`는 `%40`이다.

### 흐름
```
ContextHolder         세션 AAAA1111 조회 → SecurityContext 없음 → 빈 값
Csrf                  POST → 본문의 _csrf 값과 세션의 토큰 비교 → 일치 → 통과
                        (불일치나 누락이면 여기서 403 Forbidden으로 끝)
Logout                통과
UsernamePassword      POST /login 아님 → 통과
Anonymous             익명 사용자 넣기
Authorization         /user → permitAll() → 허용
  ▼
DispatcherServlet
  ▼
UserApiController.signup(AddUserRequest)
  · 폼 파라미터 email, password가 setter로 AddUserRequest에 들어감
  ▼
UserService.save()
  · passwordEncoder.encode("1234") → "$2a$10$..." (60글자)
  · userRepository.save(User) → INSERT INTO users ...
  ▼
"redirect:/login"
```
회원가입은 Spring Security 입장에서는 **특별한 처리가 없는 평범한 요청**이다. 접근만 허용되면(`permitAll`) 일반 컨트롤러가 처리한다. Spring Security가 관여하는 부분은 CSRF 검사와 `PasswordEncoder` 제공뿐이다.

### 응답
```http
HTTP/1.1 302
Location: /login
```
브라우저는 `Location`을 보고 바로 `GET /login`을 요청한다.

> **POST 후 리다이렉트하는 이유 (PRG 패턴)**
> POST 요청에 HTML을 바로 응답하면, 사용자가 새로고침할 때 브라우저가 POST를 다시 보내서 같은 회원이 두 번 가입될 수 있다. POST 처리 후 302로 GET 페이지에 보내면 새로고침해도 GET만 반복된다. 이 방식을 Post/Redirect/Get(PRG) 패턴이라고 한다.

---

## 3단계. 로그인 화면 열기: `GET /login`

### 요청
```http
GET /login HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=AAAA1111
```

### 흐름
```
ContextHolder         빈 SecurityContext
Csrf                  GET → 검사 생략, 토큰 준비
Logout                통과
UsernamePassword      GET이므로 통과 (POST /login에만 반응)
Anonymous             익명 사용자
Authorization         /login → permitAll() → 허용
  ▼
UserViewController.login() → "login" → templates/login.html
```
`GET /login`과 `POST /login`은 주소가 같지만 처리하는 곳이 다르다.

| 요청 | 처리하는 곳 |
|---|---|
| `GET /login` | 개발자가 만든 컨트롤러 (화면 보여 주기) |
| `POST /login` | `UsernamePasswordAuthenticationFilter` (로그인 처리) |

### 응답
```http
HTTP/1.1 200
Content-Type: text/html;charset=UTF-8

...
<form action="/login" method="POST">
    <input type="hidden" name="_csrf" value="Xk3f...9aQ"/>
    <input type="email" name="username">
    <input type="password" name="password">
</form>
...
```

---

## 4단계. 로그인 제출: `POST /login`

### 요청
```http
POST /login HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded
Cookie: JSESSIONID=AAAA1111

_csrf=Xk3f...9aQ&username=a%40a.com&password=1234
```

### 흐름
```
ContextHolder         빈 SecurityContext
Csrf                  토큰 일치 → 통과
Logout                통과
UsernamePassword      POST /login → 이 필터가 처리를 시작한다 ★
  │
  ├ ① username = "a@a.com", password = "1234" 꺼내기
  ├ ② UsernamePasswordAuthenticationToken.unauthenticated("a@a.com", "1234")
  ├ ③ AuthenticationManager(ProviderManager).authenticate()
  │     └ DaoAuthenticationProvider
  │         ├ UserDetailService.loadUserByUsername("a@a.com")
  │         │   └ UserRepository.findByEmail() → SELECT ... FROM users WHERE email=?
  │         │   ← User (UserDetails)
  │         ├ 계정 상태 확인: isAccountNonLocked, isEnabled, isAccountNonExpired
  │         ├ PasswordEncoder.matches("1234", "$2a$10$...") → true
  │         ├ isCredentialsNonExpired 확인
  │         └ 인증 후 Authentication 생성
  │             principal=User(a@a.com), authorities=[user], authenticated=true
  │
  ├ ④ 세션 고정 방어: 세션 ID 교체  AAAA1111 → BBBB2222
  ├ ⑤ SecurityContextHolder에 Authentication 저장
  ├ ⑥ HttpSessionSecurityContextRepository.saveContext()
  │     세션 BBBB2222["SPRING_SECURITY_CONTEXT"] = SecurityContext
  ├ ⑦ CSRF 토큰 새로 발급 (로그인 전 토큰 무효화)
  └ ⑧ AuthenticationSuccessHandler
        · 세션에 저장된 요청(SPRING_SECURITY_SAVED_REQUEST)이 있으면 그 주소로
        · 없으면 defaultSuccessUrl로
  ▼
체인 종료. Anonymous, Authorization, 컨트롤러는 실행되지 않는다.
```

### 응답 (성공)
저장된 요청이 없을 때 (`/login`에 바로 들어와서 로그인한 경우):
```http
HTTP/1.1 302
Set-Cookie: JSESSIONID=BBBB2222; Path=/; HttpOnly
Location: /articles
```
저장된 요청이 있을 때 (`/articles`에 들어가려다 로그인 페이지로 보내진 경우):
```http
HTTP/1.1 302
Set-Cookie: JSESSIONID=BBBB2222; Path=/; HttpOnly
Location: http://localhost:8080/articles?continue
```
- **새 세션 ID**가 `Set-Cookie`로 내려온다. 브라우저는 기존 쿠키를 덮어쓴다.
- `?continue`가 붙은 요청은 `RequestCacheAwareFilter`가 저장된 요청을 복원하는 신호다.

### 흐름 (실패)
```
UsernamePassword
  ├ ③ DaoAuthenticationProvider
  │     PasswordEncoder.matches("9999", "$2a$10$...") → false
  │     → BadCredentialsException
  ├ SecurityContextHolder 비우기
  └ AuthenticationFailureHandler → /login?error
```
```http
HTTP/1.1 302
Location: /login?error
```
브라우저가 `GET /login?error`를 요청하면 다시 3단계와 같은 흐름을 탄다. 템플릿에서 `${param.error}`로 에러 문구를 보여 줄 수 있다.

---

## 5단계. 로그인 후 페이지 요청: `GET /articles`

### 요청
```http
GET /articles HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=BBBB2222
```

### 흐름
```
ContextHolder         세션 BBBB2222["SPRING_SECURITY_CONTEXT"] 복원
                      → SecurityContextHolder: Authentication(a@a.com)
Csrf                  GET → 생략
Logout                통과
UsernamePassword      통과
RequestCache          ?continue 없음 → 통과
Anonymous             이미 인증 정보 있음 → 아무것도 하지 않음
Authorization         anyRequest().authenticated() → 인증된 사용자 → 허용
  ▼
BlogViewController.getArticles() → "articleList" → templates/articleList.html
  ▼
ContextHolder         (응답 후) SecurityContextHolder 비우기
```

### 응답
```http
HTTP/1.1 200
Content-Type: text/html;charset=UTF-8

<!DOCTYPE html> ... 글 목록 ...
```
이후 요청들도 모두 같은 흐름이다. **비밀번호를 다시 확인하지 않는다.** 쿠키의 세션 ID로 세션에 저장해 둔 `Authentication`을 꺼내 쓸 뿐이다.

---

## (참고) 로그인하지 않고 보호된 페이지에 접근할 때
### 요청
```http
GET /articles HTTP/1.1
Host: localhost:8080
```
### 흐름
```
ContextHolder         세션 없음 → 빈 SecurityContext
Anonymous             익명 사용자 넣기
ExceptionTranslation  try {
Authorization           authenticated() 불만족 → AccessDeniedException
                      } catch:
                        익명 사용자 → 인증 시작
                        ① HttpSessionRequestCache: 세션 생성,
                           세션["SPRING_SECURITY_SAVED_REQUEST"] = GET /articles
                        ② LoginUrlAuthenticationEntryPoint → /login으로 리다이렉트
```
### 응답
```http
HTTP/1.1 302
Set-Cookie: JSESSIONID=CCCC3333; Path=/; HttpOnly
Location: /login
```
이 상태에서 로그인하면 4단계의 "저장된 요청이 있을 때"처럼 `/articles?continue`로 돌아간다.

---

## 6단계. 로그아웃: `POST /logout`
CSRF가 켜져 있으면 로그아웃은 **POST로만** 할 수 있다. 그래서 로그아웃 버튼은 링크가 아니라 폼으로 만든다.
```html
<form th:action="@{/logout}" method="POST">
    <button type="submit">로그아웃</button>
</form>
```

### 요청
```http
POST /logout HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded
Cookie: JSESSIONID=BBBB2222

_csrf=Pq7w...2Lm
```

### 흐름
```
ContextHolder         세션 BBBB2222 → Authentication(a@a.com) 복원
Csrf                  토큰 일치 → 통과
Logout                POST /logout → 이 필터가 처리한다 ★
  ├ SecurityContextLogoutHandler
  │   · 세션 BBBB2222 무효화 (세션 안의 SPRING_SECURITY_CONTEXT도 함께 사라짐)
  │   · SecurityContextHolder 비우기
  ├ CsrfLogoutHandler: CSRF 토큰 삭제
  ├ LogoutSuccessEventPublishingLogoutHandler: 로그아웃 이벤트 발행
  └ LogoutSuccessHandler → /login?logout
  ▼
체인 종료. 컨트롤러는 실행되지 않는다.
```

### 응답
```http
HTTP/1.1 302
Location: /login?logout
```
- 서버의 세션은 없어졌지만, 브라우저에는 `JSESSIONID=BBBB2222` 쿠키가 남아 있다. 다음 요청에 이 쿠키를 보내도 서버에 해당 세션이 없으므로 익명 사용자로 처리된다.
- 쿠키까지 지우려면 `logout`에 `.deleteCookies("JSESSIONID")`를 설정한다. 그러면 응답에 `Set-Cookie: JSESSIONID=; Max-Age=0; ...`가 추가되어 브라우저가 쿠키를 삭제한다.

---

## 전체 요약
| 단계 | 요청 | 처리하는 곳 | 응답 |
|---|---|---|---|
| 회원가입 화면 | `GET /signup` | `UserViewController` → `signup.html` | 200 HTML |
| 회원가입 | `POST /user` | `UserApiController` → `UserService` | 302 → `/login` |
| 로그인 화면 | `GET /login` | `UserViewController` → `login.html` | 200 HTML |
| 로그인 | `POST /login` | **`UsernamePasswordAuthenticationFilter`** | 302 → 성공 URL 또는 `/login?error` |
| 보호된 페이지 | `GET /articles` | `AuthorizationFilter` 통과 후 `BlogViewController` | 200 HTML |
| 로그인 없이 보호된 페이지 | `GET /articles` | **`ExceptionTranslationFilter`** | 302 → `/login` |
| 로그아웃 | `POST /logout` | **`LogoutFilter`** | 302 → `/login?logout` |

**굵게 표시한 것**은 컨트롤러가 아니라 필터가 직접 응답하는 경우다.
