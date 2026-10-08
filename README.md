# Blog

Spring Boot 백엔드와 별도의 프론트엔드로 구성된 개인 블로그 서비스입니다.
백엔드는 AWS Elastic Beanstalk과 RDS(MySQL)에, 화면은 Vercel에 배포하고, `main` 브랜치에 push하면 GitHub Actions가 테스트 후 백엔드를 배포합니다.

> 계속 개발 중입니다. 각 기능의 구현 상태는 아래 표를 참고해 주세요. 개발 경위는 [개발 이력](#개발-이력)에 정리했습니다.

## 배포 주소
| 구분 | 주소 |
|---|---|
| Frontend | https://blog.antimony.kr |
| Backend API | https://blog-api.antimony.kr |

## 주요 기능
| 기능 | 설명 | 상태 |
|---|---|---|
| 글 관리 | 글 작성, 목록 조회, 상세 조회, 수정, 삭제 | 구현 완료 |
| 통합 로그인 (SSO) | antimony.kr 통합 로그인 서버 [accounts](https://github.com/51SB-ANTIMONY/accounts)에 로그인을 맡김 (OpenID Connect). 아이디·비밀번호, Google 로그인 | 구현 완료 |
| 로그인 상태 | accounts에서 돌아오면 blog 세션으로 기억. 토큰은 브라우저에 두지 않음 | 구현 완료 |
| 글 작성 권한 | 로그인한 사용자만 글 작성, 작성자만 수정·삭제 | 구현 완료 |
| 로그아웃 | blog 세션과 accounts 로그인을 함께 종료 | 구현 완료 |
| 이미지 업로드 | 글에 사진을 넣으면 S3에 업로드하고 이미지 주소를 본문에 삽입 | 예정 |

## 아키텍처
```
사용자 브라우저
   │
   ▼
Frontend (Next.js, Vercel)
   │  REST API (JSON), 세션 쿠키 + CSRF 토큰
   ▼
Cloudflare (DNS, HTTPS 프록시)
   │
   ▼
AWS Elastic Beanstalk ── Spring Boot 애플리케이션
   │                 │
   ▼                 ▼
Amazon RDS MySQL   Amazon S3 (예정)
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
| Database | Amazon RDS for MySQL (운영), H2 (로컬, 테스트), Flyway (스키마 관리) |
| Infra | AWS Elastic Beanstalk, Amazon RDS, Vercel (화면), Cloudflare (DNS, HTTPS), (예정) Amazon S3 |
| CI/CD | GitHub Actions (`main`에 push하면 테스트 후 Elastic Beanstalk에 배포) |

## 저장소 구성
| 폴더 | 내용 |
|---|---|
| [`backend/`](./backend) | Spring Boot REST API 서버. API 명세, 인증 흐름, 실행 방법은 [backend/README.md](./backend/README.md) 참고 |
| [`frontend/`](./frontend) | 블로그 화면 (Next.js). 홈, 글 상세, 글 쓰기·수정. 실행 방법은 [frontend/README.md](./frontend/README.md) 참고 |
| [`note/`](./note) | 프로젝트를 만들며 공부한 내용 정리 |

## 관련 저장소
| 저장소 | 내용 |
|---|---|
| [accounts](https://github.com/51SB-ANTIMONY/accounts) | antimony.kr 통합 로그인 서버 (OIDC 인가 서버 + 로그인 화면). 이 저장소의 `accounts/` 폴더에서 시작해 분리했다 |

## 개발 이력
| 시기 | 내용 |
|---|---|
| 2026.07 | 첫 버전을 별도 저장소에서 개발했다. 블로그 하나만 생각하고 로그인도 블로그 안에서 처리했는데, 배포해 운영하면서 다른 앱을 함께 묶으려 하자 구조가 맞지 않고 코드가 점점 꼬였다. |
| 2026.09 | 여러 앱이 하나의 로그인을 함께 쓰는 구조로 처음부터 계획을 세우고, 이 저장소에서 다시 시작했다. 첫 버전의 개발 순서를 따라가다가 로그인 기능부터 통합 로그인(accounts)으로 방향을 바꿨다. |
| 2026.10 | 통합 로그인 서버를 [accounts](https://github.com/51SB-ANTIMONY/accounts) 저장소로 분리했다. 이 과정에서 커밋 이력을 다시 정리해, 많은 커밋의 날짜가 2026.10.03로 표시된다. |
