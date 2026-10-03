---
title: Spring Security의 필터 기반 동작 원리
date: 2026-10-03
tags: [spring-security, filter, servlet]
---

# Spring Security의 필터 기반 동작 원리

> 구성 요소(`Authentication`, `SecurityContextHolder`, `AuthenticationManager` 등)는 [Spring Security란 무엇인가](./02_spring_security.md)에서 설명했다.

## 1. 요청이 컨트롤러에 도착하기까지

### 서블릿 컨테이너와 DispatcherServlet
> **서블릿(Servlet)이란?**
> Java로 HTTP 요청을 받아 응답을 만드는 프로그램의 표준 형식이다.

> **서블릿 컨테이너란?**
> 서블릿을 실행하고 관리하는 서버 프로그램이다. HTTP 요청을 받아 Java 객체(`HttpServletRequest`)로 바꾸고, 알맞은 서블릿에 넘긴다. Spring Boot에 내장된 **Tomcat**이 서블릿 컨테이너다([내장 WAS](../02_spring_boot/03_difference_embedded_was.md)).

> **DispatcherServlet이란?**
> Spring MVC가 제공하는 서블릿이다. 모든 요청을 받아서 URL에 맞는 컨트롤러 메서드를 찾아 호출하고, 반환값으로 HTML이나 JSON 응답을 만든다.

필터가 없다면 요청은 이렇게 흐른다.
```
브라우저 ─▶ Tomcat ─▶ DispatcherServlet ─▶ 컨트롤러
```

### 필터 (Filter)
> **필터란?**
> 서블릿 표준에 정의된 기능으로, 요청이 **서블릿에 도착하기 전**과 응답이 **서블릿에서 나온 후**에 끼어들어 실행되는 코드다.

필터는 이 인터페이스를 구현한다.
```java
public interface Filter {
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain);
}
```

필터는 여러 개가 줄지어 실행되며, 이 줄을 **필터 체인(FilterChain)**이라고 한다.
```
브라우저 ─▶ Tomcat ─▶ [필터 A] ─▶ [필터 B] ─▶ [필터 C] ─▶ DispatcherServlet ─▶ 컨트롤러
```

필터 하나의 일반적인 모양은 이렇다.
```java
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    // ① 요청이 다음으로 가기 전에 할 일

    chain.doFilter(request, response);   // ② 다음 필터(또는 서블릿)로 넘긴다

    // ③ 응답이 돌아온 후에 할 일
}
```
필터가 할 수 있는 선택은 세 가지다.

| 선택 | 방법 | 예시 |
|---|---|---|
| 통과시킨다 | `chain.doFilter()` 호출 | 로그인된 사용자의 요청 |
| 막는다 | `chain.doFilter()`를 호출하지 않고 직접 응답을 쓴다 | 로그인 안 된 사용자를 `/login`으로 리다이렉트 |
| 직접 처리하고 끝낸다 | `chain.doFilter()`를 호출하지 않고 처리 결과를 응답한다 | `POST /login` 요청을 필터가 직접 처리 |

**Spring Security가 필터를 쓰는 이유:** 필터는 DispatcherServlet보다 **앞에서** 실행된다. 그래서 요청이 컨트롤러에 닿기 전에 막을 수 있다. 보안 검사를 각 컨트롤러에 넣지 않아도 모든 요청에 똑같이 적용된다.

## 2. 서블릿 필터와 Spring 빈을 연결하는 구조
문제가 하나 있다. 서블릿 필터는 Tomcat이 관리하고, Spring Security의 구성 요소들은 Spring 컨테이너가 관리하는 빈이다([빈과 컨테이너](../03_spring_concepts/03_bean_and_container.md)). Tomcat은 Spring 빈을 모른다.

