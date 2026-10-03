# Blog

Spring Boot 백엔드와 별도의 프론트엔드로 구성된 개인 블로그 서비스입니다.
백엔드는 AWS(Elastic Beanstalk, RDS, S3)에 배포합니다.

> 현재 개발 중입니다. 각 기능의 구현 상태는 아래 표를 참고해 주세요.

## 배포 주소
| 구분 | 주소 |
|---|---|
| Frontend | (배포 예정) |
| Backend API | (배포 예정) |

## 주요 기능
| 기능 | 설명 | 상태 |
|---|---|---|
| 글 관리 | 글 작성, 목록 조회, 상세 조회, 수정, 삭제 | 구현 완료 |
| 소셜 로그인 | Google OAuth 2.0 로그인 (권한 부여 코드 방식) | 구현 완료 |
| JWT 인증 | 로그인 시 액세스 토큰, 리프레시 토큰 발급. API 요청은 `Authorization: Bearer`로 인증 | 구현 완료 |
| 토큰 재발급 | 리프레시 토큰으로 액세스 토큰 재발급 | 구현 완료 |
| 글 작성 권한 | 로그인한 사용자만 글 작성, 작성자만 수정·삭제 | 구현 완료 |
| 로그아웃 | 리프레시 토큰 삭제, 클라이언트 토큰 삭제 | 보완 중 |
| 이미지 업로드 | 글에 사진을 넣으면 S3에 업로드하고 이미지 주소를 본문에 삽입 | 예정 |

## 아키텍처
```
사용자 브라우저
   │
   ▼
Frontend (현재: Thymeleaf 임시 화면 / 예정: 별도 프론트엔드)
   │  REST API (JSON), Authorization: Bearer <JWT>
   ▼
AWS Elastic Beanstalk ── Spring Boot 애플리케이션
   │                 │
   ▼                 ▼
Amazon RDS         Amazon S3
(글, 회원 데이터)   (글에 삽입한 이미지)

Google (OAuth 2.0 로그인) ◀── 토큰 교환, 사용자 정보 ── Spring Boot
GitHub ── push ──▶ GitHub Actions ── 빌드·배포 ──▶ Elastic Beanstalk
```

## 기술 스택
| 구분 | 기술 |
|---|---|
| Frontend | (임시) Thymeleaf, Bootstrap, JavaScript / (예정) 별도 프론트엔드 |
| Backend | Java 25, Spring Boot 4, Spring Data JPA, Spring Security 7, OAuth 2.0 Client (Google), JWT |
| Database | H2 (로컬, 테스트), (예정) Amazon RDS |
| Infra | (예정) AWS Elastic Beanstalk, Amazon RDS, Amazon S3 |
| CI/CD | (예정) GitHub Actions |

## 저장소 구성
| 폴더 | 내용 |
|---|---|
| [`backend/`](./backend) | Spring Boot REST API 서버. API 명세, 인증 흐름, 실행 방법은 [backend/README.md](./backend/README.md) 참고 |
| [`frontend/`](./frontend) | 프론트엔드 (개발 예정) |
| [`note/`](./note) | 프로젝트를 만들며 공부한 내용 정리 |
