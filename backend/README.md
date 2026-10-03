# Blog Backend

블로그 서비스의 백엔드 서버입니다. Spring Boot로 만든 REST API와, 동작 확인용 임시 화면(Thymeleaf)을 함께 제공합니다.
**Google OAuth 2.0 로그인**으로 사용자를 인증하고, 로그인 후에는 서버가 직접 발급한 **JWT**(액세스 토큰, 리프레시 토큰)로 API 요청을 인증합니다.
프로젝트 전체 소개는 [루트 README](../README.md)를 참고해 주세요.

## 목차
1. [기술 스택](#기술-스택)
2. [아키텍처](#아키텍처)
3. [프로젝트 구조](#프로젝트-구조)
4. [ERD](#erd)
5. [API 명세](#api-명세)
6. [인증과 인가 설계](#인증과-인가-설계)
7. [동작 흐름](#동작-흐름)
8. [구현 과정](#구현-과정)
9. [로컬 실행 방법](#로컬-실행-방법)
10. [테스트](#테스트)
11. [환경 설정 (예정)](#환경-설정-예정)
12. [배포 (예정)](#배포-예정)
13. [트러블슈팅](#트러블슈팅)
14. [학습 기록](#학습-기록)
15. [개선 예정](#개선-예정)

## 기술 스택
| 구분 | 기술 | 용도 | 상태 |
|---|---|---|---|
| Language | Java 25 | | 사용 중 |
| Framework | Spring Boot 4.0.1, Spring Web MVC | REST API, 화면 컨트롤러 | 사용 중 |
| ORM | Spring Data JPA, Hibernate | 엔티티 매핑, 리포지토리, JPA Auditing(작성/수정 시각) | 사용 중 |
| Database | H2 (인메모리) | 로컬 실행, 테스트 | 사용 중 |
| 보안 | Spring Security 7.0 | 필터 체인, 인가 규칙 | 사용 중 |
| 인증 | Spring Security OAuth2 Client | Google 소셜 로그인 (권한 부여 코드 방식) | 사용 중 |
| 인증 | JWT (jjwt 0.9.1, HS256) | 액세스 토큰, 리프레시 토큰 | 사용 중 |
| View | Thymeleaf, Bootstrap, Vanilla JS | 동작 확인용 임시 화면 | 사용 중 (별도 프론트엔드로 교체 예정) |
| Test | JUnit 5, AssertJ, MockMvc, Spring Security Test | 단위, 통합 테스트 | 사용 중 |
| 기타 | Lombok | 반복 코드 생성 | 사용 중 |
| Database | Amazon RDS | 운영 DB | 예정 |
| 파일 저장소 | Amazon S3 | 이미지 업로드 | 예정 |
| 배포 | AWS Elastic Beanstalk | | 예정 |
| CI/CD | GitHub Actions | | 예정 |

## 아키텍처
```
                    ┌──────────── Google ────────────┐
                    │ accounts.google.com (인가 서버)  │
                    │ googleapis.com (토큰, 사용자 정보)│
                    └───────▲─────────────────▲──────┘
                 ② 로그인/동의 │                 │ ③ 코드 → 토큰 교환, 사용자 정보 조회
                            │                 │   (서버 ↔ 서버)
브라우저 ── ① 로그인 요청 ────┼──▶ ┌───────────┴──────────────── Spring Boot ─────────────────┐
  │                         │    │ Security Filter Chain                                   │
  │ ④ JWT 발급              │    │   TokenAuthenticationFilter (JWT 검증)                   │
  │◀────────────────────────┼────│   OAuth2 Login Filters (Google 로그인)                   │
  │                         │    │   AuthorizationFilter (/api/** 인증 필요)                 │
  │ ⑤ Authorization:        │    │        ▼                                                 │
  │   Bearer <JWT>          │    │ Controller → Service → Repository ──▶ H2 (예정: RDS)     │
  └────────────────────────▶│    │ (View: Thymeleaf)                                        │
                                 └──────────────────────────────────────────────────────────┘
```

## 프로젝트 구조
```
src/main/java/kr/antimony
├── BlogApplication.java                 # 시작 클래스 (@EnableJpaAuditing)
├── config/
│   ├── WebOAuthSecurityConfig.java      # 보안 설정: OAuth2 로그인, JWT 필터, STATELESS, 인가 규칙
│   ├── TokenAuthenticationFilter.java   # Authorization: Bearer 헤더의 JWT 검증 → SecurityContext 설정
│   ├── WebSecurityConfig.java           # (이전) 폼 로그인 + 세션 설정. 현재 주석 처리됨
│   ├── jwt/
│   │   ├── JwtProperties.java           # application.yml의 jwt.issuer, jwt.secret_key 바인딩
│   │   └── TokenProvider.java           # JWT 생성, 검증, Authentication 생성
│   └── oauth/
│       ├── OAuth2UserCustomService.java # Google 사용자 정보로 회원 저장/갱신
│       ├── OAuth2SuccessHandler.java    # 로그인 성공 시 JWT 발급, 리프레시 토큰 저장
│       └── OAuth2AuthorizationRequestBasedOnCookieRepository.java  # OAuth2 요청 정보를 쿠키에 저장
├── controller/
│   ├── BlogApiController.java           # 글 REST API
│   ├── TokenApiController.java          # 액세스 토큰 재발급 API
│   ├── BlogViewController.java          # 글 화면 (Thymeleaf)
│   ├── UserViewController.java          # 로그인, 회원가입 화면
│   └── UserApiController.java           # (이전) 폼 회원가입, 세션 로그아웃
├── service/                             # BlogService, UserService, TokenService, RefreshTokenService, UserDetailService
├── repository/                          # BlogRepository, UserRepository, RefreshTokenRepository
├── domain/                              # Article, User, RefreshToken
├── dto/                                 # 요청/응답 DTO
├── exception/                           # API 예외, 공통 에러 응답, GlobalExceptionHandler
└── util/CookieUtil.java                 # 쿠키 추가/삭제, 직렬화

src/main/resources
├── application.yml                      # Git 제외 (로컬 실행 방법 참고)
├── import.sql                           # 초기 데이터
├── templates/                           # articleList, article, newArticle, oauthLogin (+ 이전 login, signup)
└── static/js/                           # article.js (API 호출, 토큰 재발급), token.js (토큰 저장)
```

## ERD
```
users
├── id          BIGINT   PK, 자동 증가
├── uid         VARCHAR  아이디·비밀번호 로그인의 아이디 (회원가입 정리 후 NOT NULL, UNIQUE 예정)
├── email       VARCHAR  NOT NULL, UNIQUE
├── password    VARCHAR  (OAuth 회원은 NULL)
└── nickname    VARCHAR  NOT NULL, UNIQUE   ← OAuth 가입 시 제공자의 이름으로 자동 설정

article
├── id          BIGINT    PK, 자동 증가
├── title       VARCHAR   NOT NULL
├── content     VARCHAR   NOT NULL
├── author      VARCHAR   NOT NULL   ← 작성자 이메일
├── created_at  TIMESTAMP            (JPA Auditing)
└── updated_at  TIMESTAMP            (JPA Auditing)

refresh_token
├── id             BIGINT   PK, 자동 증가
├── user_id        BIGINT   NOT NULL, UNIQUE   ← users.id (회원당 1개)
└── refresh_token  VARCHAR  NOT NULL
```
- `article.author`는 `users`와 외래 키로 연결하지 않고 작성자의 이메일 문자열을 저장합니다. 작성자 확인은 로그인 사용자의 이메일과 비교해서 합니다.
- `refresh_token.user_id`도 외래 키 없이 회원 id만 저장합니다.

## API 명세

### REST API
| 메서드 | URL | 설명 | 인증 | 성공 응답 |
|---|---|---|---|---|
| `POST` | `/api/articles` | 글 작성. 작성자는 로그인 사용자 | 필요 | 201 Created, 글 |
| `GET` | `/api/articles` | 글 목록 조회 (최신순) | 필요 | 200 OK, 글 배열 |
| `GET` | `/api/articles/{id}` | 글 상세 조회 | 필요 | 200 OK, 글 |
| `PUT` | `/api/articles/{id}` | 글 수정. **작성자만** | 필요 | 200 OK, 글 |
| `DELETE` | `/api/articles/{id}` | 글 삭제. **작성자만** | 필요 | 204 No Content |
| `POST` | `/api/token` | 리프레시 토큰으로 액세스 토큰 재발급 | 불필요 | 201 Created, `{accessToken}` |

**글 응답 형식** (작성, 조회, 수정 모두 같음)
```json
{
  "id": 4,
  "title": "제목",
  "content": "내용",
  "author": "a@gmail.com",
  "createdAt": "2026-10-03T14:30:00.123456",
  "updatedAt": "2026-10-03T14:30:00.123456"
}
```

**입력값 규칙** (작성, 수정)
| 필드 | 규칙 |
|---|---|
| `title` | 필수, 공백만은 안 됨, 255자 이하 |
| `content` | 필수, 공백만은 안 됨 |

### 에러 응답
모든 REST API의 에러는 같은 형식으로 응답합니다.
```json
{
  "message": "입력값을 확인해 주세요.",
  "errors": { "title": "제목을 입력해 주세요." }
}
```
`errors`는 입력값 검증 실패일 때 필드별 메시지를 담고, 그 밖에는 빈 객체(`{}`)입니다.

| 상태 코드 | 상황 | `message` 예시 |
|---|---|---|
| `400 Bad Request` | 입력값 검증 실패 | `입력값을 확인해 주세요.` (+ `errors`) |
| `400 Bad Request` | JSON 형식 오류 | `요청 본문을 읽을 수 없습니다. JSON 형식을 확인해 주세요.` |
| `400 Bad Request` | 경로 값 형식 오류 (`/api/articles/abc`) | `잘못된 요청 값입니다: id` |
| `401 Unauthorized` | 액세스 토큰 없음, 만료, 위조 | (본문 없음) |
| `403 Forbidden` | 작성자가 아닌 사용자의 수정, 삭제 | `글 작성자만 수정하거나 삭제할 수 있습니다.` |
| `404 Not Found` | 없는 글 | `글을 찾을 수 없습니다. (id: 999)` |

에러 처리는 `exception/GlobalExceptionHandler`(`@RestControllerAdvice`)가 한곳에서 담당합니다.

### 요청, 응답 예시
**글 작성**
```http
POST /api/articles HTTP/1.1
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{"title": "제목", "content": "내용"}
```
```http
HTTP/1.1 201 
Content-Type: application/json

{"id": 4, "title": "제목", "content": "내용", "author": "a@gmail.com", "createdAt": "2026-10-03T14:30:00.123456", "updatedAt": "2026-10-03T14:30:00.123456"}
```

**액세스 토큰 재발급**
```http
POST /api/token HTTP/1.1
Content-Type: application/json

{"refreshToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."}
```
```http
HTTP/1.1 201 
Content-Type: application/json

{"accessToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."}
```

### OAuth2 로그인 (Spring Security 제공)
| 메서드 | URL | 설명 |
|---|---|---|
| `GET` | `/oauth2/authorization/google` | Google 로그인 시작. Google 권한 요청 화면으로 302 |
| `GET` | `/login/oauth2/code/google` | Google이 권한 부여 코드를 보내는 리디렉션 URI. 토큰 교환, 회원 저장, JWT 발급 후 `/articles`로 302 |

### 화면 (Thymeleaf, 임시)
| URL | 화면 | 비고 |
|---|---|---|
| `GET /login` | `oauthLogin.html` | Google 로그인 버튼 |
| `GET /articles` | `articleList.html` | 글 목록. URL의 `token` 파라미터를 `localStorage`에 저장 |
| `GET /articles/{id}` | `article.html` | 글 상세. 작성자, 작성 시각 표시 |
| `GET /new-article` | `newArticle.html` | 새 글 작성 |
| `GET /new-article?id={id}` | `newArticle.html` | 기존 글 수정 |
| `GET /signup`, `POST /user`, `GET /logout` | `signup.html` 등 | 이전 폼 로그인 방식에서 남은 기능 ([트러블슈팅 5](#5-로그아웃해도-토큰이-남아-api를-계속-사용할-수-있음)) |

화면 요청은 누구나 접근할 수 있고, 화면 안의 JavaScript가 액세스 토큰을 붙여 REST API를 호출합니다.

## 인증과 인가 설계

### 보안 설정 요약 (`WebOAuthSecurityConfig`)
| 항목 | 설정 | 이유 |
|---|---|---|
| 세션 | `STATELESS` | 인증 상태를 서버에 저장하지 않고 JWT로만 판단 |
| CSRF | 비활성화 | 토큰을 `Authorization` 헤더로 보내므로 브라우저 자동 전송을 악용하는 CSRF의 대상이 아님 |
| 폼 로그인, HTTP Basic, 로그아웃 필터 | 비활성화 | 로그인은 OAuth2, 인증은 JWT로 대체 |
| JWT 필터 | `addFilterBefore(TokenAuthenticationFilter, UsernamePasswordAuthenticationFilter)` | 인가 검사 전에 토큰으로 인증 정보 설정 |
| 인가 규칙 | `/api/token` 허용 → `/api/**` 인증 필요 → 나머지 허용 | 데이터 API만 보호, 화면은 공개 |
| 인증 실패 | `/api/`로 시작하면 `401` (`HttpStatusEntryPoint`) | API 클라이언트에는 리다이렉트 대신 상태 코드 |
| 보안 필터 제외 | H2 콘솔, `/img/**`, `/css/**`, `/js/**` | 정적 파일, 개발 도구 |
| OAuth2 요청 저장 | 쿠키 (`OAuth2AuthorizationRequestBasedOnCookieRepository`) | `STATELESS`라 세션에 저장할 수 없음 |

### 보안 필터 체인
```
요청
 ├ SecurityContextHolderFilter
 ├ HeaderWriterFilter
 ├ OAuth2AuthorizationRequestRedirectFilter   /oauth2/authorization/google → Google로 302
 ├ OAuth2LoginAuthenticationFilter            /login/oauth2/code/google → 토큰 교환, 로그인 처리
 ├ ★ TokenAuthenticationFilter                Bearer 토큰 검증 → SecurityContext에 Authentication
 │                                             (addFilterBefore로 UsernamePasswordAuthenticationFilter 자리 바로 앞)
 ├ AnonymousAuthenticationFilter               인증 정보 없으면 익명 사용자
 ├ ExceptionTranslationFilter                  /api/** 인증 실패 → 401
 └ AuthorizationFilter                         인가 규칙 검사
 ▼
DispatcherServlet → Controller
```
(흐름과 관계가 적은 필터는 생략)

### JWT 설계
| 항목 | 액세스 토큰 | 리프레시 토큰 |
|---|---|---|
| 형식 | JWT (HS256) | JWT (HS256) |
| 클레임 | `iss`, `iat`, `exp`, `sub`(이메일), `id`(회원 id) | 같음 |
| 유효 시간 | 로그인 시 1일, 재발급 시 2시간 ([트러블슈팅 10](#10-액세스-토큰-유효-시간이-발급-경로마다-다름)) | 14일 |
| 클라이언트 보관 | `localStorage` (`access_token`) | 쿠키 (`refresh_token`, `Path=/`) |
| 서버 보관 | 없음 | `refresh_token` 테이블 (회원당 1개) |
| 용도 | `/api/**` 요청 인증 | `/api/token`에서 액세스 토큰 재발급 |

토큰 검증(`TokenProvider.validToken`)은 서명과 만료 시각을 확인합니다. 검증된 토큰의 `sub`로 `Authentication`(권한 `ROLE_USER`)을 만들며, DB는 조회하지 않습니다.

### 인가: 작성자 확인
URL 단위 인가(`/api/**` 인증 필요)에 더해, 글 수정과 삭제는 서비스 계층에서 **데이터 단위 인가**를 합니다.
```java
private static void authorizeArticleAuthor(Article article) {
    String userName = SecurityContextHolder.getContext().getAuthentication().getName();
    if (!article.getAuthor().equals(userName)) {
        throw new IllegalArgumentException("not authorized");
    }
}
```

## 동작 흐름

### 1. 요청 처리 계층
```
Controller (요청/응답 변환, DTO)
   ▼
Service (비즈니스 로직, 트랜잭션, 작성자 확인)
   ▼
Repository (Spring Data JPA)
   ▼
H2 Database
```

### 2. 로그인 흐름 (Google OAuth2 → JWT 발급)
```
브라우저                       블로그 서버                               Google
   │ GET /login                   │                                      │
   │─────────────────────────────▶│ oauthLogin.html                      │
   │ Google 버튼 클릭              │                                      │
   │ GET /oauth2/authorization/google                                    │
   │─────────────────────────────▶│ 권한 요청 생성(state 포함),            │
   │                              │ oauth2_auth_request 쿠키에 저장       │
   │◀── 302 accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=...&scope=email profile&state=...&redirect_uri=...
   │──────────────────────────────────────────────────────────────────▶│
   │                         Google 로그인, 동의                          │
   │◀── 302 /login/oauth2/code/google?code=...&state=... ───────────────│
   │─────────────────────────────▶│ ① 쿠키의 state와 비교                 │
   │                              │ ② POST /token (code + Client Secret)│
   │                              │─────────────────────────────────────▶│
   │                              │◀──── Google access_token ───────────│
   │                              │ ③ GET /userinfo (Bearer)             │
   │                              │─────────────────────────────────────▶│
   │                              │◀──── {sub, email, name} ────────────│
   │                              │ ④ OAuth2UserCustomService            │
   │                              │    users 테이블 저장/닉네임 갱신        │
   │                              │ ⑤ OAuth2SuccessHandler               │
   │                              │    리프레시 토큰(14일) 생성 → DB 저장   │
   │                              │    액세스 토큰(1일) 생성               │
   │◀── 302 /articles?token=<액세스 토큰>                                 │
   │    Set-Cookie: refresh_token=...; Max-Age=1209600; Path=/          │
   │    Set-Cookie: oauth2_auth_request=; Max-Age=0                     │
   │ GET /articles?token=...      │                                      │
   │─────────────────────────────▶│ articleList.html                     │
   │ token.js: localStorage.access_token 저장                            │
```
①~③은 Spring Security OAuth2 Client가 처리하고, ④⑤는 직접 구현한 부분입니다.

### 3. 인증 흐름 (JWT로 API 호출)
```
article.js: httpRequest('POST', '/api/articles', body)
   │ Authorization: Bearer <localStorage.access_token>
   ▼
TokenAuthenticationFilter
   ├ "Bearer " 뒤의 토큰 추출
   ├ TokenProvider.validToken(): 서명, 만료 확인
   └ 유효하면 TokenProvider.getAuthentication() → SecurityContextHolder에 저장
   ▼
AuthorizationFilter: /api/** authenticated → 인증됨 → 허용
   ▼                                      (토큰 없음/무효 → 401)
BlogApiController.addArticle(request, Principal)
   ▼ principal.getName() = 토큰의 sub(이메일)
BlogService.save() → article.author = 이메일
```

### 4. 토큰 재발급 흐름
```
API 응답 401
   ▼
article.js: refresh_token 쿠키가 있으면
   POST /api/token {"refreshToken": "..."}
   ▼
TokenService.createNewAccessToken()
   ├ TokenProvider.validToken(refreshToken)        서명, 만료
   ├ RefreshTokenService.findByRefreshToken()      DB에 저장된 토큰인가
   ├ UserService.findById(userId)                  회원 조회
   └ TokenProvider.generateToken(user, 2시간)       새 액세스 토큰
   ▼
201 {"accessToken": "..."}
   ▼
article.js: localStorage 갱신 → 실패했던 요청 재시도
```

### 5. 글 수정, 삭제 흐름
```
PUT /api/articles/{id}  (Bearer 토큰)
  → TokenAuthenticationFilter → AuthorizationFilter
  → BlogService.update()
      ├ 글 조회 (없으면 예외)
      ├ authorizeArticleAuthor(): 작성자 이메일 == 로그인 사용자 이메일?
      │     다르면 예외 (트러블슈팅 8)
      └ article.update() → 변경 감지로 UPDATE, updatedAt 자동 갱신
```

### 6. 로그아웃 흐름 (현재 상태)
```
글 목록의 로그아웃 버튼 → GET /logout
  → UserApiController.logout(): 세션 로그아웃 후 /login으로 이동
  → localStorage의 access_token, refresh_token 쿠키는 남아 있음
```
토큰 기반 로그아웃(리프레시 토큰 DB 삭제 + 클라이언트 토큰 삭제)은 아직 구현되지 않았습니다. [트러블슈팅 5](#5-로그아웃해도-토큰이-남아-api를-계속-사용할-수-있음)

## 구현 과정
인증 방식을 단계적으로 바꿔 가며 구현했습니다.

| 단계 | 커밋 | 인증 방식 | 내용 |
|---|---|---|---|
| 1 | `08f359d` | 없음 | 글 CRUD REST API, JPA, H2 |
| 2 | `072b1d3` | 없음 | Thymeleaf 임시 화면, JPA Auditing(작성/수정 시각) |
| 3 | `9ef1b99` | 폼 로그인 + 세션 | Spring Security, 회원가입, BCrypt, `UserDetailsService` |
| 4 | `77e2349` | (준비) JWT | `TokenProvider`, `TokenAuthenticationFilter`, 리프레시 토큰, 재발급 API |
| 5 | (작업 중) | **Google OAuth2 + JWT, STATELESS** | OAuth2 로그인, 로그인 성공 시 JWT 발급, 글 작성자와 작성자 확인 |

## 로컬 실행 방법

### 필요 환경
- JDK 25
- Google Cloud Console에서 발급한 OAuth 2.0 클라이언트 (웹 애플리케이션)
  - 승인된 리디렉션 URI: `http://localhost:8080/login/oauth2/code/google`

### 설정 파일
`src/main/resources/application.yml`은 비밀 값이 들어 있어 Git에서 제외되어 있습니다. 아래 내용으로 직접 만들어 주세요.
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
    defer-datasource-initialization: true
  datasource:
    url: jdbc:h2:mem:testdb
    username: sa
  h2:
    console:
      enabled: true
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: <Google Client ID>
            client-secret: <Google Client Secret>
            scope:
              - email
              - profile

jwt:
  issuer: <발급자 문자열>
  secret_key: <Base64로 인코딩한 32바이트 이상의 난수>   # 예: openssl rand -base64 32
```

### 실행
```bash
./gradlew bootRun
```
- 로그인: `http://localhost:8080/login`
- 글 목록: `http://localhost:8080/articles`
- H2 콘솔: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:testdb`
  - User Name: `sa`
  - Password: (비워 둠)

## 테스트
```bash
./gradlew test
```
| 테스트 클래스 | 대상 | 내용 |
|---|---|---|
| `BlogApiControllerTest` | 글 API | 작성, 목록, 상세, 수정, 삭제와 응답 형식. 없는 글 404, 작성자가 아닌 사용자의 수정·삭제 403, 입력값 검증 400. `SecurityContextHolder`에 테스트 사용자를 넣어 작성자 확인까지 검증 |
| `TokenProviderTest` | JWT | 토큰 생성, 만료 토큰 거부, 유효 토큰 통과, `Authentication` 생성, `id` 클레임 추출 |
| `TokenApiControllerTest` | 토큰 재발급 API | 저장된 리프레시 토큰으로 새 액세스 토큰 발급 |

`JwtFactory`는 테스트에서 필요한 클레임만 바꿔 토큰을 만드는 도구입니다.

## 환경 설정 (예정)
운영 환경에서 필요한 환경 변수입니다. 값은 Elastic Beanstalk 환경 속성에 설정하고 저장소에 올리지 않습니다.

| 환경 변수 | 용도 |
|---|---|
| `GOOGLE_CLIENT_ID` | Google OAuth 2.0 Client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 2.0 Client Secret |
| `JWT_ISSUER` | JWT `iss` 클레임 |
| `JWT_SECRET_KEY` | JWT 서명 키 (HS256, 32바이트 이상) |
| `SPRING_DATASOURCE_URL` | RDS 접속 주소 |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | RDS 계정 |

## 배포 (예정)
| 구성 요소 | 역할 |
|---|---|
| Elastic Beanstalk | Spring Boot JAR 실행, 서버 관리 |
| RDS | 운영 DB. 보안 그룹으로 Elastic Beanstalk에서 오는 접속만 허용 |
| S3 | 이미지 저장. Elastic Beanstalk 인스턴스에 IAM 역할을 부여해 접근 |
| GitHub Actions | `main` 브랜치에 push하면 테스트, 빌드 후 Elastic Beanstalk에 배포 |

## 트러블슈팅
개발하면서 겪은 문제와 해결 과정을 기록합니다.

### 1. 글 조회 응답에 id가 없음
- **문제**: 글 목록과 상세 조회 응답에 `title`, `content`만 있고 `id`가 없다. 클라이언트가 목록에서 글을 골라 상세 조회, 수정, 삭제 요청을 보낼 수 없다.
- **원인**: 조회 응답 DTO `ArticleResponse`에 `title`, `content` 필드만 정의되어 있었다.
- **해결**: `ArticleResponse`에 `id`, `author`, `createdAt`, `updatedAt`을 추가했다. 테스트에서 응답의 `id`, `author`를 확인한다.

### 2. API마다 응답 형식이 다름
- **문제**: 글 작성(`POST`)과 수정(`PUT`)은 엔티티를 그대로 응답하고, 조회(`GET`)는 DTO로 응답한다.
- **원인**: `BlogApiController`의 작성, 수정 메서드가 `ResponseEntity<Article>`로 엔티티를 그대로 반환했다. 엔티티를 응답하면 엔티티 필드를 바꿀 때마다 API 응답도 함께 바뀌고, 응답에 넣을 필드를 따로 고를 수 없다.
- **해결**: 작성, 목록, 상세, 수정이 모두 `ArticleResponse`를 반환하도록 통일했다. 삭제는 돌려줄 내용이 없으므로 `204 No Content`로 응답한다.

### 3. 없는 글을 조회하거나 수정하면 500 에러 발생
- **문제**: 존재하지 않는 id로 조회, 수정 요청을 보내면 404 Not Found가 아니라 500 Internal Server Error가 응답된다.
- **원인**: `BlogService`가 글을 찾지 못하면 `IllegalArgumentException`을 던졌는데, 이 예외를 HTTP 상태 코드로 바꾸는 처리가 없었다. 처리되지 않은 예외는 Spring이 500으로 응답한다.
- **해결**: `ApiException`(상태 코드를 가진 예외)을 상속한 `ArticleNotFoundException`(404)을 만들고, `@RestControllerAdvice`인 `GlobalExceptionHandler`가 `{"message", "errors"}` 형식의 에러 응답으로 바꾸도록 했다. 입력값 검증 실패, JSON 형식 오류, 경로 값 형식 오류는 400으로 처리한다.

### 4. 없는 글을 삭제해도 200 OK 응답
- **문제**: 존재하지 않는 id로 삭제 요청을 보내도 200 OK가 응답된다.
- **원인**: 처음에는 `blogRepository.deleteById(id)`를 바로 호출했다. Spring Data JPA 3.0부터 `deleteById`는 대상이 없으면 예외 없이 아무것도 하지 않으므로, 컨트롤러는 항상 200을 응답했다. 이후 작성자 확인을 넣으면서 글을 먼저 조회하게 되었지만, 그때는 3번과 같은 이유로 500이 응답되었다.
- **해결**: 삭제도 `findById()`로 글을 먼저 조회해서, 없으면 `ArticleNotFoundException`(404)을 던진다. 성공하면 `204 No Content`로 응답한다.

### 5. 로그아웃해도 토큰이 남아 API를 계속 사용할 수 있음
- **문제**: 글 목록의 로그아웃 버튼을 누르면 `/login`으로 이동하지만, 다시 글 목록으로 가면 글 등록, 수정, 삭제가 그대로 된다.
- **원인**: 로그아웃 버튼이 `location.href='/logout'`으로 이동하고, `UserApiController.logout()`은 세션 기반 로그아웃(`SecurityContextLogoutHandler`)만 수행한다. 인증 정보는 세션이 아니라 `localStorage`의 `access_token`과 `refresh_token` 쿠키에 있으므로 지워지지 않는다. `article.js`에 토큰을 지우는 로그아웃 코드(`logout-btn`)가 있지만, 화면에 `id="logout-btn"`인 요소가 없고 호출 대상인 `DELETE /api/refresh-token` API도 없다.
- **해결**:

### 6. OAuth2 요청 쿠키를 Java 역직렬화함
- **문제**: `CookieUtil.deserialize()`가 브라우저가 보낸 `oauth2_auth_request` 쿠키 값을 `SerializationUtils.deserialize()`로 Java 객체로 복원한다.
- **원인**: `STATELESS` 설정으로 세션을 쓸 수 없어서 OAuth2 인가 요청 정보를 쿠키에 Java 직렬화해 저장했다. 쿠키는 클라이언트가 조작할 수 있는 값이므로, 신뢰할 수 없는 데이터를 Java 역직렬화하는 취약점(insecure deserialization)에 해당한다. `SerializationUtils.deserialize()`는 Spring 6부터 deprecated다.
- **해결**:

### 7. 액세스 토큰과 리프레시 토큰이 노출되기 쉬움
- **문제**: 로그인 성공 후 액세스 토큰이 `/articles?token=...`처럼 URL로 전달되고, 리프레시 토큰 쿠키에 `HttpOnly`가 없다.
- **원인**: `OAuth2SuccessHandler`가 액세스 토큰을 쿼리 파라미터로 붙여 리다이렉트하므로 브라우저 방문 기록과 서버 접속 로그에 남는다. `CookieUtil.addCookie()`가 `HttpOnly`, `Secure`, `SameSite`를 설정하지 않으며, `article.js`가 `document.cookie`로 리프레시 토큰을 읽는 구조라 XSS가 발생하면 탈취될 수 있다.
- **해결**:

### 8. 다른 사람의 글을 수정하거나 삭제하면 500 에러 발생
- **문제**: 작성자가 아닌 사용자가 글 수정, 삭제 요청을 보내면 403 Forbidden이 아니라 500 Internal Server Error가 응답된다.
- **원인**: `BlogService.authorizeArticleAuthor()`가 작성자가 다르면 `IllegalArgumentException("not authorized")`을 던지고, 이 예외를 HTTP 상태 코드로 바꾸는 처리가 없다.
- **해결**: 작성자가 다르면 `ArticleForbiddenException`(403)을 던지고, `GlobalExceptionHandler`가 `{"message": "글 작성자만 수정하거나 삭제할 수 있습니다.", "errors": {}}`로 응답한다. 수정, 삭제 각각에 대해 403 응답과 데이터가 바뀌지 않는 것을 테스트로 확인한다.

### 9. 이름이 같은 Google 사용자가 로그인하면 실패
- **문제**: 이미 가입한 사용자와 Google 계정 이름이 같은 사용자가 처음 로그인하면 회원 저장에 실패한다.
- **원인**: `User.nickname`이 `unique = true`인데, `OAuth2UserCustomService`가 Google의 `name` 값을 그대로 닉네임으로 저장한다. Google 계정 이름은 서로 겹칠 수 있다.
- **해결**:

### 10. 액세스 토큰 유효 시간이 발급 경로마다 다름
- **문제**: 로그인 직후 받은 액세스 토큰은 1일, 재발급받은 액세스 토큰은 2시간 동안 유효하다.
- **원인**: `OAuth2SuccessHandler.ACCESS_TOKEN_DURATION`은 `Duration.ofDays(1)`이고, `TokenService.createNewAccessToken()`은 `Duration.ofHours(2)`를 따로 지정한다. 유효 시간이 한곳에서 관리되지 않는다.
- **해결**:

## 학습 기록
이 프로젝트를 만들면서 공부한 내용을 [`note/`](../note)에 정리했습니다.

| 주제 | 노트 |
|---|---|
| Spring Boot, Spring 핵심 개념 | [`01_spring`](../note/01_spring) ~ [`07_spring_boot_structure`](../note/07_spring_boot_structure) |
| 테스트 | [`08_test`](../note/08_test) |
| JPA, 영속성 컨텍스트 | [`09_database`](../note/09_database) |
| REST API | [`10_rest_api`](../note/10_rest_api) |
| Thymeleaf 임시 화면, JPA Auditing | [`11_temporary_frontend_thymeleaf`](../note/11_temporary_frontend_thymeleaf) |
| Spring Security, 필터 체인 | [`12_spring_security`](../note/12_spring_security) |
| JWT, 리프레시 토큰 | [`13_jwt`](../note/13_jwt) |
| OAuth 2.0, 쿠키, Client ID/Secret | [`14_oauth`](../note/14_oauth) |

## 개선 예정
- [x] 글 CRUD REST API
- [x] Spring Security 적용
- [x] JWT 액세스 토큰, 리프레시 토큰 발급과 재발급
- [x] Google OAuth 2.0 로그인
- [x] 글 작성자 저장, 작성자만 수정·삭제
- [ ] 토큰 기반 로그아웃 (리프레시 토큰 삭제 API)
- [x] 예외 처리 통일 (404, 403 응답, 공통 에러 응답 형식), 입력값 검증, 응답 DTO 통일
- [ ] 토큰 보관 방식 개선 (`HttpOnly` 쿠키, URL 토큰 전달 제거)
- [ ] 이전 폼 로그인 코드 정리
- [ ] AWS: RDS 연결, S3 이미지 업로드, Elastic Beanstalk 배포
- [ ] CI/CD: GitHub Actions로 테스트, 빌드, 배포 자동화
- [ ] 별도 프론트엔드로 화면 교체
