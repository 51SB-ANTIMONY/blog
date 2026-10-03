# Accounts

`accounts.antimony.kr`에서 동작할 antimony.kr 통합 로그인(SSO) 서버입니다. 아이디·비밀번호 로그인과 소셜 로그인(Google)을 제공하고, antimony.kr의 여러 앱에 OpenID Connect로 로그인을 제공하는 것이 목표입니다.

> 현재 개발 중입니다. 설계는 [note/15_sso](../note/15_sso)를 참고해 주세요.
> 이 폴더는 나중에 별도 저장소로 분리합니다. 그래서 폴더 밖의 파일을 참조하지 않고, 이 폴더만으로 빌드되고 실행되도록 구성합니다.

## 구성
| 폴더 | 내용 | 상태 |
|---|---|---|
| `backend/` | Spring Boot 인가 서버 | 개발 중 |
| `frontend/` | Next.js 로그인, 회원가입 화면 | 예정 |

## 현재 상태 (1-2단계)
blog 서버의 인증 코드(Google 로그인, JWT 발급과 재발급, 회원)를 **그대로 복사**한 상태입니다. 패키지 이름만 `kr.antimony.accounts`로 바꿨고, 로그인 성공 후 이동할 주소(`accounts.login-success-redirect-url`)를 설정으로 뺐습니다.

- 아직 OpenID Connect 인가 서버가 아니며, blog의 Google 로그인을 그대로 떼어 낸 "토큰 발급 서버"입니다.
- 다음 단계에서 토큰 처리를 Spring Authorization Server로 교체합니다.

| 기능 | 주소 |
|---|---|
| 로그인 화면 | `GET /login` |
| Google 로그인 시작 | `GET /oauth2/authorization/google` |
| 액세스 토큰 재발급 | `POST /api/token` |
| 회원가입 (폼) | `GET /signup`, `POST /user` |

## 로컬 실행

### 필요 환경
- JDK 25

### 설정 파일
`backend/src/main/resources/application.yml`은 비밀 값이 들어가므로 Git에서 제외되어 있습니다. 같은 폴더의 `application.yml.example`을 복사해서 만들어 주세요.

- `jwt.secret_key`: blog 서버가 accounts가 발급한 토큰을 검증할 수 있도록 **blog와 같은 값**을 넣습니다.
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
