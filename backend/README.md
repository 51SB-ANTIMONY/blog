# Blog Backend

블로그 서비스의 REST API 서버입니다. Spring Boot로 개발하고 AWS Elastic Beanstalk에 배포합니다.
프로젝트 전체 소개는 [루트 README](../README.md)를 참고해 주세요.

## 기술 스택
| 구분 | 기술 | 상태 |
|---|---|---|
| Language | Java 25 | 사용 중 |
| Framework | Spring Boot 4.0, Spring Web MVC | 사용 중 |
| ORM | Spring Data JPA, Hibernate | 사용 중 |
| Database | H2 (로컬, 테스트) | 사용 중 |
| Database | Amazon RDS (운영) | 예정 |
| 인증 | Spring Security, JWT, OAuth 2.0 Client | 예정 |
| 파일 저장소 | Amazon S3 (이미지 업로드) | 예정 |
| 배포 | AWS Elastic Beanstalk | 예정 |
| CI/CD | GitHub Actions | 예정 |
| Test | JUnit 5, AssertJ, MockMvc | 사용 중 |
| 기타 | Lombok | 사용 중 |

## 아키텍처
```
Frontend
   │  HTTP 요청 (Authorization: Bearer <JWT>)
   ▼
┌───────────── AWS Elastic Beanstalk ─────────────┐
│  Spring Boot                                     │
│   Spring Security (JWT 검증, OAuth 2.0 로그인)    │
│      ▼                                           │
│   Controller → Service → Repository              │
└──────────────────────────────────────────────────┘
        │                          │
        ▼                          ▼
   Amazon RDS                  Amazon S3
   (글, 회원)                   (이미지 파일)
```

## 프로젝트 구조
```
src/main/java/kr/antimony
├── BlogApplication.java   # 애플리케이션 시작 클래스
├── controller/            # 요청을 받고 응답을 돌려주는 계층
├── service/               # 비즈니스 로직
├── repository/            # DB 접근 (Spring Data JPA)
├── domain/                # 엔티티
└── dto/                   # 요청, 응답 객체
```
로그인과 이미지 업로드를 구현할 때 `config/`(보안·CORS 설정), `security/`(JWT, OAuth 처리) 같은 패키지를 추가할 예정입니다.

## ERD
현재 테이블:
```
article
├── id       BIGINT  PK, 자동 증가
├── title    VARCHAR NOT NULL
└── content  VARCHAR NOT NULL
```
예정:
- `member` 테이블 추가 (일반 회원, 소셜 로그인 회원)
- `article`에 작성자(`member_id`), 작성일, 수정일 컬럼 추가

## API 명세

### 글
| 메서드 | URL | 설명 | 성공 응답 |
|---|---|---|---|
| `POST` | `/api/articles` | 글 작성 | 201 Created |
| `GET` | `/api/articles` | 글 목록 조회 | 200 OK |
| `GET` | `/api/articles/{id}` | 글 상세 조회 | 200 OK |
| `PUT` | `/api/articles/{id}` | 글 수정 | 200 OK |
| `DELETE` | `/api/articles/{id}` | 글 삭제 | 200 OK |

요청 예시:
```http
POST /api/articles
Content-Type: application/json

{
  "title": "제목",
  "content": "내용"
}
```

### 인증 (예정)
| 기능 | 설명 |
|---|---|
| 회원가입 | 이메일, 비밀번호로 가입 |
| 로그인 | 로그인 성공 시 Access Token, Refresh Token 발급 |
| 토큰 재발급 | Refresh Token으로 새 Access Token 발급 |
| 로그아웃 | Refresh Token 무효화 |
| 소셜 로그인 | OAuth 2.0 제공자 로그인 후 JWT 발급 |

### 이미지 (예정)
| 기능 | 설명 |
|---|---|
| 이미지 업로드 | 이미지 파일을 S3에 업로드하고 이미지 URL 반환 |

URL과 요청·응답 형식은 구현할 때 확정해서 이 표에 추가합니다.

## 인증 방식 (예정)

### JWT 로그인
```
① 사용자가 이메일, 비밀번호로 로그인 요청
② 서버가 확인 후 Access Token, Refresh Token 발급
③ 이후 요청마다 Authorization: Bearer <Access Token> 헤더 전송
④ 서버는 Spring Security 필터에서 토큰을 검증하고 사용자 정보 확인
⑤ Access Token이 만료되면 Refresh Token으로 재발급
```

### OAuth 2.0 소셜 로그인
```
① 사용자가 소셜 로그인 버튼 클릭
② OAuth 제공자 로그인 화면에서 로그인, 정보 제공 동의
③ 서버가 제공자로부터 사용자 정보를 받아 회원 조회 또는 가입
④ 서버가 JWT를 발급해 프론트엔드에 전달
⑤ 이후 과정은 JWT 로그인과 같음
```
연동할 OAuth 제공자는 구현할 때 결정합니다.

## 이미지 업로드 (예정)
```
① 글 작성 중 사진 삽입
② 프론트엔드가 이미지 파일을 업로드 API로 전송
③ 서버가 S3에 저장하고 이미지 URL 반환
④ 프론트엔드가 반환된 URL을 글 본문에 삽입
```

## 로컬 실행 방법

### 필요 환경
- JDK 25

### 실행
- API 주소: `http://localhost:8080`
- H2 콘솔: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:testdb`
  - User Name: `sa`
  - Password: (비워 둠)

### 테스트
```bash
./gradlew test
```

## 환경 설정 (예정)
운영 환경에서 필요한 환경 변수 (값은 Elastic Beanstalk 환경 속성에 설정하고 저장소에 올리지 않습니다):

| 환경 변수 | 용도 |
|---|---|
| . | . |
| . | . |
| . | . |
| . | . |
| . | . |
| . | . |

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
- **원인**:
- **해결**:

### 2. API마다 응답 형식이 다름
- **문제**: 글 작성(`POST`)과 수정(`PUT`)은 엔티티를 그대로 응답하고, 조회(`GET`)는 DTO로 응답한다.
- **원인**:
- **해결**:

### 3. 없는 글을 조회하거나 수정하면 500 에러 발생
- **문제**: 존재하지 않는 id로 조회, 수정 요청을 보내면 404 Not Found가 아니라 500 Internal Server Error가 응답된다.
- **원인**:
- **해결**:

### 4. 없는 글을 삭제해도 200 OK 응답
- **문제**: 존재하지 않는 id로 삭제 요청을 보내도 200 OK가 응답된다.
- **원인**:
- **해결**:

## 개선 예정
- [ ] 로그인: JWT 기반 회원가입, 로그인
- [ ] 로그인: OAuth 2.0 소셜 로그인
- [ ] AWS: RDS 연결, S3 이미지 업로드, Elastic Beanstalk 배포
- [ ] CI/CD: GitHub Actions로 테스트, 빌드, 배포 자동화
