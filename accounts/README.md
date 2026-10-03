# Accounts

`accounts.antimony.kr`에서 동작할 antimony.kr 통합 로그인(SSO) 서버입니다. 아이디·비밀번호 로그인과 소셜 로그인(Google)을 제공하고, antimony.kr의 여러 앱에 OpenID Connect로 로그인을 제공하는 것이 목표입니다.

> 현재 개발 중입니다. 설계는 [note/15_sso](../note/15_sso)를 참고해 주세요.
> 이 폴더는 나중에 별도 저장소로 분리합니다. 그래서 폴더 밖의 파일을 참조하지 않고, 이 폴더만으로 빌드되고 실행되도록 구성합니다.

## 구성
| 폴더 | 내용 | 상태 |
|---|---|---|
| `backend/` | Spring Boot 인가 서버 | 개발 중 |
| `frontend/` | Next.js 로그인, 회원가입 화면 | 예정 |

## 로컬 실행

### 필요 환경
- JDK 25

### 설정 파일
`backend/src/main/resources/application.yml`은 비밀 값이 들어가므로 Git에서 제외되어 있습니다. 같은 폴더의 `application.yml.example`을 복사해서 만들어 주세요.

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
