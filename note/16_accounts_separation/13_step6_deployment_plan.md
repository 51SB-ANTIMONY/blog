---
title: accounts 분리하기 - 6단계 실제 배포 (목적, 구성, 순서)
date: 2026-10-04
tags: [deployment, aws, elastic-beanstalk, rds, vercel, cloudflare]
---

# accounts 분리하기 - 6단계 실제 배포 (목적, 구성, 순서)

> 전체 단계는 [로드맵](./01_roadmap.md)에, 배포 전에 준비한 코드와 설정은 [5단계](./12_step5_production_readiness.md)에 있다.

## 1. 목적
accounts와 blog를 실제 주소에서 운영한다. **최대한 빨리 배포부터** 하고, 서버 구동을 막는 문제가 아니면 배포 뒤에 고친다.

## 2. 정한 것
| 질문 | 결정 |
|---|---|
| Spring 서버 2개 | **AWS Elastic Beanstalk** (Java 플랫폼에 JAR 업로드). Docker는 쓰지 않는다. 나중에 바꿀 수 있다 |
| DB | **AWS RDS for MySQL** 1개에 DB 2개(`accounts`, `blog`) |
| Next.js 화면 2개 | **Vercel**. S3 정적 호스팅은 서버에서 화면을 그리거나 요청을 넘길 수 없어서 쓰지 않는다 |
| S3 | 나중에 이미지 업로드 저장소로 |
| DNS | **Cloudflare** |

## 3. 구성
```
브라우저 ─HTTPS─▶ accounts.antimony.kr, blog.antimony.kr       Vercel (Next.js 2개)
                        │ rewrites, 서버에서 화면을 그릴 때 조회
                        ▼ HTTPS
                  accounts-api.antimony.kr, blog-api.antimony.kr  Elastic Beanstalk
                  (로드밸런서 + ACM 인증서 → EC2의 Spring JAR)
                        │
                        ▼
                  RDS MySQL (accounts, blog)
```
| 주소 | 가리키는 곳 | 쓰는 쪽 |
|---|---|---|
| `accounts.antimony.kr` | Vercel (accounts 화면) | 브라우저. OIDC issuer |
| `blog.antimony.kr` | Vercel (blog 화면) | 브라우저 |
| `accounts-api.antimony.kr` | Elastic Beanstalk (accounts Spring) | accounts 화면(Vercel), blog Spring(토큰 교환) |
| `blog-api.antimony.kr` | Elastic Beanstalk (blog Spring) | blog 화면(Vercel) |

**Vercel과 Spring 사이도 HTTPS로 한다.** 이 구간도 인터넷을 지나고, 로그인할 때 비밀번호가 지나가기 때문이다. 그래서 Elastic Beanstalk에 로드밸런서와 무료 인증서(ACM)를 붙인다.

> **ACM(AWS Certificate Manager)이란?**
> AWS가 HTTPS 인증서를 무료로 발급하고 자동으로 갱신해 주는 서비스다. 로드밸런서에 붙여 쓴다. 발급받으려면 그 도메인의 주인임을 DNS 레코드로 증명한다.

## 4. 순서

### 준비물
| 준비물 | 위치 |
|---|---|
| accounts JAR | `Desktop/accounts/backend/build/libs/accounts-0.0.1-SNAPSHOT.jar` |
| blog JAR | `Desktop/blog/backend/build/libs/blog-0.0.1-SNAPSHOT.jar` |
| 운영 비밀 값 | `Desktop/antimony-prod-secrets.txt` (Git 밖. 비밀번호 관리자로 옮긴 뒤 지운다) |
| AWS 리전 | 서울 (`ap-northeast-2`). 모든 서비스를 같은 리전에 만든다 |

