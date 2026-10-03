# Blog Backend

블로그 서비스의 백엔드 서버입니다. Spring Boot로 만든 REST API를 제공하고, 화면은 [blog frontend](../frontend)(Next.js)가 그립니다.
로그인은 antimony.kr 통합 로그인 서버인 **[accounts](https://github.com/51SB-ANTIMONY/accounts)에 맡깁니다(OpenID Connect).** blog는 accounts에 등록된 OIDC 클라이언트이고, accounts에서 로그인하고 돌아오면 **세션**(`BLOG_SESSION` 쿠키)으로 로그인 상태를 기억합니다. 토큰은 브라우저에 두지 않습니다.
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
| Framework | Spring Boot 4.0.1, Spring Web MVC | REST API | 사용 중 |
| ORM | Spring Data JPA, Hibernate | 엔티티 매핑, 리포지토리, JPA Auditing(작성/수정 시각) | 사용 중 |
| Database | H2 (인메모리) | 로컬 실행, 테스트 | 사용 중 |
| 보안 | Spring Security 7.0 | 필터 체인, 인가 규칙 | 사용 중 |
| 인증 | Spring Security OAuth2 Client | accounts(OIDC 인가 서버)로 로그인 (권한 부여 코드 방식 + PKCE), ID 토큰 검증 | 사용 중 |
| 인증 | 세션, CSRF 토큰 | 로그인 상태 유지 (`BLOG_SESSION`), 쿠키 기반 요청 보호 (`XSRF-TOKEN`) | 사용 중 |
| View | Next.js ([`../frontend`](../frontend)) | 화면. 이 서버 앞에서 요청을 넘겨준다(rewrites) | 사용 중 (Thymeleaf 임시 화면은 삭제) |
| Test | JUnit 5, AssertJ, MockMvc, Spring Security Test | 단위, 통합 테스트 | 사용 중 |
| 기타 | Lombok | 반복 코드 생성 | 사용 중 |
| Database | Amazon RDS | 운영 DB | 예정 |
| 파일 저장소 | Amazon S3 | 이미지 업로드 | 예정 |
| 배포 | AWS Elastic Beanstalk | | 예정 |
| CI/CD | GitHub Actions | | 예정 |

## 아키텍처
```
브라우저 ── localhost:3000  blog frontend (Next.js)       화면 (목록·상세는 서버에서 그림)
              │ rewrites: /api/**, /oauth2/**, /login/oauth2/**, /logout
              ▼
           localhost:8080  blog backend (이 서버)           API, 로그인 처리, 세션(BLOG_SESSION), CSRF
              │ ① 로그인 필요 → accounts로 (브라우저가 이동)
              │ ② code → 토큰 교환, 서명 공개 키 (서버 ↔ 서버)
              ▼
           accounts  localhost:3001 (화면, issuer) / localhost:9000 (Spring)
```
- 브라우저는 3000만 봅니다. Next.js가 위 경로를 이 서버로 넘기므로 화면과 API가 같은 주소이고, 세션 쿠키와 CSRF 쿠키를 그대로 씁니다.
- `server.forward-headers-strategy: framework`로 Next.js가 붙이는 `X-Forwarded-Host`를 읽어, redirect_uri와 리다이렉트 주소를 3000 기준으로 만듭니다.
- 토큰은 이 서버의 세션에만 있고, 브라우저에는 `HttpOnly` 세션 쿠키만 갑니다(BFF).

## 프로젝트 구조
```
src/main/java/kr/antimony/blog
├── BlogApplication.java                 # 시작 클래스 (@EnableJpaAuditing)
├── config/
│   ├── SecurityConfig.java              # 보안 설정: accounts 로그인, 세션, CSRF, 로그아웃, 인가 규칙
│   ├── AccountsClientConfig.java        # blog를 accounts의 OIDC 클라이언트로 등록 (주소, Client ID/Secret, 범위)
│   └── oauth/
│       └── AccountsOidcUserService.java # 로그인 후 ID 토큰의 sub로 blog 회원을 찾거나 만듦
├── controller/
│   ├── BlogApiController.java           # 글 REST API
│   ├── MeApiController.java             # 로그인 상태 (닉네임)
│   └── CsrfApiController.java           # CSRF 토큰 발급
├── service/                             # BlogService, UserService
├── repository/                          # BlogRepository, UserRepository
├── domain/                              # Article, Category, User
├── dto/                                 # 요청/응답 DTO
└── exception/                           # API 예외, 공통 에러 응답, GlobalExceptionHandler

src/main/resources
├── application.yml                      # 공통 설정 (주소·비밀 값은 환경 변수)
├── application-prod.yml                 # 운영 설정
└── import.sql                           # 초기 데이터 (예시 작성자, 예시 글)
```

## ERD
```
users
├── id            BIGINT   PK, 자동 증가
├── accounts_sub  VARCHAR  NOT NULL, UNIQUE   ← accounts가 발급한 ID 토큰의 sub (accounts의 회원 id)
└── nickname      VARCHAR  NOT NULL           ← accounts의 닉네임. 로그인할 때마다 갱신

article
├── id          BIGINT    PK, 자동 증가
├── title       VARCHAR   NOT NULL
├── content     VARCHAR   NOT NULL
├── category    VARCHAR   NOT NULL   ← DAILY(일상), STUDY(학습), TREE(시냇가에 심은 나무)
├── summary     VARCHAR   (300자 이하, 선택)   ← 비우면 목록에 본문 앞부분
├── author_id   BIGINT    NOT NULL, FK → users.id
├── created_at  TIMESTAMP            (JPA Auditing)
└── updated_at  TIMESTAMP            (JPA Auditing)
```
- 아이디, 비밀번호, 이메일 같은 계정 정보는 accounts에만 있습니다. blog는 accounts 회원과 연결할 `sub`와 화면에 보여 줄 닉네임만 저장합니다.
- 같은 사람은 아이디·비밀번호로 로그인하든 Google로 로그인하든 accounts에서 같은 `sub`를 받으므로, blog에서도 같은 회원입니다.

## API 명세

### REST API
| 메서드 | URL | 설명 | 인증 | 성공 응답 |
|---|---|---|---|---|
| `POST` | `/api/articles` | 글 작성. 작성자는 로그인 사용자 | 필요 | 201 Created, 글 |
| `GET` | `/api/articles?category=` | 글 목록 조회 (최신순). `category`(DAILY, STUDY, TREE)로 거를 수 있음 | 불필요 | 200 OK, 글 배열 |
| `GET` | `/api/articles/{id}` | 글 상세 조회 | 불필요 | 200 OK, 글 |
| `PUT` | `/api/articles/{id}` | 글 수정. **작성자만** | 필요 | 200 OK, 글 |
| `DELETE` | `/api/articles/{id}` | 글 삭제. **작성자만** | 필요 | 204 No Content |
| `GET` | `/api/me` | 로그인 상태. `{nickname}` | 필요 (안 했으면 401) | 200 OK |
| `GET` | `/api/csrf` | CSRF 토큰 발급 (`XSRF-TOKEN` 쿠키) | 불필요 | 200 OK, `{token, headerName, parameterName}` |

**글 응답 형식** (작성, 조회, 수정 모두 같음)
```json
{
  "id": 4,
  "title": "제목",
  "content": "내용",
  "category": "STUDY",
  "summary": null,
  "excerpt": "내용",
  "author": "닉네임",
  "editable": true,
  "createdAt": "2026-10-03T14:30:00.123456",
  "updatedAt": "2026-10-03T14:30:00.123456"
}
```
- `summary`: 작성자가 쓴 요약(없으면 `null`). `excerpt`: 목록에 보일 요약(요약이 없으면 본문 앞 120자).
- `editable`: 로그인한 사용자가 작성자인가. 화면이 수정·삭제 버튼을 보여 줄지 정하는 값이고, 실제 권한 검사는 서버가 다시 한다.

**입력값 규칙** (작성, 수정)
| 필드 | 규칙 |
|---|---|
| `title` | 필수, 공백만은 안 됨, 255자 이하 |
| `content` | 필수, 공백만은 안 됨 |
| `category` | 필수. `DAILY`, `STUDY`, `TREE` |
| `summary` | 선택, 300자 이하 |

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
| `401 Unauthorized` | 로그인하지 않음 (세션 없음) | (본문 없음) |
| `403 Forbidden` | CSRF 토큰 없음, 틀림 | (본문 없음) |
| `403 Forbidden` | 작성자가 아닌 사용자의 수정, 삭제 | `글 작성자만 수정하거나 삭제할 수 있습니다.` |
| `404 Not Found` | 없는 글 | `글을 찾을 수 없습니다. (id: 999)` |

에러 처리는 `exception/GlobalExceptionHandler`(`@RestControllerAdvice`)가 한곳에서 담당합니다.

### 요청, 응답 예시
**글 작성**
```http
POST /api/articles HTTP/1.1
Cookie: BLOG_SESSION=...; XSRF-TOKEN=6f2b...
X-XSRF-TOKEN: 6f2b...
Content-Type: application/json

{"title": "제목", "content": "내용", "category": "STUDY", "summary": ""}
```
```http
HTTP/1.1 201 
Content-Type: application/json

{"id": 4, "title": "제목", "content": "내용", "category": "STUDY", "summary": null, "excerpt": "내용", "author": "닉네임", "editable": true, "createdAt": "2026-10-03T14:30:00.123456", "updatedAt": "2026-10-03T14:30:00.123456"}
```

### 로그인, 로그아웃 (Spring Security 제공)
| 메서드 | URL | 설명 |
|---|---|---|
| `GET` | `/oauth2/authorization/accounts` | 로그인 시작. accounts의 `/oauth2/authorize`로 302 (`state`, `nonce`, PKCE 포함) |
| `GET` | `/login/oauth2/code/accounts` | accounts가 code를 보내는 리디렉션 URI(`http://localhost:3000/...`). 토큰 교환, ID 토큰 검증, 회원 저장 후 화면의 첫 페이지(`/`)로 302 |
| `POST` | `/logout` | blog 세션 삭제 후 accounts의 `/connect/logout`으로 302. accounts 로그인도 끝나고 `http://localhost:3000/`으로 돌아옴 |

### 화면
화면은 [blog frontend](../frontend)가 그립니다. 홈(`/`, `/?category=daily`), 글 상세(`/articles/{id}`), 글 쓰기(`/write`), 글 수정(`/articles/{id}/edit`).

## 인증과 인가 설계

### 보안 설정 요약 (`SecurityConfig`)
| 항목 | 설정 | 이유 |
|---|---|---|
| 로그인 | `oauth2Login()`, 등록된 제공자는 accounts 하나 | 로그인 화면, 비밀번호 확인, 소셜 로그인은 accounts가 담당 |
| 로그인 후 | `AccountsOidcUserService`가 `sub`로 회원 저장, 닉네임 갱신 | blog 회원과 accounts 회원 연결 |
| 세션 | 기본값 (필요할 때 생성), 쿠키 이름 `BLOG_SESSION` | 같은 localhost의 accounts 세션 쿠키(`ACCOUNTS_SESSION`)와 구별 |
| CSRF | `csrf.spa()`: `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더 또는 `_csrf` 폼 값 | 세션 쿠키는 브라우저가 자동으로 보내므로 다른 사이트가 보낸 요청을 막아야 함 |
| 인가 규칙 | `/api/csrf`, 글 읽기(`GET /api/articles`, `GET /api/articles/*`) 허용 → 나머지 `/api/**` 인증 필요 | 읽기는 누구나, 쓰기·수정·삭제는 로그인 |
| 인증 실패 | `/api/**`는 `401` | 화면(Next.js)이 401을 받으면 accounts 로그인으로 보낸다 |
| 로그아웃 | `OidcClientInitiatedLogoutSuccessHandler` | blog 세션과 함께 accounts 로그인도 끝냄 |

### accounts 등록 정보 (`AccountsClientConfig`)
| 항목 | 값 |
|---|---|
| registrationId | `accounts` |
| client_id / 인증 방식 | `blog` / `client_secret_basic` |
| 범위 | `openid`, `profile`, `email` |
| redirect_uri | `{baseUrl}/login/oauth2/code/accounts` |
| issuer (브라우저가 가는 주소, ID 토큰의 `iss` 검사) | `accounts.issuer` (로컬: `http://localhost:3001`) |
| 토큰 교환, 공개 키 (서버가 직접 부르는 주소) | `accounts.backchannel-uri` (로컬: `http://localhost:9000`) |
| PKCE | Spring Security 7이 기본으로 붙임 (`code_challenge_method=S256`) |

`issuer-uri` 설정을 쓰지 않고 주소를 직접 등록한 이유: `issuer-uri`를 쓰면 blog를 켤 때 accounts에서 설정 문서를 받아 와야 해서, accounts가 꺼져 있으면 blog도 켜지지 않습니다.

### ID 토큰 검증 (Spring Security가 처리)
| 검사 | 내용 |
|---|---|
| 서명 | accounts의 공개 키(`/oauth2/jwks`)로 RS256 서명 확인 |
| `iss` | `http://localhost:3001`과 같은가 |
| `aud` | `blog`가 들어 있는가 |
| `exp`, `iat` | 만료되지 않았는가 |
| `nonce` | 로그인 시작 때 세션에 저장한 값과 같은가 (토큰 재사용 방지) |

### 인가: 작성자 확인
URL 단위 인가(`/api/**` 인증 필요)에 더해, 글 수정과 삭제는 서비스 계층에서 **데이터 단위 인가**를 합니다. 로그인한 사용자의 이름(`Authentication.getName()`)은 ID 토큰의 `sub`입니다.
```java
private static void authorizeArticleAuthor(Article article) {
    String accountsSub = SecurityContextHolder.getContext().getAuthentication().getName();
    if (!article.getAuthor().getAccountsSub().equals(accountsSub)) {
        throw new ArticleForbiddenException();
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

### 2. 로그인 흐름 (accounts OIDC → blog 세션)
```
브라우저              blog frontend (3000)        blog backend (8080)            accounts (3001 → 9000)
  │ 글쓰기 클릭 (/write)    │                          │                              │
  │──────────────────────▶│ /api/me → 401 (로그인 안 됨)                              │
  │ 돌아올 화면(/write)을 sessionStorage에 저장                                        │
  │ GET /oauth2/authorization/accounts ──(rewrites)──▶│ state, nonce, code_verifier 생성 → 세션
  │◀── 302 :3001/oauth2/authorize?client_id=blog&redirect_uri=http://localhost:3000/login/oauth2/code/accounts...
  │─────────────────────────────────────────────────────────────────────────────────▶│
  │                        accounts 로그인 (이미 로그인돼 있으면 생략 = SSO)              │
  │◀── 302 :3000/login/oauth2/code/accounts?code=...&state=... ─────────────────────│
  │──────────────────────▶│──(rewrites)─────────────▶│ ① state 비교                  │
  │                       │                          │ ② POST :9000/oauth2/token ───▶│
  │                       │                          │◀── id_token, access_token ────│
  │                       │                          │ ③ ID 토큰 검증 ④ 회원 저장       │
  │◀── 302 :3000/, Set-Cookie: BLOG_SESSION ──────────│                               │
  │ 첫 화면이 sessionStorage를 보고 /write로 이동                                        │
```
①②③은 Spring Security OAuth2 Client가 처리하고, ④는 `AccountsOidcUserService`입니다.

### 3. API 호출 흐름
```
글 작성 (브라우저): POST /api/articles → (rewrites) → blog backend
   │ Cookie: BLOG_SESSION, XSRF-TOKEN   (브라우저가 자동으로)
   │ X-XSRF-TOKEN: <XSRF-TOKEN 쿠키 값>  (화면이 붙임. 쿠키가 없으면 /api/csrf로 먼저 받음)
   ▼
CsrfFilter: 헤더 값 == 쿠키 값?          (아니면 403)
   ▼
AuthorizationFilter: 로그인했나?         (아니면 401 → 화면이 accounts 로그인으로)
   ▼
BlogService.save() → sub로 회원 조회 → article.author = 회원

글 목록·상세 (Next.js 서버): GET http://localhost:8080/api/articles
   │ Cookie: 브라우저가 보낸 쿠키를 그대로 넘김 → 로그인했으면 editable이 정확히 나옴
```

### 4. 글 수정, 삭제 흐름
```
PUT /api/articles/{id}
  → CsrfFilter → AuthorizationFilter
  → BlogService.update()
      ├ 글 조회 (작성자까지 함께, 없으면 404)
      ├ authorizeArticleAuthor(): 작성자의 sub == 로그인 사용자의 sub?
      │     다르면 403
      └ article.update() → 변경 감지로 UPDATE, updatedAt 자동 갱신
```

### 5. 로그아웃 흐름
```
헤더의 로그아웃 버튼 → POST /logout (_csrf 포함, rewrites로 이 서버에)
  → blog 세션 삭제
  → 302 :3001/connect/logout?id_token_hint=<ID 토큰>&post_logout_redirect_uri=http://localhost:3000/
  → accounts 세션, 로그인 상태 유지 쿠키 삭제
  → 302 http://localhost:3000/ (로그인 버튼이 보임)
```

## 구현 과정
인증 방식을 단계적으로 바꿔 가며 구현했습니다.

| 단계 | 커밋 | 인증 방식 | 내용 |
|---|---|---|---|
| 1 | `08f359d` | 없음 | 글 CRUD REST API, JPA, H2 |
| 2 | `072b1d3` | 없음 | Thymeleaf 임시 화면, JPA Auditing(작성/수정 시각) |
| 3 | `9ef1b99` | 폼 로그인 + 세션 | Spring Security, 회원가입, BCrypt, `UserDetailsService` |
| 4 | `77e2349` | (준비) JWT | `TokenProvider`, `TokenAuthenticationFilter`, 리프레시 토큰, 재발급 API |
| 5 | `51e8aa2` | Google OAuth2 + JWT, STATELESS | OAuth2 로그인, 로그인 성공 시 JWT 발급, 글 작성자와 작성자 확인 |
| 6 | `06cbebc` ~ `8664d11` | accounts OIDC + 세션 | 로그인을 accounts에 맡김. Google 로그인, JWT 코드 삭제. `sub`로 회원 연결, CSRF, 전체 로그아웃 |
| 7 | `8d3636a` ~ `22ff712` | **accounts OIDC + 세션, 화면은 Next.js** | 카테고리·요약, 읽기 공개, `/api/me`, Next.js 뒤에서 동작, Thymeleaf 삭제 |

6, 7단계의 자세한 과정은 [note/16_accounts_separation](../note/16_accounts_separation)에 정리했습니다.

## 로컬 실행 방법

### 필요 환경
- JDK 25
- [accounts](https://github.com/51SB-ANTIMONY/accounts) 실행 (backend `localhost:9000`, frontend `localhost:3001`). blog는 accounts 없이도 켜지지만, 로그인하려면 필요합니다.

### 설정 파일
설정은 `application.yml`(공통), `application-prod.yml`(운영, 값은 환경 변수), 테스트용 `src/test/resources/application-test.yml`로 나뉩니다. 로컬 비밀 값은 Git에서 제외한 `backend/application-local.yml`에 둡니다. 같은 폴더의 `application-local.yml.example`을 복사해서 만들어 주세요. 프로필을 지정하지 않으면 `local` 프로필로 실행됩니다.
- `accounts.client-secret`: accounts 설정의 `accounts.clients.blog.client-secret`과 같은 값

### 실행
```bash
./gradlew bootRun          # localhost:8080 (API)

cd ../frontend
npm install
npm run dev                # localhost:3000 (화면)
```
- 화면: `http://localhost:3000`
- H2 콘솔: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:testdb`
  - User Name: `sa`
  - Password: (비워 둠)

## 테스트
```bash
./gradlew test
```
accounts를 켜지 않아도 됩니다. 로그인한 상태는 `spring-security-test`의 `oidcLogin()`으로 만듭니다.

| 테스트 클래스 | 대상 | 내용 |
|---|---|---|
| `AccountsLoginTest` | 로그인, 로그아웃 | 권한 요청 주소(client_id, 범위, PKCE, nonce), Next.js를 거친 요청이면 redirect_uri·로그아웃 후 주소가 3000, 브라우저용·서버용 주소, API 401, 로그아웃 → accounts `/connect/logout` |
| `CsrfTest` | CSRF | 토큰 발급, 토큰 없으면 403, 쿠키 값을 헤더로 보내면 통과 (새 Spring 컨텍스트에서 실행) |
| `BlogApiControllerTest` | 글 API | 작성, 목록, 상세, 수정, 삭제와 응답 형식(작성자 닉네임, 카테고리, 요약). 로그인 없이 읽기, `editable`, 카테고리 필터, 본문으로 만든 요약, `/api/me`. 없는 글 404, 작성자가 아닌 사용자의 수정·삭제 403, 입력값 검증 400 |
| `UserServiceTest` | 회원 연결 | 처음 로그인하면 회원 생성, 같은 `sub`면 같은 회원이고 닉네임 갱신 |

## 환경 설정 (예정)
운영 환경에서 필요한 환경 변수입니다. 값은 Elastic Beanstalk 환경 속성에 설정하고 저장소에 올리지 않습니다.

| 환경 변수 | 용도 |
|---|---|
| `ACCOUNTS_ISSUER` | accounts 주소 (예: `https://accounts.antimony.kr`) |
| `ACCOUNTS_BACKCHANNEL_URI` | blog 서버가 accounts를 직접 부르는 주소 (없으면 issuer) |
| `ACCOUNTS_CLIENT_SECRET` | accounts에 등록한 blog의 Client Secret |
| `DB_URL` | DB 접속 주소 (5-2에서 MySQL) |
| `DB_USERNAME`, `DB_PASSWORD` | DB 계정 |

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
- **해결**: 6단계에서 토큰을 브라우저에 두지 않고 세션으로 로그인 상태를 기억하도록 바꿨다. 로그아웃(`POST /logout`)은 blog 세션을 지우고, accounts의 `/connect/logout`으로 보내 accounts 로그인까지 끝낸다. 지울 토큰이 브라우저에 남지 않는다.

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
- **해결**: 6단계에서 `STATELESS`를 없애고 세션을 쓰게 되면서, OAuth2 인가 요청 정보는 Spring Security 기본값대로 **서버의 세션**에 저장한다. 쿠키 저장소(`OAuth2AuthorizationRequestBasedOnCookieRepository`)와 `CookieUtil`을 삭제했다.

### 7. 액세스 토큰과 리프레시 토큰이 노출되기 쉬움
- **문제**: 로그인 성공 후 액세스 토큰이 `/articles?token=...`처럼 URL로 전달되고, 리프레시 토큰 쿠키에 `HttpOnly`가 없다.
- **원인**: `OAuth2SuccessHandler`가 액세스 토큰을 쿼리 파라미터로 붙여 리다이렉트하므로 브라우저 방문 기록과 서버 접속 로그에 남는다. `CookieUtil.addCookie()`가 `HttpOnly`, `Secure`, `SameSite`를 설정하지 않으며, `article.js`가 `document.cookie`로 리프레시 토큰을 읽는 구조라 XSS가 발생하면 탈취될 수 있다.
- **해결**: 6단계에서 blog가 직접 발급하던 JWT를 없앴다. accounts에서 받은 토큰은 blog 서버의 세션에만 있고, 브라우저에는 `HttpOnly` 세션 쿠키(`BLOG_SESSION`)만 간다. 토큰이 URL이나 JavaScript가 읽을 수 있는 곳에 남지 않는다.

### 8. 다른 사람의 글을 수정하거나 삭제하면 500 에러 발생
- **문제**: 작성자가 아닌 사용자가 글 수정, 삭제 요청을 보내면 403 Forbidden이 아니라 500 Internal Server Error가 응답된다.
- **원인**: `BlogService.authorizeArticleAuthor()`가 작성자가 다르면 `IllegalArgumentException("not authorized")`을 던지고, 이 예외를 HTTP 상태 코드로 바꾸는 처리가 없다.
- **해결**: 작성자가 다르면 `ArticleForbiddenException`(403)을 던지고, `GlobalExceptionHandler`가 `{"message": "글 작성자만 수정하거나 삭제할 수 있습니다.", "errors": {}}`로 응답한다. 수정, 삭제 각각에 대해 403 응답과 데이터가 바뀌지 않는 것을 테스트로 확인한다.

### 9. 이름이 같은 Google 사용자가 로그인하면 실패
- **문제**: 이미 가입한 사용자와 Google 계정 이름이 같은 사용자가 처음 로그인하면 회원 저장에 실패한다.
- **원인**: `User.nickname`이 `unique = true`인데, `OAuth2UserCustomService`가 Google의 `name` 값을 그대로 닉네임으로 저장한다. Google 계정 이름은 서로 겹칠 수 있다.
- **해결**: 회원 가입과 닉네임 중복 처리를 accounts가 맡는다(중복이면 숫자 4자리를 붙임). blog의 `users.nickname`은 accounts의 닉네임을 복사해 두는 값이라 `UNIQUE`를 없앴고, 회원은 이름이 아니라 `sub`로 구별한다.

### 10. 액세스 토큰 유효 시간이 발급 경로마다 다름
- **문제**: 로그인 직후 받은 액세스 토큰은 1일, 재발급받은 액세스 토큰은 2시간 동안 유효하다.
- **원인**: `OAuth2SuccessHandler.ACCESS_TOKEN_DURATION`은 `Duration.ofDays(1)`이고, `TokenService.createNewAccessToken()`은 `Duration.ofHours(2)`를 따로 지정한다. 유효 시간이 한곳에서 관리되지 않는다.
- **해결**: 6단계에서 blog의 액세스 토큰, 리프레시 토큰, 재발급 API를 모두 없앴다. 토큰 수명은 accounts가 관리한다(액세스 30분, 리프레시 14일).

### 11. 로그인이 필요한 화면인데 테스트에서는 401
- (Thymeleaf 화면이 있던 6단계의 기록. 7단계에서 화면을 Next.js로 옮기며 이 테스트는 지웠다.)
- **문제**: 테스트에서 로그인하지 않고 `GET /new-article`을 보내자, accounts 로그인으로 보내는 302가 아니라 401이 응답됐다.
- **원인**: Spring Security는 HTML을 원하는 요청(`Accept: text/html`, 브라우저가 보냄)에만 로그인 화면으로 보내는 처리기를 쓴다. MockMvc 요청에는 `Accept` 헤더가 없어서, 직접 등록한 다른 처리기(API용 401)가 쓰였다.
- **해결**: 테스트 요청에 `.accept(MediaType.TEXT_HTML)`을 붙여 브라우저 요청과 같게 했다.

### 12. 로그인하지 않은 API 요청인데 401이 아니라 403
- **문제**: CSRF 보호를 켜자, 로그인하지 않고 `POST /api/articles`를 보내는 테스트가 401 대신 403을 받았다.
- **원인**: 필터 체인에서 `CsrfFilter`가 인가 검사(`AuthorizationFilter`)보다 앞에 있다. CSRF 토큰이 없는 요청은 로그인 여부를 확인하기 전에 403으로 거절된다.
- **해결**: 의도한 동작이다. "로그인 안 됨"만 확인하는 테스트에는 `.with(csrf())`로 토큰을 넣었다.

### 13. accounts 로그인 후 blog로 돌아오면 `/login?error`
- **문제**: 세 서버를 함께 띄워 확인하자, accounts 로그인까지는 성공했지만 blog로 돌아온 뒤 `/login?error`로 이동했다. 기본 오류 화면은 `Invalid credentials`만 보여 줬다.
- **원인**: blog 서버가 code를 토큰으로 바꾸려고 `http://localhost:3001/oauth2/token`(accounts 화면 서버, Next.js)을 불렀다. Spring이 쓰는 Java 기본 HTTP 클라이언트는 `http://` 주소에 HTTP/2로 바꾸자는 요청(`Upgrade: h2c`)을 함께 보내는데, Next.js는 이 요청에 응답하지 않고 연결을 끊었다. `curl --http2`로 같은 요청을 보내 3001은 빈 응답, 9000(Spring)은 정상 응답인 것을 확인했다.
- **해결**: 주소를 두 가지로 나눴다. 브라우저가 가는 주소(로그인, 로그아웃)는 `accounts.issuer`(3001), blog 서버가 직접 부르는 주소(토큰 교환, 공개 키)는 `accounts.backchannel-uri`(9000)로 정했다. ID 토큰의 `iss`는 계속 3001과 비교한다.

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
| SSO, OIDC 개념과 설계 | [`15_sso`](../note/15_sso) |
| accounts 분리 (단계별 목적, 계획, 구현, 트러블슈팅) | [`16_accounts_separation`](../note/16_accounts_separation) |

## 개선 예정
- [x] 글 CRUD REST API
- [x] Spring Security 적용
- [x] JWT 액세스 토큰, 리프레시 토큰 발급과 재발급
- [x] Google OAuth 2.0 로그인
- [x] 글 작성자 저장, 작성자만 수정·삭제
- [x] 로그아웃 (blog 세션 + accounts 로그인 함께 종료)
- [x] 예외 처리 통일 (404, 403 응답, 공통 에러 응답 형식), 입력값 검증, 응답 DTO 통일
- [x] 토큰 보관 방식 개선 (토큰은 서버 세션에, 브라우저에는 `HttpOnly` 세션 쿠키만)
- [x] 이전 폼 로그인, Google 로그인, JWT 코드 정리
- [x] 로그인을 accounts(OIDC)로 이전
- [ ] AWS: RDS 연결, S3 이미지 업로드, Elastic Beanstalk 배포
- [ ] CI/CD: GitHub Actions로 테스트, 빌드, 배포 자동화
- [x] 별도 프론트엔드(Next.js)로 화면 교체
- [ ] 태그, 공개 범위, 임시저장, 미리보기, 사진 업로드, 댓글, 예약 게시, 검색 (화면에는 "준비 중"으로 표시)
