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
| 통합 로그인 (SSO) | antimony.kr 통합 로그인 서버 [accounts](./accounts)에 로그인을 맡김 (OpenID Connect). 아이디·비밀번호, Google 로그인 | 구현 완료 (로컬) |
| 로그인 상태 | accounts에서 돌아오면 blog 세션으로 기억. 토큰은 브라우저에 두지 않음 | 구현 완료 |
| 글 작성 권한 | 로그인한 사용자만 글 작성, 작성자만 수정·삭제 | 구현 완료 |
| 로그아웃 | blog 세션과 accounts 로그인을 함께 종료 | 구현 완료 |
| 이미지 업로드 | 글에 사진을 넣으면 S3에 업로드하고 이미지 주소를 본문에 삽입 | 예정 |

## 아키텍처
```
사용자 브라우저
   │
   ▼
Frontend (Next.js, localhost:3000)
   │  REST API (JSON), 세션 쿠키 + CSRF 토큰
   ▼
AWS Elastic Beanstalk ── Spring Boot 애플리케이션
   │                 │
   ▼                 ▼
Amazon RDS         Amazon S3
(글, 회원 데이터)   (글에 삽입한 이미지)

accounts (OIDC 통합 로그인) ◀── 로그인 요청, 토큰 교환 ── Spring Boot
GitHub ── push ──▶ GitHub Actions ── 빌드·배포 ──▶ Elastic Beanstalk
```

## 기술 스택
| 구분 | 기술 |
|---|---|
| Frontend | Next.js 16 (App Router, TypeScript) |
| Backend | Java 25, Spring Boot 4, Spring Data JPA, Spring Security 7, OAuth 2.0 Client (OIDC) |
| 로그인 서버 (accounts) | Spring Authorization Server, Next.js |
| Database | H2 (로컬, 테스트), (예정) Amazon RDS |
| Infra | (예정) AWS Elastic Beanstalk, Amazon RDS, Amazon S3 |
| CI/CD | (예정) GitHub Actions |

## 저장소 구성
| 폴더 | 내용 |
|---|---|
| [`backend/`](./backend) | Spring Boot REST API 서버. API 명세, 인증 흐름, 실행 방법은 [backend/README.md](./backend/README.md) 참고 |
| [`frontend/`](./frontend) | 블로그 화면 (Next.js). 홈, 글 상세, 글 쓰기·수정. 실행 방법은 [frontend/README.md](./frontend/README.md) 참고 |
| [`accounts/`](./accounts) | antimony.kr 통합 로그인 서버 (OIDC 인가 서버 + 로그인 화면). 나중에 별도 저장소로 분리 |
| [`note/`](./note) | 프로젝트를 만들며 공부한 내용 정리 |