### ① RDS (MySQL)
1. RDS → 데이터베이스 생성 → MySQL 8.4, 템플릿 "프리 티어", `db.t4g.micro`(또는 `db.t3.micro`).
2. 관리자 이름 `admin`, 비밀번호는 비밀 값 파일의 `DB_PASSWORD`.
3. 퍼블릭 액세스 **아니요**. VPC는 기본 VPC.
4. 만들어지면 **엔드포인트**(`xxx.ap-northeast-2.rds.amazonaws.com`)를 적어 둔다.

DB(`accounts`, `blog`)는 따로 만들지 않는다. 주소에 `createDatabaseIfNotExist=true`를 붙여 서버가 처음 연결할 때 만든다.

### ② ACM 인증서
1. ACM(서울 리전) → 퍼블릭 인증서 요청 → 도메인 `accounts-api.antimony.kr`, `blog-api.antimony.kr`, DNS 검증.
2. ACM이 보여 주는 CNAME 레코드 2개를 Cloudflare DNS에 추가한다. **프록시 끔(회색 구름, DNS only)**.
3. 상태가 "발급됨"이 될 때까지 기다린다(보통 몇 분).

### ③ Elastic Beanstalk: accounts
1. 애플리케이션 `antimony-accounts`, 환경 `accounts-api` (웹 서버 환경).
2. 플랫폼: **Java**, 플랫폼 브랜치 **Corretto 25**. (목록에 25가 없으면 알려 준다. 빌드 대상을 21로 바꿔야 한다)
3. 애플리케이션 코드: accounts JAR 업로드.
4. "추가 옵션 구성"(사용자 지정 구성):
   - 용량: 로드 밸런싱, 인스턴스 최소 1 최대 1, `t3.micro`(또는 `t3.small`)
   - 로드 밸런서: Application Load Balancer. 리스너 추가 → **443, HTTPS, ②의 인증서**. 프로세스의 헬스 체크 경로 **`/actuator/health`**
   - 환경 속성(환경 변수): 아래 표
5. 환경이 만들어지면 RDS의 보안 그룹에 **이 환경의 EC2 보안 그룹에서 오는 3306 포트**를 허용한다.

| 이름 | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | `5000` (Elastic Beanstalk의 Nginx가 이 포트로 넘긴다) |
| `DB_URL` | `jdbc:mysql://<RDS 엔드포인트>:3306/accounts?createDatabaseIfNotExist=true&preserveInstants=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` |
| `DB_USERNAME` | `admin` |
| `DB_PASSWORD` | 비밀 값 파일의 `DB_PASSWORD` |
| `ACCOUNTS_ISSUER` | `https://accounts.antimony.kr` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google Cloud Console의 값 |
| `BLOG_CLIENT_SECRET` | 비밀 값 파일 |
| `BLOG_REDIRECT_URI` | `https://blog.antimony.kr/login/oauth2/code/accounts` |
| `BLOG_POST_LOGOUT_REDIRECT_URI` | `https://blog.antimony.kr/` |
| `REMEMBER_ME_KEY` | 비밀 값 파일 |
| `SIGNING_KEY_BASE64` | 비밀 값 파일 |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | (선택) Gmail이면 `smtp.gmail.com`, `587`, Gmail 주소, 앱 비밀번호. 비워 두면 인증번호가 서버 로그에만 남는다 |

### ④ Elastic Beanstalk: blog
③과 같게 하되 애플리케이션 `antimony-blog`, 환경 `blog-api`, blog JAR.

| 이름 | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | `5000` |
| `DB_URL` | `jdbc:mysql://<RDS 엔드포인트>:3306/blog?createDatabaseIfNotExist=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` |
| `DB_USERNAME`, `DB_PASSWORD` | ③과 같음 |
| `ACCOUNTS_ISSUER` | `https://accounts.antimony.kr` |
| `ACCOUNTS_BACKCHANNEL_URI` | `https://accounts-api.antimony.kr` |
| `ACCOUNTS_CLIENT_SECRET` | 비밀 값 파일의 `BLOG_CLIENT_SECRET`과 같은 값 |