Spring Security는 세 단계로 이 둘을 연결한다.
```
Tomcat의 필터 체인
  [다른 필터] ─▶ [DelegatingFilterProxy] ─▶ [다른 필터] ─▶ DispatcherServlet
                        │
                        │ 위임
                        ▼
                  FilterChainProxy  (Spring 빈, 이름: springSecurityFilterChain)
                        │
                        │ 요청 URL에 맞는 체인 하나를 고른다
                        ▼
              ┌─ SecurityFilterChain A : /h2-console/**  → 보안 필터 0개
              └─ SecurityFilterChain B : 그 외 모든 요청 → 보안 필터 여러 개
                        │
                        ▼
              [보안 필터 1] ─▶ [보안 필터 2] ─▶ ... ─▶ [보안 필터 N]
```

| 구성 요소 | 정체 | 역할 |
|---|---|---|
| `DelegatingFilterProxy` | Tomcat에 등록된 **서블릿 필터** | 직접 일하지 않고, Spring 컨테이너에서 `springSecurityFilterChain` 빈을 찾아 일을 넘긴다. |
| `FilterChainProxy` | **Spring 빈** | Spring Security의 실제 입구. 요청에 맞는 `SecurityFilterChain`을 골라 그 안의 필터들을 실행한다. |
| `SecurityFilterChain` | **Spring 빈** | "어떤 요청에 적용할지(요청 매처)" + "실행할 보안 필터 목록"의 묶음. 개발자가 `HttpSecurity`로 만든다. |

> **위임(Delegation)이란?**
> 자신이 받은 일을 다른 객체에게 넘겨서 처리하게 하는 것이다.

### SecurityFilterChain이 여러 개일 때
`FilterChainProxy`는 등록된 `SecurityFilterChain`들을 순서대로 보면서 **요청과 맞는 첫 번째 체인 하나만** 실행한다.

`WebSecurityCustomizer`의 `web.ignoring()`으로 지정한 경로는 **필터가 0개인 체인**이 맨 앞에 만들어진다. 그래서 그 경로의 요청은 보안 필터를 하나도 거치지 않는다. 인증 정보도 읽지 않고, 보안 헤더도 붙지 않는다.

> **`ignoring()`과 `permitAll()`의 차이**
> `ignoring()`: 보안 필터를 아예 거치지 않는다.
> `permitAll()`: 보안 필터를 모두 거치되, 마지막 인가 검사에서 "누구나 허용"으로 통과한다. 로그인한 사용자라면 인증 정보도 읽히고, 보안 헤더도 붙는다.
> Spring Security는 정적 파일에도 `permitAll()`을 권장한다. `ignoring()`을 쓰면 시작할 때 경고 로그가 나온다.

## 3. 보안 필터의 종류와 순서
`SecurityFilterChain` 안의 필터들은 **정해진 순서**로 실행된다. 앞 필터의 결과를 뒤 필터가 쓰기 때문에 순서가 중요하다. 어떤 필터가 들어가는지는 설정에 따라 다르다.

아래는 **폼 로그인을 쓰고 CSRF를 켠 일반적인 구성**의 필터 목록이다(Spring Security 7.0 기준).

