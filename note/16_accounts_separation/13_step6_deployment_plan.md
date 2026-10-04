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
| Elastic Beanstalk 환경 종류 | **단일 인스턴스** (로드밸런서 없음). 프리 티어에서 무료로 쓸 수 있어서 |
| Vercel과 Spring 사이 HTTPS | 로드밸런서가 없어 ACM 인증서를 붙일 곳이 없다. **Cloudflare 프록시 + Full (strict) + Cloudflare 원본 인증서**로 한다 |

## 3. 구성
```
브라우저 ─HTTPS─▶ accounts.antimony.kr, blog.antimony.kr       Vercel (Next.js 2개)
                        │ rewrites, 서버에서 화면을 그릴 때 조회
                        ▼ HTTPS
                  accounts-api.antimony.kr, blog-api.antimony.kr  Cloudflare (프록시)
                        │ HTTPS (원본 인증서)
                        ▼
                  Elastic Beanstalk 단일 인스턴스
                  (EC2 안의 Nginx 443 → Spring 5000)
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

**Vercel과 Spring 사이도 HTTPS로 한다.** 이 구간도 인터넷을 지나고, 로그인할 때 비밀번호가 지나가기 때문이다.

처음에는 로드밸런서에 무료 인증서(ACM)를 붙이려 했다. 그런데 환경을 **단일 인스턴스**로 만들어서 로드밸런서가 없다. 단일 인스턴스를 유지하기로 했으므로(프리 티어), 인증서를 다른 곳에서 처리한다.

| 구간 | 처리 |
|---|---|
| Vercel → Cloudflare | Cloudflare가 자기 인증서로 HTTPS를 받는다 |
| Cloudflare → EC2 | EC2 안의 Nginx가 443 포트에서 **Cloudflare 원본 인증서**로 HTTPS를 받는다. Cloudflare는 Full (strict) 모드로 이 인증서를 확인한다 |
| Nginx → Spring | 같은 서버 안(127.0.0.1:5000)이라 HTTP |

> **ACM(AWS Certificate Manager)이란?**
> AWS가 HTTPS 인증서를 무료로 발급하고 자동으로 갱신해 주는 서비스다. 로드밸런서에 붙여 쓴다. 단일 인스턴스에는 붙일 수 없다. 발급해 둔 인증서는 쓰지 않지만 비용이 들지 않으므로 그대로 둔다(나중에 로드밸런서로 바꾸면 쓴다).

> **Cloudflare 프록시란?**
> DNS 레코드의 주황 구름을 켜면, 요청이 AWS 서버로 바로 가지 않고 Cloudflare 서버를 거쳐 간다. 바깥에서 보이는 HTTPS 인증서는 Cloudflare가 처리한다.

> **원본 인증서(Origin Certificate)란?**
> Cloudflare가 무료로 발급하는, Cloudflare와 우리 서버 사이 구간용 인증서다(최대 15년). 브라우저는 믿지 않지만 Cloudflare는 믿는다. 그래서 이 인증서를 쓰는 주소는 반드시 Cloudflare 프록시를 켜서 접속한다.

> **SSL 모드 Flexible, Full (strict)란?**
> Cloudflare가 우리 서버에 어떻게 접속할지 정하는 설정이다. Flexible은 HTTP로 접속해 이 구간이 암호화되지 않는다. Full (strict)는 HTTPS로 접속하고, 우리 서버의 인증서가 믿을 수 있는 것(원본 인증서 포함)인지 확인한다.

### HTTPS를 위해 저장소에 추가한 파일 (accounts, blog의 `backend/`에 같은 내용)
| 파일 | 하는 일 |
|---|---|
| `.platform/nginx/conf.d/https.conf` | Nginx가 443에서 원본 인증서로 HTTPS를 받아 Spring(5000)으로 넘긴다. `X-Forwarded-Proto: https`를 붙인다 |
| `.platform/hooks/prebuild/01_tls_certificate.sh` | 배포할 때 S3에서 인증서(`origin.crt`)와 개인 키(`origin.key`)를 내려받는다. 위치는 환경 속성 `TLS_CERT_S3_URI` |
| `.ebextensions/https-instance.config` | EC2 보안 그룹에 443 포트를 연다 |
| `Procfile` | `java -jar application.jar`로 실행 |
| `.gitattributes` | 위 파일들의 줄 끝을 LF로 고정 (Windows에서 CRLF로 바뀌면 Linux에서 스크립트가 실행되지 않는다) |
| `build.gradle`의 `ebBundle` 작업 | JAR(`application.jar`로 이름 바꿈), `Procfile`, `.platform`, `.ebextensions`를 묶어 `build/distributions/<이름>-eb.zip`을 만든다. 배포 훅에 실행 권한을 준다 |

개인 키는 비밀이므로 저장소에 넣지 않고 S3에 둔다. Elastic Beanstalk이 EC2에 주는 기본 권한(`aws-elasticbeanstalk-ec2-role`)은 이름이 `elasticbeanstalk-`로 시작하는 버킷을 읽을 수 있으므로, Elastic Beanstalk이 만들어 둔 버킷을 쓴다.

> **배포 훅(platform hook)이란?**
> Elastic Beanstalk이 배포하는 도중 정해진 시점에 실행하는 스크립트다. `prebuild`는 애플리케이션과 Nginx 설정을 적용하기 전에 실행된다. 그래서 Nginx가 시작할 때 인증서 파일이 이미 있다.

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

### ② ~~ACM 인증서~~ → Cloudflare 원본 인증서
ACM 인증서는 발급했지만 단일 인스턴스라 쓰지 않는다. 대신:
1. Cloudflare → `antimony.kr` → SSL/TLS → **Origin Server** → **Create Certificate**. 키 종류 RSA, 호스트 이름 `*.antimony.kr`, `antimony.kr`(기본값), 유효 기간 15년.
2. 나오는 **Origin Certificate**를 `origin.crt`, **Private Key**를 `origin.key`로 저장한다(형식 PEM). 개인 키는 이 화면에서만 볼 수 있다. 저장소에 넣지 않는다.
3. S3 → `elasticbeanstalk-ap-northeast-2-<계정 ID>` 버킷(Elastic Beanstalk이 만든 것) → `tls/origin/` 폴더에 두 파일을 올린다. 퍼블릭 액세스는 막힌 그대로 둔다.
4. 이 위치 `s3://elasticbeanstalk-ap-northeast-2-<계정 ID>/tls/origin`을 아래 환경 속성 `TLS_CERT_S3_URI`에 쓴다. accounts와 blog가 같은 인증서를 쓴다.

