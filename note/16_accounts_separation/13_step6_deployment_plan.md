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

→ **결과: 넘기지 않았다.** [트러블슈팅 4](#4-로그인-화면-주소가-accounts-api로-만들어졌다)에서 고쳤다.

## 6. 비용 (대략, 서울 리전, 가장 작은 사양)
| 항목 | 월 비용 | 프리 티어 (가입 후 12개월) |
|---|---|---|
| EC2 2대 (`t3.micro`) | 약 2만 원 | 750시간(1대분) 무료 |
| ~~로드밸런서~~ | 0 (단일 인스턴스) | |
| RDS (`db.t4g.micro`, 20GB) | 약 2만 원 | 750시간 무료 |
| Vercel | 무료 (Hobby) | |

단일 인스턴스로 로드밸런서 비용(2개 약 4~5만 원)을 없앴다. EC2 2대는 프리 티어 750시간을 넘으므로(2대 × 720시간) 1대분은 요금이 나온다. 나중에 로드밸런서가 필요하면 Elastic Beanstalk의 **공유 로드밸런서**(로드밸런서 하나를 두 환경이 나눠 씀)와 ②에서 발급한 ACM 인증서를 쓴다.

## 7. 배포 중 바꾼 것
| 바꾼 것 | 내용 |
|---|---|
| accounts 첫 화면(`/`) | 전에는 무조건 `/account`로 보내고, 화면이 뜬 뒤 로그인이 안 되어 있으면 `/login`으로 다시 보냈다(내 계정 화면이 잠깐 보였다). 이제 `proxy.ts`가 화면을 띄우기 전에 Spring의 `/api/me`로 로그인 여부를 확인해, 로그인되어 있으면 `/account`, 아니면 `/login`으로 보낸다 |

## 8. 트러블슈팅

### 1. 서버가 시작하지 못했다: 서명 키를 읽지 못함
- **증상**: accounts-api 배포 후 Spring이 시작하지 못했다. 로그에 `Input byte array has wrong 4-byte ending unit`.
- **원인**: 환경 속성 `SIGNING_KEY_BASE64`(1624자)를 붙여 넣을 때 끝이 잘렸다. 메모장의 자동 줄 바꿈 때문에 한 줄 전체가 선택되지 않았다.
- **해결**: 메모장의 자동 줄 바꿈을 끄고, 줄 처음에서 Shift+End로 한 줄 전체를 복사해 다시 넣었다.

### 2. RDS 보안 그룹에 규칙을 추가하지 못했다
- **증상**: RDS의 `default` 보안 그룹 인바운드 규칙에 주소를 넣으니 `You may not specify an IPv4 CIDR for an existing referenced group id rule`.
- **원인**: 이미 "다른 보안 그룹에서 오는 요청 허용"으로 만들어진 규칙의 원본 칸에 IP 주소 범위를 넣으려 했다. 한 규칙에는 둘 중 하나만 쓸 수 있다.
- **해결**: 기존 규칙을 고치지 않고, **새 규칙**(MySQL/Aurora 3306, 원본 `172.31.0.0/16` = 기본 VPC 안)을 추가하거나 `default` 보안 그룹을 Elastic Beanstalk 인스턴스에 붙이는 방법 중 하나로 해결했다.

### 3. HTTPS가 연결되지 않았다: 단일 인스턴스
- **증상**: `https://accounts-api.antimony.kr`이 시간 초과. Elastic Beanstalk 구성에 리스너(로드밸런서) 설정 메뉴가 없었다.
- **원인**: 환경을 **단일 인스턴스**로 만들어서 로드밸런서가 없었다. ACM 인증서는 로드밸런서에만 붙일 수 있고, EC2의 443 포트는 닫혀 있었다.
- **해결**: 단일 인스턴스를 유지하고(프리 티어), Cloudflare 프록시 + 원본 인증서 + EC2 안의 Nginx 443으로 바꿨다([3. 구성](#3-구성)).

### 4. 로그인 화면 주소가 accounts-api로 만들어졌다
- **증상**: `https://accounts.antimony.kr/oauth2/authorize?...`의 응답이 `Location: https://accounts-api.antimony.kr/login`. 브라우저가 Cloudflare 원본 인증서를 쓰는 API 주소로 가게 된다.
- **원인 찾기**: 같은 요청을 Cloudflare 주소(`accounts-api`)로 직접 보내며 `X-Forwarded-Host: accounts.antimony.kr`을 넣으면 `accounts.antimony.kr/login`이 나왔다. Cloudflare와 Nginx는 헤더를 그대로 넘긴다. **Vercel을 거칠 때 사용자가 접속한 주소가 빠진다.**
- **첫 시도 (실패)**: `next.config.ts`의 rewrites를 `proxy.ts`로 옮기고 `X-Forwarded-Host`, `X-Forwarded-Proto`를 직접 붙였다. 로컬에서는 맞게 나왔지만, push한 뒤에도 Vercel에서는 그대로 `accounts-api`였다. Vercel이 바깥 서버로 넘길 때 `X-Forwarded-Host`를 자기 값으로 다시 쓰는 것으로 보인다(Vercel 배포가 끝난 시점이었는지는 확인하지 못했다. 어느 쪽이든 아래 해결로 상관없어진다).
  - 로컬 확인 중 하나 더: Next.js가 자기 서버 포트를 `X-Forwarded-Port`로 붙여, 주소가 `accounts.antimony.kr:3101/login`처럼 나왔다.
- **두 번째 시도 (실패)**: 표준 헤더 **`Forwarded: host="accounts.antimony.kr";proto=https`**(RFC 7239)로 보냈다. Spring은 `Forwarded`가 있으면 `X-Forwarded-*`를 보지 않으므로, Vercel이 `X-Forwarded-Host`를 바꿔도 상관없을 것이라 봤다. 실제 API에 `Forwarded`와 틀린 `X-Forwarded-Host`를 함께 보내 `Forwarded`가 이기는 것도 확인했다. 그러나 Vercel에 배포된 뒤(같은 `proxy.ts`의 첫 화면 이동은 동작하는 것으로 배포를 확인)에도 그대로 `accounts-api`였다. **Vercel은 `proxy.ts`에서 바꾼 요청 헤더를 바깥 서버까지 넘기지 않는 것으로 보인다.** 어느 헤더가 어디서 빠지는지는 Vercel 내부라 확인하지 못했다.
- **해결**: 헤더 전달을 Vercel에 맡기지 않는다. **EC2의 Nginx가 `Forwarded: host=accounts.antimony.kr;proto=https`를 붙여** Spring에 넘긴다(blog는 `blog.antimony.kr`). 저장소마다 Nginx 설정이 따로 있으므로 각자의 공개 주소를 적었다. `proxy.ts`의 `Forwarded`는 로컬 개발용으로 남긴다.
  - 이 서버로 직접 들어오는 요청(blog Spring이 토큰을 교환하는 요청 등)도 같은 주소로 보이지만, 리다이렉트를 만들지 않는 요청이고 issuer는 설정값으로 고정되어 있어 영향이 없다.
  - 나중에 AWS를 떠나면, 옮겨 갈 곳의 프록시에서 같은 헤더를 붙이거나 Spring에 공개 주소를 설정한다.

> **Forwarded 헤더(RFC 7239)란?**
> 중간 서버(프록시)가 "원래 요청이 어떤 주소, 어떤 방식(http/https)으로 들어왔는지"를 다음 서버에 알리는 표준 헤더다. `X-Forwarded-Host`, `X-Forwarded-Proto`는 이 표준이 나오기 전부터 쓰던 비표준 헤더다.

### 5. 배포 훅에 실행 권한이 붙지 않았다
- **증상**: `ebBundle`이 만든 zip 안의 `01_tls_certificate.sh`가 실행 권한 없이(`rw-r--r--`) 들어갔다. 그대로 올리면 Elastic Beanstalk이 인증서를 내려받지 못하고, Nginx가 시작하지 못한다.
- **원인**: `filesMatching('.platform/hooks/**')`를 작업 전체에 걸었는데, `from('.platform') { into '.platform' }` 안의 파일은 그 경로로 맞지 않았다.
- **해결**: `from('.platform')` 블록 안에 `filesMatching('hooks/**')`로 옮겼다. 배포 전에 `unzip -Z`로 `rwxr-xr-x`인 것을 확인했다.