| 순서 | 필터 | 하는 일 | 분류 |
|---|---|---|---|
| 1 | `DisableEncodeUrlFilter` | 세션 ID가 URL에 붙어(`;jsessionid=...`) 노출되는 것을 막는다. | 준비 |
| 2 | `WebAsyncManagerIntegrationFilter` | 비동기 처리 스레드에서도 `SecurityContext`를 쓸 수 있게 연결한다. | 준비 |
| 3 | `SecurityContextHolderFilter` | 세션에 저장된 `SecurityContext`를 꺼내 `SecurityContextHolder`에 넣는다. 요청이 끝나면 비운다. | **인증 정보 로드** |
| 4 | `HeaderWriterFilter` | 응답에 보안 헤더를 추가한다. | 공격 방어 |
| 5 | `CsrfFilter` | `POST`, `PUT`, `DELETE` 등의 요청에 올바른 CSRF 토큰이 있는지 검사한다. | 공격 방어 |
| 6 | `LogoutFilter` | 로그아웃 요청이면 로그아웃을 처리하고 끝낸다. | **로그아웃** |
| 7 | `UsernamePasswordAuthenticationFilter` | `POST /login` 요청이면 로그인을 처리하고 끝낸다. | **로그인** |
| 8 | `DefaultLoginPageGeneratingFilter` | 로그인 페이지를 직접 만들지 않았을 때 기본 로그인 HTML을 만들어 준다. | 기본 화면 |
| 9 | `DefaultLogoutPageGeneratingFilter` | 기본 로그아웃 확인 HTML을 만들어 준다. | 기본 화면 |
| 10 | `RequestCacheAwareFilter` | 로그인 전에 가려던 요청이 저장되어 있으면 그 요청을 복원한다. | 편의 |
| 11 | `SecurityContextHolderAwareRequestFilter` | `request.isUserInRole()`, `request.logout()` 같은 서블릿 API가 Spring Security와 연동되게 요청 객체를 감싼다. | 연동 |
| 12 | `AnonymousAuthenticationFilter` | 이 시점까지 인증 정보가 없으면 **익명 사용자** `Authentication`을 넣는다. | **익명 처리** |
| 13 | `ExceptionTranslationFilter` | 뒤에서 발생한 인증/인가 예외를 잡아서 로그인 페이지 리다이렉트나 403 응답으로 바꾼다. | **예외 처리** |
| 14 | `AuthorizationFilter` | 접근 규칙을 검사해서 허용하거나 예외를 던진다. | **인가** |

다른 설정을 켜면 이 사이에 필터가 더 들어간다. 예를 들어 `httpBasic()`을 켜면 `BasicAuthenticationFilter`, `rememberMe()`를 켜면 `RememberMeAuthenticationFilter`, CORS 설정이 있으면 `CorsFilter`가 추가된다.

> **확인하는 방법:** `application.yml`에 아래 설정을 넣고 실행하면, 시작 로그에 실제 필터 목록이 출력되고 요청마다 어떤 필터를 거치는지 로그가 찍힌다.
> ```yaml
> logging:
>   level:
>     org.springframework.security: TRACE
> ```

## 4. 핵심 필터 자세히 보기

### `SecurityContextHolderFilter` - 요청마다 로그인 정보 복원
```
요청 도착
  ▼
SecurityContextRepository.loadContext(request)
  │  쿠키의 JSESSIONID로 세션을 찾고
  │  세션의 "SPRING_SECURITY_CONTEXT" 값을 꺼낸다
  ▼
SecurityContextHolder에 넣는다
  ▼
chain.doFilter()  → 뒤의 필터, 컨트롤러가 SecurityContextHolder를 사용
  ▼
finally: SecurityContextHolder를 비운다
```
- 세션이 없거나 세션에 `SPRING_SECURITY_CONTEXT`가 없으면 빈 `SecurityContext`가 들어간다.
- 실제로 세션을 읽는 시점은 누군가 인증 정보를 처음 사용할 때다(지연 로딩).
- 이 필터는 **저장하지 않는다.** 저장은 로그인 필터가 로그인 성공 시점에 직접 한다.
- 요청이 끝날 때 반드시 비우는 이유: Tomcat은 스레드를 재사용한다. 비우지 않으면 다음 요청이 같은 스레드에 배정됐을 때 이전 사용자의 로그인 정보가 남아 있게 된다.

### `HeaderWriterFilter` - 보안 헤더 추가
모든 응답에 아래 헤더를 기본으로 붙인다.
```http
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
```
| 헤더 | 의미 |
|---|---|
| `X-Content-Type-Options: nosniff` | 브라우저가 `Content-Type`을 무시하고 내용을 보고 형식을 추측하지 못하게 한다. |
| `X-XSS-Protection: 0` | 오래된 브라우저의 XSS 필터 기능을 끈다. 이 기능 자체가 보안 문제를 일으켜서 끄는 것이 현재 권장 사항이다. |
| `Cache-Control`, `Pragma`, `Expires` | 로그인 사용자의 화면이 브라우저나 중간 서버에 캐시되지 않게 한다. |
| `X-Frame-Options: DENY` | 다른 사이트가 이 페이지를 `<iframe>` 안에 넣지 못하게 한다. 투명한 iframe 위에서 클릭을 유도하는 공격(클릭재킹)을 막는다. |

