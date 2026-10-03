# Accounts

`accounts.antimony.kr`에서 동작할 antimony.kr 통합 로그인(SSO) 서버입니다. 아이디·비밀번호 로그인과 소셜 로그인(Google)을 제공하고, antimony.kr의 여러 앱에 OpenID Connect로 로그인을 제공하는 것이 목표입니다.

> 현재 개발 중입니다. 설계는 [note/15_sso](../note/15_sso), 단계별 계획과 구현 과정은 [note/16_accounts_separation](../note/16_accounts_separation)을 참고해 주세요.
> 이 폴더는 나중에 별도 저장소로 분리합니다. 그래서 폴더 밖의 파일을 참조하지 않고, 이 폴더만으로 빌드되고 실행되도록 구성합니다.

## 구성
| 폴더 | 내용 | 상태 |
|---|---|---|
| `backend/` | Spring Boot 인가 서버, 로그인 처리, API | 개발 중 |
| `frontend/` | Next.js 화면 (로그인, 회원가입, 이메일 인증, 아이디·비밀번호 찾기, 내 계정) | 개발 중 |

브라우저는 Next.js(`localhost:3001`) 한 곳만 봅니다. Next.js가 아래 경로를 Spring(`localhost:9000`)으로 넘깁니다(`frontend/next.config.ts`의 rewrites). 그래서 화면과 API가 같은 주소이고, 세션 쿠키와 CSRF 쿠키를 그대로 함께 씁니다.

| Spring으로 넘기는 경로 | 용도 |
|---|---|
| `/api/**` | 회원가입, 계정 찾기, 내 계정 API |
| `/login/password` | 아이디·비밀번호 로그인 처리 |
| `/oauth2/**`, `/login/oauth2/**` | 소셜 로그인 시작·콜백, OIDC authorize·token·jwks |
| `/.well-known/**`, `/userinfo`, `/connect/**` | OIDC 설정 문서, 사용자 정보, 로그아웃 |
| `/logout` | accounts 로그아웃 |

## 현재 상태 (1-7단계)
Spring Authorization Server로 **OpenID Connect 인가 서버**가 되었습니다. 등록된 앱(지금은 `blog`)에 권한 부여 코드 방식으로 ID 토큰, 액세스 토큰, 리프레시 토큰을 발급합니다. issuer는 화면 주소인 `http://localhost:3001`입니다.

### 필터 체인
| 순서 | 체인 | 담당 주소 | 인증 |
|---|---|---|---|
| 1 | 인가 서버 (`AuthorizationServerConfig`) | `/oauth2/**`, `/.well-known/**`, `/userinfo`, `/connect/**` | 앱: Client Secret / `/userinfo`: 액세스 토큰. 로그인 안 된 브라우저 요청은 `/login`으로 |
| 2 | 로그인 (`SecurityConfig`) | 그 외 전부 | 아이디(`uid`)·비밀번호 폼 로그인, Google 로그인, 로그인 상태 유지. 세션, CSRF(`XSRF-TOKEN` 쿠키) |

### 화면 (Next.js)
| 주소 | 화면 |
|---|---|
| `/login` | 로그인. 실패, 소셜 로그인 오류, 로그아웃 안내를 위쪽 상자로 보여 줌 |
| `/signup` → `/signup/verify` | 회원가입 → 이메일 인증번호 입력 |
| `/find-id` | 아이디 찾기 (이메일 → 인증번호 → 가린 아이디) |
| `/reset-password` | 비밀번호 찾기 (아이디·이메일 → 인증번호 → 새 비밀번호) |
| `/account` | 내 계정. 계정 정보, 소셜 계정 연결·해제, 로그아웃 |

PC와 모바일(가로 1023px 이하), 라이트·다크 모드를 지원합니다. 디자인은 "ANTIMONY 로그인 · 회원가입" 프로토타입을 그대로 따릅니다.

### 주요 주소 (Spring)
| 주소 | 설명 |
|---|---|
| `GET /.well-known/openid-configuration` | 설정 문서 |
| `GET /oauth2/authorize` | 권한 요청 (PKCE 필수) |
| `POST /oauth2/token` | 코드 → 토큰 교환, 리프레시 토큰으로 재발급 |
| `GET /oauth2/jwks` | 토큰 서명 검증용 공개 키 |
| `GET /userinfo` | 사용자 정보 (액세스 토큰 필요) |
| `GET /connect/logout` | OIDC 로그아웃 (세션과 로그인 상태 유지 쿠키 삭제) |
| `POST /login/password` | 아이디·비밀번호 로그인. 성공 → 원래 요청 또는 `/account`, 실패 → `/login?error=bad_credentials` |
| `POST /logout` | 로그아웃 → `/login?logout` |
| `GET /api/csrf` | CSRF 토큰 발급 |
| `GET /api/auth/providers` | 소셜 로그인 제공자별 사용 가능 여부 (`google`, `kakao`, `naver`) |
| `GET /api/signup/uid-availability` | 아이디 중복확인 (`ok`, `invalid`, `taken`) |
| `POST /api/signup` | 가입 신청 → 이메일로 인증번호 발송 (202) |
| `POST /api/signup/verify`, `/api/signup/resend` | 인증번호 확인 → 회원 생성 (201) / 재전송 |
| `POST /api/find-id/code`, `/api/find-id/verify` | 아이디 찾기 |
| `POST /api/password-reset/code`, `/verify`, `/complete` | 비밀번호 찾기 (인증 후 10분 안에 새 비밀번호) |
| `GET /api/me`, `DELETE /api/me/identities/{provider}` | 내 계정 정보, 소셜 계정 연결 해제 |

