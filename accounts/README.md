# Accounts

`accounts.antimony.kr`에서 동작할 antimony.kr 통합 로그인(SSO) 서버입니다. 아이디·비밀번호 로그인과 소셜 로그인(Google)을 제공하고, antimony.kr의 여러 앱에 OpenID Connect로 로그인을 제공하는 것이 목표입니다.

> 현재 개발 중입니다. 설계는 [note/15_sso](../note/15_sso)를 참고해 주세요.
> 이 폴더는 나중에 별도 저장소로 분리합니다. 그래서 폴더 밖의 파일을 참조하지 않고, 이 폴더만으로 빌드되고 실행되도록 구성합니다.

## 구성
| 폴더 | 내용 | 상태 |
|---|---|---|
| `backend/` | Spring Boot 인가 서버 | 개발 중 |
| `frontend/` | Next.js 로그인, 회원가입 화면 | 예정 |

## 현재 상태 (1-3단계)
Spring Authorization Server로 **OpenID Connect 인가 서버**가 되었습니다. 등록된 앱(지금은 `blog`)에 권한 부여 코드 방식으로 ID 토큰, 액세스 토큰, 리프레시 토큰을 발급합니다.

### 필터 체인
| 순서 | 체인 | 담당 주소 | 인증 |
|---|---|---|---|
| 1 | 인가 서버 (`AuthorizationServerConfig`) | `/oauth2/**`, `/.well-known/**`, `/userinfo`, `/connect/**` | 앱: Client Secret / `/userinfo`: 액세스 토큰. 로그인 안 된 브라우저 요청은 `/login`으로 |
| 2 | 로그인 (`SecurityConfig`) | 그 외 전부 | 아이디(`uid`)·비밀번호 폼 로그인, Google 로그인. 세션, CSRF 보호 |

### 주요 주소
| 주소 | 설명 |
|---|---|
| `GET /.well-known/openid-configuration` | 설정 문서 |
| `GET /oauth2/authorize` | 권한 요청 (PKCE 필수) |
| `POST /oauth2/token` | 코드 → 토큰 교환, 리프레시 토큰으로 재발급 |
| `GET /oauth2/jwks` | 토큰 서명 검증용 공개 키 |
| `GET /userinfo` | 사용자 정보 (액세스 토큰 필요) |
| `GET /login` | 로그인 화면 (아이디·비밀번호, Google) |
| `GET /signup`, `POST /user` | 회원가입 |

### 등록된 앱
| client_id | 인증 방식 | redirect_uri | scope | 토큰 수명 |
|---|---|---|---|---|
| `blog` | `client_secret_basic` + PKCE | `http://localhost:8080/login/oauth2/code/accounts` | `openid`, `profile`, `email` | 액세스 30분, 리프레시 14일 (재발급 시 교체) |

자사 앱이므로 동의 화면은 생략합니다.

### 아직 남은 것
- ID 토큰의 `sub`가 로그인 방식에 따라 다릅니다. 아이디 로그인은 `uid`, Google 로그인은 Google의 `sub`입니다. 회원 id로 통일하고 `nickname`, `email` 클레임을 추가합니다.
- 토큰 서명 키를 서버를 시작할 때마다 새로 만들어서, 재시작하면 기존 토큰을 검증할 수 없습니다.
- 등록된 앱, 발급한 토큰은 메모리에 저장합니다. 재시작하면 사라집니다.
- 화면은 임시로 Thymeleaf를 쓰며, Next.js로 바꿀 예정입니다.

## 로컬 실행

### 필요 환경
- JDK 25

### 설정 파일
`backend/src/main/resources/application.yml`은 비밀 값이 들어가므로 Git에서 제외되어 있습니다. 같은 폴더의 `application.yml.example`을 복사해서 만들어 주세요.

- `accounts.clients.blog.client-secret`: blog 앱의 Client Secret. blog 서버 설정에도 같은 값을 넣습니다.
- Google Cloud Console의 OAuth 클라이언트에 승인된 리디렉션 URI `http://localhost:9000/login/oauth2/code/google`을 추가해야 합니다.

### 실행
```bash
cd backend
./gradlew bootRun
```
- 주소: `http://localhost:9000`
- H2 콘솔: `http://localhost:9000/h2-console` (JDBC URL: `jdbc:h2:mem:accounts`, User Name: `sa`)

같은 컴퓨터에서 blog 서버(`localhost:8080`)와 함께 실행할 때 세션 쿠키가 섞이지 않도록, 세션 쿠키 이름을 `ACCOUNTS_SESSION`으로 정했습니다. 쿠키는 포트를 구분하지 않기 때문입니다.

### 테스트
```bash
cd backend
./gradlew test
```