HTTPS 연결이면 `Strict-Transport-Security`(이후 HTTPS로만 접속하라는 지시)도 붙는다.

### `CsrfFilter` - CSRF 토큰 검사
- `GET`, `HEAD`, `TRACE`, `OPTIONS` 요청은 검사하지 않는다(데이터를 바꾸지 않는 메서드).
- 그 외 요청은 요청에 담긴 토큰(폼 파라미터 `_csrf` 또는 헤더 `X-CSRF-TOKEN`)이 세션에 저장된 토큰과 같은지 비교한다.
- 다르거나 없으면 `AccessDeniedException` → **403 Forbidden**.
- Thymeleaf의 `th:action`을 쓴 `<form>`에는 hidden input으로 토큰이 자동으로 들어간다.

### `LogoutFilter` - 로그아웃
요청이 로그아웃 주소와 맞으면 다음을 하고 **체인을 끝낸다**(컨트롤러까지 가지 않는다).
1. 등록된 `LogoutHandler`들을 실행한다.
   - `SecurityContextLogoutHandler`: 세션을 무효화하고 `SecurityContextHolder`를 비운다.
   - `CsrfLogoutHandler` (CSRF가 켜져 있을 때): CSRF 토큰을 지운다.
   - `LogoutSuccessEventPublishingLogoutHandler`: "로그아웃 성공" 이벤트를 발행한다.
2. `LogoutSuccessHandler`를 실행한다. 기본은 `/login?logout`으로 리다이렉트한다.

어떤 요청을 로그아웃으로 볼지는 CSRF 설정에 따라 다르다.

| CSRF | 로그아웃으로 처리하는 요청 |
|---|---|
| 켜짐 (기본) | `POST /logout`만 |
| 꺼짐 | `GET`, `POST`, `PUT`, `DELETE` `/logout` 모두 |

CSRF가 켜져 있을 때 `GET /logout`을 막는 이유: `GET`은 CSRF 토큰 검사를 받지 않는다. 다른 사이트가 `<img src="http://localhost:8080/logout">` 하나만 넣어도 사용자를 강제로 로그아웃시킬 수 있게 된다.

### `UsernamePasswordAuthenticationFilter` - 폼 로그인
**`POST /login` 요청에만** 반응한다. 다른 요청은 그대로 통과시킨다.

```
POST /login 도착
  ▼
① 폼 파라미터 username, password를 꺼낸다
  ▼
② 인증 전 Authentication 생성
   UsernamePasswordAuthenticationToken.unauthenticated(username, password)
  ▼
③ AuthenticationManager.authenticate() 호출
   → ProviderManager → DaoAuthenticationProvider
     → UserDetailsService.loadUserByUsername(username)
     → 계정 상태 확인
     → PasswordEncoder.matches(password, 저장된 해시)
  ▼
 성공                                       실패 (AuthenticationException)
  ▼                                          ▼
④ 세션 ID 교체 (세션 고정 방어)              SecurityContextHolder 비우기
⑤ SecurityContextHolder에 저장               AuthenticationFailureHandler
⑥ SecurityContextRepository로 세션에 저장      → 302 /login?error
⑦ AuthenticationSuccessHandler
   → 302 (저장된 요청 또는 defaultSuccessUrl)
  ▼
체인을 끝낸다 (컨트롤러로 가지 않는다)
```

**`POST /login`을 처리하는 컨트롤러는 만들지 않는다.** 이 필터가 요청을 처리하고 응답까지 끝내기 때문이다. 개발자가 만드는 것은 로그인 **화면**을 보여 주는 `GET /login` 컨트롤러뿐이다.