### 회원가입 규칙
| 필드 | 규칙 |
|---|---|
| `uid` (아이디) | 영문 소문자·숫자 4–16자. 중복 불가 |
| `password` | 8–64자, 영문·숫자·특수문자를 모두 포함. BCrypt로 저장 |
| `nickname` | 2–16자. 중복 불가 |
| `email` | 이메일 형식. 중복 불가. 인증번호로 확인한 뒤에 회원이 됨 |
| 약관 | 이용약관·개인정보 동의 필수, 이벤트·소식 수신 선택 |

인증번호는 6자리, 3분 동안 유효하고 5번 틀리면 다시 받아야 합니다. 인증 전 신청은 `pending_signups`에 두고, 인증하면 `users`에 저장합니다. 개발 환경에서는 메일 대신 서버 로그에 인증번호를 출력합니다(`spring.mail.host`를 설정하면 SMTP로 보냄).

### 소셜 로그인 (Google)
소셜 계정은 이메일이 아니라 **제공자의 계정 고유 번호(Google의 `sub`)**로 회원과 연결합니다(`user_identities` 테이블). 소셜 계정의 이메일이 바뀌어도 같은 회원으로 로그인됩니다.

| 상황 | 결과 |
|---|---|
| 처음 보는 Google 계정, 같은 이메일의 회원 없음 | 자동 가입. 아이디는 `google_3f9a2c1b`처럼 자동 생성, 닉네임은 Google 이름(이미 있으면 뒤에 숫자 4자리). 비밀번호가 없으므로 아이디·비밀번호 로그인은 불가 |
| 이미 연결된 Google 계정 | 연결된 회원으로 로그인 |
| 처음 보는 Google 계정인데 **같은 이메일의 회원이 이미 있음** | **거부.** 자동으로 합치지 않는다. `/login?social_error=email_already_registered` |
| 아이디·비밀번호로 로그인한 상태에서 Google 로그인 | 그 회원에 Google 계정을 **연결** |
| 연결하려는 Google 계정이 이미 다른 회원에 연결됨 | 거부. `/login?social_error=linked_to_another_user` |

에러 응답 형식:
```json
{"message": "이미 사용 중인 값이 있습니다.", "errors": {"uid": "이미 사용 중인 아이디예요."}}
```

### 등록된 앱
| client_id | 인증 방식 | redirect_uri | scope | 토큰 수명 |
|---|---|---|---|---|
| `blog` | `client_secret_basic` + PKCE | `http://localhost:8080/login/oauth2/code/accounts` | `openid`, `profile`, `email` | 액세스 30분, 리프레시 14일 (재발급 시 교체) |

자사 앱이므로 동의 화면은 생략합니다.

ID 토큰의 `sub`는 로그인 방식과 상관없이 **회원 id**입니다. `profile` 범위면 `nickname`, `preferred_username`(아이디), `email` 범위면 `email` 클레임을 담습니다.

### 아직 남은 것
- 토큰 서명 키를 서버를 시작할 때마다 새로 만들어서, 재시작하면 기존 토큰을 검증할 수 없습니다.
- 등록된 앱, 발급한 토큰, 회원은 메모리 DB(H2)에 저장합니다. 재시작하면 사라집니다.
- 카카오·네이버 로그인은 아직 등록하지 않아 "준비 중"으로 보입니다.
- 약관 "보기" 링크와 왼쪽 소개 문구는 프로토타입의 자리 표시 그대로입니다.

## 로컬 실행

### 필요 환경
- JDK 25
- Node.js 20 이상

### 설정 파일
`backend/src/main/resources/application.yml`은 비밀 값이 들어가므로 Git에서 제외되어 있습니다. 같은 폴더의 `application.yml.example`을 복사해서 만들어 주세요.

- `accounts.clients.blog.client-secret`: blog 앱의 Client Secret. blog 서버 설정에도 같은 값을 넣습니다.
- Google Cloud Console의 OAuth 클라이언트에 승인된 리디렉션 URI `http://localhost:3001/login/oauth2/code/google`을 추가해야 합니다. (브라우저가 보는 주소가 Next.js이기 때문)

### 실행
```bash
# 터미널 1
cd backend
./gradlew bootRun

# 터미널 2
cd frontend
npm install
npm run dev
```
- 주소: `http://localhost:3001` (Spring은 `localhost:9000`에서 뒤에서 동작)
- H2 콘솔: `http://localhost:9000/h2-console` (JDBC URL: `jdbc:h2:mem:accounts`, User Name: `sa`)

같은 컴퓨터에서 blog 서버(`localhost:8080`)와 함께 실행할 때 세션 쿠키가 섞이지 않도록, 세션 쿠키 이름을 `ACCOUNTS_SESSION`으로 정했습니다. 쿠키는 포트를 구분하지 않기 때문입니다.

### 테스트
```bash
cd backend
./gradlew test

cd frontend
npm run lint
npm run build
```