### ⑤ Cloudflare: API 주소
| 이름 | 종류 | 값 | 프록시 |
|---|---|---|---|
| `accounts-api` | CNAME | accounts-api 환경의 주소 (`xxx.ap-northeast-2.elasticbeanstalk.com`) | 끔 |
| `blog-api` | CNAME | blog-api 환경의 주소 | 끔 |

확인: `https://accounts-api.antimony.kr/actuator/health`와 `https://blog-api.antimony.kr/actuator/health`가 `{"status":"UP"}`.

### ⑥ Vercel
1. 새 프로젝트 → GitHub의 `accounts` 저장소 → **Root Directory `frontend`** → 환경 변수 `ACCOUNTS_BACKEND_URL=https://accounts-api.antimony.kr` → 배포.
2. 새 프로젝트 → `blog` 저장소 → Root Directory `frontend` → 환경 변수 `BLOG_BACKEND_URL=https://blog-api.antimony.kr`, `NEXT_PUBLIC_ACCOUNTS_URL=https://accounts.antimony.kr` → 배포.
3. 각 프로젝트의 Settings → Domains에 `accounts.antimony.kr`, `blog.antimony.kr`를 추가한다.
4. Cloudflare에 CNAME `accounts`, `blog` → `cname.vercel-dns.com`, **프록시 끔**.

> `NEXT_PUBLIC_`으로 시작하는 환경 변수는 빌드할 때 화면 코드에 들어간다. 바꾸면 다시 배포해야 한다.

### ⑦ Google 로그인
Google Cloud Console의 OAuth 클라이언트에 승인된 리디렉션 URI `https://accounts.antimony.kr/login/oauth2/code/google`을 추가한다. OAuth 동의 화면이 "테스트 중"이면 테스트 사용자로 등록한 계정만 로그인할 수 있다.

## 5. 배포 뒤 확인
| 확인 | 기대 결과 |
|---|---|
| `https://blog.antimony.kr` | 글 목록 (처음에는 비어 있음) |
| 회원가입 | 인증번호가 메일로 온다 (SMTP를 설정하지 않았으면 accounts 환경의 로그에서 확인) |
| blog에서 로그인 | accounts 로그인 화면 → 로그인 → blog로 돌아와 닉네임 |
| 글 쓰기·수정·삭제 | 동작 |
| 로그아웃 | accounts 로그인도 끝남 |
| 서버 재시작 후 | 로그인 유지 (세션이 DB에 있음) |
| PC·모바일, 라이트·다크 | 프로토타입대로 (3단계에서 미룬 브라우저 확인) |

### 배포해 봐야 아는 것
**Vercel이 `X-Forwarded-Host`를 넘기는지.** Spring은 이 헤더로 "사용자가 접속한 주소"를 알아서 redirect_uri와 리다이렉트 주소를 만든다. 로컬의 Next.js는 넘긴다는 것을 확인했다. Vercel도 넘기지 않으면, 로그인 후 `blog-api.antimony.kr`로 돌아가는 식으로 주소가 어긋난다. 그러면 Spring에 공개 주소를 직접 설정하는 방식으로 고친다.

## 6. 비용 (대략, 서울 리전, 가장 작은 사양)
| 항목 | 월 비용 | 프리 티어 (가입 후 12개월) |
|---|---|---|
| EC2 2대 (`t3.micro`) | 약 2만 원 | 750시간(1대분) 무료 |
| 로드밸런서 2개 | 약 4~5만 원 | 750시간(1개분) 무료 |
| RDS (`db.t4g.micro`, 20GB) | 약 2만 원 | 750시간 무료 |
| Vercel | 무료 (Hobby) | |

로드밸런서가 가장 크다. 나중에 Elastic Beanstalk의 **공유 로드밸런서**(로드밸런서 하나를 두 환경이 나눠 씀)로 바꾸면 하나 값으로 줄일 수 있다.