파라미터 이름은 기본으로 `username`, `password`다. 화면에서 이메일을 입력받더라도 `<input name="username">`으로 보내야 한다. 이름을 바꾸려면 `formLogin`에서 `.usernameParameter("email")`처럼 설정한다.

**로그인 실패의 종류**
| 원인 | 예외 | 사용자에게 보이는 결과 |
|---|---|---|
| 아이디 없음 | `UsernameNotFoundException` → `BadCredentialsException`으로 바뀜 | `/login?error` |
| 비밀번호 틀림 | `BadCredentialsException` | `/login?error` |
| 계정 잠김 | `LockedException` | `/login?error` |
| 계정 비활성 | `DisabledException` | `/login?error` |

아이디가 없을 때 예외를 `BadCredentialsException`으로 바꾸는 이유: "아이디가 없다"와 "비밀번호가 틀렸다"를 구분해서 알려 주면, 공격자가 어떤 아이디가 가입되어 있는지 알아낼 수 있기 때문이다. 같은 이유로, 아이디가 없을 때도 비밀번호 비교와 비슷한 시간이 걸리도록 일부러 해시 계산을 한 번 한다.

### `AnonymousAuthenticationFilter` - 익명 사용자
여기까지 왔는데 `SecurityContextHolder`에 인증 정보가 없으면 `AnonymousAuthenticationToken`을 넣는다.
```
principal  : "anonymousUser"
authorities: [ROLE_ANONYMOUS]
```
"인증 정보가 없음(`null`)"과 "익명 사용자"를 구분하지 않고 항상 `Authentication` 객체가 있게 만들어서, 뒤의 인가 필터가 `null` 검사 없이 판단할 수 있게 한다.

### `AuthorizationFilter` - 인가
설정한 접근 규칙을 **위에서부터 순서대로** 보고, 요청과 맞는 **첫 번째 규칙**을 적용한다.
```java
.requestMatchers("/login", "/signup").permitAll()   // 규칙 1
.requestMatchers("/admin/**").hasRole("ADMIN")      // 규칙 2
.anyRequest().authenticated()                       // 규칙 3
```
| 규칙 메서드 | 허용 조건 |
|---|---|
| `permitAll()` | 항상 허용 (익명 사용자 포함) |
| `authenticated()` | 익명 사용자가 아니면 허용 |
| `hasRole("ADMIN")` | `ROLE_ADMIN` 권한이 있으면 허용 |
| `hasAuthority("article:write")` | `article:write` 권한이 있으면 허용 |
| `denyAll()` | 항상 거부 |

- 허용: `chain.doFilter()` → DispatcherServlet → 컨트롤러
- 거부: `AccessDeniedException`을 던진다.

`anyRequest()`는 모든 요청과 맞으므로 반드시 **마지막**에 둔다. 앞에 두면 그 뒤의 규칙은 절대 적용되지 않는다.

### `ExceptionTranslationFilter` - 예외를 HTTP 응답으로 바꾸기
`AuthorizationFilter`보다 **앞에** 있으면서, 뒤쪽 처리 전체를 `try-catch`로 감싸고 있다.
```java
try {
    chain.doFilter(request, response);   // 이 안에 AuthorizationFilter가 있다
} catch (AuthenticationException | AccessDeniedException e) {
    // 아래 표대로 처리
}
```
| 잡은 예외 | 현재 사용자 | 처리 | 결과 |
|---|---|---|---|
| `AccessDeniedException` | **익명** | 인증 시작 | 요청 저장 → 로그인 페이지로 302 |
| `AccessDeniedException` | 로그인한 사용자 | `AccessDeniedHandler` | **403 Forbidden** |
| `AuthenticationException` | - | 인증 시작 | 요청 저장 → 로그인 페이지로 302 |

"인증 시작"은 두 단계로 이루어진다.
1. **요청 저장 (`RequestCache`)**: 원래 가려던 요청을 세션에 `SPRING_SECURITY_SAVED_REQUEST`라는 이름으로 저장한다. 로그인 후 그 주소로 돌려보내기 위해서다.
2. **`AuthenticationEntryPoint` 실행**: 인증을 시작하는 방법이다. 폼 로그인에서는 `LoginUrlAuthenticationEntryPoint`가 로그인 페이지로 302 리다이렉트한다. HTTP Basic에서는 `401 + WWW-Authenticate` 헤더를 보낸다.

