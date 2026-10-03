---
title: Spring Security란 무엇인가
date: 2026-10-03
tags: [spring-security, authentication, authorization]
---

# Spring Security란 무엇인가

> 인증, 인가, 세션, CSRF 같은 용어는 [인증과 인가](./01_authentication_and_authorization.md)에서 설명했다.
> 이 노트의 내용은 Spring Security 7.0 (Spring Boot 4.0) 기준이다.

## 정의
**Spring 기반 애플리케이션에 인증, 인가, 공격 방어 기능을 제공하는 프레임워크**다.

[인증과 인가](./01_authentication_and_authorization.md)에서 본 일들을 직접 구현하려면 다음을 모두 작성해야 한다.
- 모든 요청마다 세션 쿠키를 읽어서 로그인한 사용자를 알아내는 코드
- URL별로 접근 가능한 사용자를 검사하는 코드
- 로그인 폼 처리, 비밀번호 비교, 세션 생성, 세션 ID 교체
- 로그아웃 시 세션 삭제
- CSRF 토큰 발급과 검사
- 보안 관련 응답 헤더 추가

Spring Security는 이 기능들을 미리 만들어 두었다. 개발자는 **설정**으로 어떤 기능을 어떻게 쓸지 정하고, **"사용자를 어디서 어떻게 찾는지"** 같이 애플리케이션마다 다른 부분만 직접 작성한다.

## Spring Security가 제공하는 기능
| 분류 | 기능 |
|---|---|
| 인증 | 폼 로그인, HTTP Basic, OAuth2 로그인(구글/카카오 로그인 등), JWT 검증, 자동 로그인(remember-me) |
| 인가 | URL별 접근 규칙, 메서드별 접근 규칙(`@PreAuthorize`) |
| 세션 관리 | 로그인 후 세션 ID 교체(세션 고정 방어), 동시 로그인 수 제한 |
| 공격 방어 | CSRF 토큰 검사, 보안 응답 헤더 추가 |
| 비밀번호 | BCrypt 등 비밀번호 해시 도구(`PasswordEncoder`) |

## 의존성을 추가하면 일어나는 일
```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
```
이 한 줄만 추가하고 아무 설정도 하지 않으면, Spring Boot의 자동 구성이 다음을 해 준다([자동 구성](../05_auto_configuration/01_auto_configuration.md)).

1. **모든 URL이 로그인을 요구하게 된다.** 이전에 잘 열리던 `/articles`도 막힌다.
2. **기본 로그인 페이지**(`/login`)와 **기본 로그아웃 확인 페이지**(`/logout`)가 자동으로 생긴다. Spring Security가 HTML을 직접 만들어 준다.
3. **임시 사용자 하나**가 메모리에 만들어진다. 아이디는 `user`이고, 비밀번호는 실행할 때마다 새로 만들어져 콘솔에 출력된다.
   ```
   Using generated security password: 8e557245-73e2-4286-969a-ff57fe326336
   ```
4. CSRF 보호, 보안 응답 헤더, 세션 고정 방어가 켜진다.

개발자가 `SecurityFilterChain` 빈이나 `UserDetailsService` 빈을 직접 등록하면, 자동 구성은 해당 부분을 만들지 않고 개발자의 설정을 쓴다.

## 핵심 구성 요소
Spring Security 코드를 읽으려면 아래 이름들을 알아야 한다. 각 요소가 무엇을 담고, 누가 누구를 호출하는지 순서대로 본다.

### 1. `Authentication` - 인증 정보 객체
"누가 인증을 시도하는지" 또는 "누가 인증되었는지"를 담는 객체다. 인증 전과 후에 같은 타입을 쓴다.

| 필드 | 인증 전 (로그인 시도) | 인증 후 (로그인 성공) |
|---|---|---|
| `principal` | 입력한 아이디 문자열 `"a@a.com"` | DB에서 찾은 사용자 객체 (`UserDetails`) |
| `credentials` | 입력한 비밀번호 `"1234"` | 비워짐 (`null`) |
| `authorities` | 비어 있음 | 사용자의 권한 목록 |
| `authenticated` | `false` | `true` |
| `details` | 요청 부가 정보 (IP 주소, 세션 ID) | 같음 |

폼 로그인에서는 `Authentication`의 구현체로 `UsernamePasswordAuthenticationToken`을 쓴다.