### ③ Elastic Beanstalk: accounts
1. 애플리케이션 `antimony-accounts`, 환경 `accounts-api` (웹 서버 환경).
2. 플랫폼: **Java**, 플랫폼 브랜치 **Corretto 25**. (목록에 25가 없으면 알려 준다. 빌드 대상을 21로 바꿔야 한다)
3. 애플리케이션 코드: **`./gradlew ebBundle`로 만든 `build/distributions/accounts-eb.zip`** 업로드. (JAR만 올리면 HTTPS 설정이 빠진다)
4. "추가 옵션 구성"(사용자 지정 구성):
   - 용량: **단일 인스턴스**, `t3.micro`
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
| `TLS_CERT_S3_URI` | `s3://elasticbeanstalk-ap-northeast-2-<계정 ID>/tls/origin` (②) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | (선택) Gmail이면 `smtp.gmail.com`, `587`, Gmail 주소, 앱 비밀번호. 비워 두면 인증번호가 서버 로그에만 남는다 |

### ④ Elastic Beanstalk: blog
③과 같게 하되 애플리케이션 `antimony-blog`, 환경 `blog-api`, `build/distributions/blog-eb.zip`.

| 이름 | 값 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | `5000` |
| `DB_URL` | `jdbc:mysql://<RDS 엔드포인트>:3306/blog?createDatabaseIfNotExist=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` |
| `DB_USERNAME`, `DB_PASSWORD` | ③과 같음 |
| `ACCOUNTS_ISSUER` | `https://accounts.antimony.kr` |
| `ACCOUNTS_BACKCHANNEL_URI` | `https://accounts-api.antimony.kr` |
| `ACCOUNTS_CLIENT_SECRET` | 비밀 값 파일의 `BLOG_CLIENT_SECRET`과 같은 값 |
| `TLS_CERT_S3_URI` | ③과 같음 |