### `RequestCacheAwareFilter` - 저장된 요청 복원
로그인 성공 후, 저장된 요청이 있으면 `Location`에 `?continue`가 붙은 주소로 리다이렉트된다.
```http
Location: http://localhost:8080/articles?continue
```
`?continue` 파라미터가 붙은 요청이 오면 이 필터가 세션에서 저장된 요청을 꺼내 지우고, 원래 요청 정보로 이어서 처리한다. `continue` 파라미터가 없는 요청에서는 세션을 뒤지지 않아 불필요한 세션 조회를 줄인다.

## 5. 정리: 요청 하나가 필터를 지나는 모습
**로그인한 사용자가 `GET /articles`를 요청할 때**
```
GET /articles  (Cookie: JSESSIONID=9F8E...)
 │
 ├ DisableEncodeUrlFilter              통과
 ├ WebAsyncManagerIntegrationFilter    통과
 ├ SecurityContextHolderFilter         세션 9F8E... → Authentication(a@a.com) 복원
 ├ HeaderWriterFilter                  응답에 보안 헤더 예약
 ├ CsrfFilter                          GET이므로 검사 생략
 ├ LogoutFilter                        /logout 아님 → 통과
 ├ UsernamePasswordAuthenticationFilter POST /login 아님 → 통과
 ├ RequestCacheAwareFilter             ?continue 없음 → 통과
 ├ SecurityContextHolderAwareRequestFilter 요청 객체 감싸기
 ├ AnonymousAuthenticationFilter       이미 인증 정보 있음 → 통과
 ├ ExceptionTranslationFilter          try { ...
 ├ AuthorizationFilter                 anyRequest().authenticated() → 허용
 ▼
DispatcherServlet → BlogViewController.getArticles() → articleList.html
 │
 ▼
HTTP/1.1 200  (보안 헤더 포함, HTML 본문)
```

**로그인하지 않은 사용자가 `GET /articles`를 요청할 때**
```
GET /articles  (쿠키 없음)
 │
 ├ SecurityContextHolderFilter         세션 없음 → 빈 SecurityContext
 ├ ...                                 통과
 ├ AnonymousAuthenticationFilter       익명 사용자 Authentication 넣기
 ├ ExceptionTranslationFilter          try {
 ├ AuthorizationFilter                 authenticated() 조건 불만족 → AccessDeniedException
 │                                     }
 ├ ExceptionTranslationFilter          catch: 익명 사용자 → 인증 시작
 │                                       요청 저장 (세션 생성)
 │                                       LoginUrlAuthenticationEntryPoint
 ▼
HTTP/1.1 302
Location: /login
Set-Cookie: JSESSIONID=...; Path=/; HttpOnly
```
컨트롤러는 실행되지 않는다.

## 정리
| 개념 | 핵심 |
|---|---|
| 필터 | 서블릿보다 앞에서 실행되어 요청을 통과시키거나, 막거나, 직접 처리한다 |
| `DelegatingFilterProxy` | Tomcat 필터 체인에서 Spring 빈인 `FilterChainProxy`로 일을 넘긴다 |
| `FilterChainProxy` | 요청과 맞는 첫 번째 `SecurityFilterChain`을 골라 실행한다 |
| `SecurityFilterChain` | 요청 매처 + 보안 필터 목록. `HttpSecurity`로 만든다 |
| 필터 순서 | 인증 정보 로드 → 공격 방어 → 로그아웃/로그인 처리 → 익명 처리 → 예외 변환 → 인가 |
| 로그인, 로그아웃 | 필터가 직접 처리하고 끝낸다. 컨트롤러가 필요 없다 |
| 인가 실패 | 익명이면 로그인 페이지로 302, 로그인 사용자면 403 |