> **구현체란?**
> 인터페이스의 메서드를 실제로 작성한 클래스다. `Authentication`은 인터페이스이고, `UsernamePasswordAuthenticationToken`은 그 구현체 중 하나다.

### 2. `SecurityContext`와 `SecurityContextHolder` - 현재 요청의 인증 정보 보관
```
SecurityContextHolder
  └─ SecurityContext
       └─ Authentication
            ├─ principal  : User(a@a.com)
            ├─ authorities: [user]
            └─ authenticated: true
```
- `SecurityContext`: `Authentication` 하나를 담는 객체다.
- `SecurityContextHolder`: 현재 요청을 처리하는 스레드의 `SecurityContext`를 보관한다.

> **스레드(Thread)란?**
> 프로그램 안에서 코드를 실행하는 하나의 실행 흐름이다. 웹 서버는 요청이 들어올 때마다 스레드 하나를 배정해서 그 요청을 처리한다. 요청 100개가 동시에 오면 스레드 100개가 동시에 일한다.

> **ThreadLocal이란?**
> 스레드마다 따로 값을 저장하는 Java 기능이다. 같은 변수라도 스레드 A가 넣은 값은 스레드 A에서만 보인다.

`SecurityContextHolder`는 내부적으로 ThreadLocal을 쓴다. 그래서 동시에 들어온 요청들의 로그인 정보가 섞이지 않는다. 그리고 컨트롤러, 서비스 어디에서든 아래 코드로 현재 로그인한 사용자를 꺼낼 수 있다.
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
```

**중요:** `SecurityContextHolder`는 요청 하나를 처리하는 동안만 값을 들고 있다. 요청이 끝나면 비워진다. 다음 요청에서도 로그인 상태를 유지하려면 `SecurityContext`를 **세션에 저장해 두었다가 다음 요청에서 다시 꺼내야** 한다. 이 일을 하는 것이 `SecurityContextRepository`다.

### 3. `SecurityContextRepository` - 요청 사이에 인증 정보 저장
| 메서드 | 하는 일 |
|---|---|
| `saveContext` | 로그인에 성공했을 때 `SecurityContext`를 저장한다. |
| `loadContext` | 요청이 들어올 때 저장된 `SecurityContext`를 꺼낸다. |

기본 구현체인 `HttpSessionSecurityContextRepository`는 HTTP 세션에 `SPRING_SECURITY_CONTEXT`라는 이름으로 저장한다.
```
세션 9F8E7D6C5B4A
  └─ "SPRING_SECURITY_CONTEXT" → SecurityContext → Authentication(a@a.com)