### ⑤ Cloudflare: API 주소
| 이름 | 종류 | 값 | 프록시 |
|---|---|---|---|
| `accounts-api` | CNAME | accounts-api 환경의 주소 (`xxx.ap-northeast-2.elasticbeanstalk.com`) | **켬 (주황 구름)** |
| `blog-api` | CNAME | blog-api 환경의 주소 | **켬** |

SSL 모드는 이 두 주소에만 Full (strict)를 건다(영역 전체 설정을 바꾸면 다른 하위 도메인에도 영향이 간다).
- Cloudflare → Rules → **Configuration Rules** → Create rule → 조건 "Hostname is in `accounts-api.antimony.kr`, `blog-api.antimony.kr`" → SSL: **Full (strict)** → 배포.

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
**Vercel이 `X-Forwarded-Host`를 넘기는지.** Spring은 이 헤더로 "사용자가 접속한 주소"를 알아서 redirect_uri와 리다이렉트 주소를 만든다. 로컬의 Next.js는 넘긴다는 것을 확인했다. Vercel도 넘기지 않으면, 로그인 후 `blog-api.antimony.kr`로 돌아가는 식으로 주소가 어긋난다.

→ **결과: Vercel은 넘기지 않았다.** `accounts.antimony.kr/oauth2/authorize`의 리다이렉트가 `https://accounts-api.antimony.kr/login`으로 만들어졌다. 같은 요청을 Cloudflare 주소로 직접 보내며 헤더를 넣으면 `accounts.antimony.kr/login`이 나와서, Cloudflare와 Nginx는 헤더를 그대로 넘기고 Vercel의 rewrites만 빠뜨린다는 것을 확인했다.

**고친 방법:** `next.config.ts`의 rewrites를 지우고, 두 화면 저장소에 `frontend/proxy.ts`를 만들었다. Spring이 맡는 경로를 여기서 Spring으로 넘기면서 헤더를 직접 붙인다.
| 헤더 | 값 |
|---|---|
| `X-Forwarded-Host` | 브라우저가 보낸 Host (`accounts.antimony.kr`, 로컬은 `localhost:3001`) |
| `X-Forwarded-Proto` | 브라우저가 접속한 방식 (`https`, 로컬은 `http`) |
| `X-Forwarded-Port` | 지운다. Next.js가 자기 서버 포트를 붙여 보내서, 로컬 확인에서 `accounts.antimony.kr:3101/login`처럼 포트가 섞였다 |

> **proxy.ts란?**
> Next.js 16에서 요청이 화면에 닿기 전에 실행되는 코드다(15까지의 이름은 middleware). `matcher`에 적은 경로만 실행되고, `NextResponse.rewrite`로 요청을 다른 서버에 넘길 때 요청 헤더를 바꿔서 보낼 수 있다.

## 6. 비용 (대략, 서울 리전, 가장 작은 사양)
| 항목 | 월 비용 | 프리 티어 (가입 후 12개월) |
|---|---|---|
| EC2 2대 (`t3.micro`) | 약 2만 원 | 750시간(1대분) 무료 |
| ~~로드밸런서~~ | 0 (단일 인스턴스) | |
| RDS (`db.t4g.micro`, 20GB) | 약 2만 원 | 750시간 무료 |
| Vercel | 무료 (Hobby) | |

단일 인스턴스로 로드밸런서 비용(2개 약 4~5만 원)을 없앴다. EC2 2대는 프리 티어 750시간을 넘으므로(2대 × 720시간) 1대분은 요금이 나온다. 나중에 로드밸런서가 필요하면 Elastic Beanstalk의 **공유 로드밸런서**(로드밸런서 하나를 두 환경이 나눠 씀)와 ②에서 발급한 ACM 인증서를 쓴다.