```

### 4. `AuthenticationManager`와 `ProviderManager` - 인증 요청 접수
```java
Authentication authenticate(Authentication authentication);
```
**인증 전** `Authentication`을 받아서, 성공하면 **인증 후** `Authentication`을 돌려주고, 실패하면 `AuthenticationException`을 던진다.

기본 구현체 `ProviderManager`는 직접 인증하지 않는다. 등록된 `AuthenticationProvider` 목록을 차례로 보면서 이 종류의 인증을 처리할 수 있는 Provider에게 맡긴다.

### 5. `AuthenticationProvider` - 실제 인증 처리
인증 방식마다 Provider가 따로 있다. 아이디와 비밀번호를 DB 사용자 정보와 비교하는 Provider는 **`DaoAuthenticationProvider`**다.

> **DAO란?**
> Data Access Object. 데이터 저장소에서 데이터를 꺼내 오는 객체를 말한다. `DaoAuthenticationProvider`는 "저장소에서 사용자 정보를 꺼내 와서 인증하는 Provider"라는 뜻이다.

`DaoAuthenticationProvider`가 하는 일:
1. `UserDetailsService.loadUserByUsername(아이디)`로 사용자 정보를 가져온다.
2. 계정 상태를 확인한다: 잠겼는가, 비활성인가, 만료됐는가
3. `PasswordEncoder.matches(입력한 비밀번호, 저장된 해시)`로 비밀번호를 비교한다.
4. 비밀번호 만료 여부를 확인한다.
5. 모두 통과하면 인증 후 `Authentication`을 만들어 돌려준다.

### 6. `UserDetailsService` - 사용자 정보를 찾는 방법
```java
UserDetails loadUserByUsername(String username);
```
Spring Security는 사용자 정보가 MySQL에 있는지, 어떤 테이블에 있는지, 아이디가 이메일인지 알 수 없다. 그래서 **사용자를 찾는 방법은 개발자가 이 인터페이스를 구현해서 알려 준다.** Spring Security가 만든 기능과 개발자가 만든 코드가 만나는 지점이다.

사용자를 찾지 못하면 `UsernameNotFoundException`을 던지는 것이 규칙이다.

### 7. `UserDetails` - Spring Security가 이해하는 사용자 형식
| 메서드 | 의미 |
|---|---|
| `getUsername()` | 아이디 |
| `getPassword()` | 해시된 비밀번호 |
| `getAuthorities()` | 권한 목록 |
| `isAccountNonExpired()` | 계정이 만료되지 않았는가 |
| `isAccountNonLocked()` | 계정이 잠기지 않았는가 |
| `isCredentialsNonExpired()` | 비밀번호가 만료되지 않았는가 |
| `isEnabled()` | 계정이 활성화되어 있는가 |

개발자의 회원 클래스가 이 인터페이스를 구현하면, Spring Security가 그 회원 객체를 그대로 사용자로 다룰 수 있다.

### 8. `GrantedAuthority` - 권한
권한 하나를 나타낸다. 문자열 하나를 감싼 `SimpleGrantedAuthority("ROLE_ADMIN")`을 주로 쓴다.

### 9. `PasswordEncoder` - 비밀번호 해시
| 메서드 | 쓰는 때 | 하는 일 |
|---|---|---|
| `encode(원문)` | 회원가입 | 원문을 해시해서 돌려준다. |
| `matches(원문, 해시)` | 로그인 | 원문을 같은 방식으로 해시해서 저장된 해시와 같은지 비교한다. |

`BCryptPasswordEncoder`가 대표 구현체다. 기본 비용(cost)은 10이다.

## 구성 요소 사이의 관계
로그인 요청 하나가 들어왔을 때 호출 순서는 다음과 같다.

```
로그인 필터 (UsernamePasswordAuthenticationFilter)
  │ 1. 요청에서 아이디/비밀번호를 꺼내 인증 전 Authentication 생성
  ▼
AuthenticationManager (ProviderManager)
  │ 2. 처리할 수 있는 Provider를 찾아 위임
  ▼
AuthenticationProvider (DaoAuthenticationProvider)
  │ 3. 사용자 조회 ──────────────▶ UserDetailsService ──▶ DB
  │                       ◀────── UserDetails
  │ 4. 비밀번호 비교 ────────────▶ PasswordEncoder.matches()
  │ 5. 인증 후 Authentication 생성
  ▼
로그인 필터
  │ 6. SecurityContextHolder에 저장 (현재 요청용)
  │ 7. SecurityContextRepository로 세션에 저장 (다음 요청용)
  ▼
성공 처리 (리다이렉트)
```

| 요소 | 누가 만드나 |
|---|---|
| 로그인 필터, `ProviderManager`, `DaoAuthenticationProvider`, `SecurityContextHolder`, `SecurityContextRepository` | Spring Security가 제공 |
| `UserDetailsService` 구현, `UserDetails` 구현 | **개발자가 작성** |
| `PasswordEncoder` | Spring Security가 제공, **개발자가 빈으로 등록** |
| 접근 규칙, 로그인 페이지 주소 등 | **개발자가 `SecurityFilterChain`으로 설정** |

`UserDetailsService` 빈과 `PasswordEncoder` 빈이 하나씩 등록되어 있으면, Spring Security가 이 둘을 사용하는 `DaoAuthenticationProvider`를 자동으로 만든다. 개발자가 Provider를 직접 만들지 않아도 된다.

## 이 구성 요소들은 어디서 호출되나
위 그림의 맨 위에 있는 "로그인 필터"가 출발점이다. Spring Security의 모든 기능은 **필터**에서 시작된다. 로그인 처리, 세션에서 인증 정보 꺼내기, 접근 규칙 검사, 로그아웃이 모두 각각의 필터가 하는 일이다.

필터가 무엇이고 어떤 순서로 실행되는지는 [필터 기반 동작 원리](./03_filter_architecture.md)에서 본다.
