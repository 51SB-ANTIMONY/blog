---
title: accounts 분리하기 - 5단계 운영 준비 (목적과 계획)
date: 2026-10-03
tags: [production, mysql, flyway, spring-session, configuration]
---

# accounts 분리하기 - 5단계 운영 준비 (목적과 계획)

> 전체 단계는 [로드맵](./01_roadmap.md)에 있다. 이 단계는 accounts 저장소와 blog 저장소 모두에서 진행한다.

## 1. 목적
**accounts와 blog를 실제 주소(`accounts.antimony.kr`, `blog.antimony.kr`)에서 운영할 수 있게 코드와 설정을 바꾼다.** 실제 서버를 꾸리고 배포하는 일은 6단계에서 한다.

지금은 두 서비스 모두 로컬 개발용이다.

| 항목 | 지금 | 운영에 필요한 것 |
|---|---|---|
| DB | H2 메모리. 재시작하면 회원과 글이 사라진다 | MySQL |
| 테이블 | Hibernate가 엔티티를 보고 자동으로 만든다 | SQL 스크립트로 관리 (Flyway) |
| accounts 서명 키 | 켤 때마다 새로 만든다. 재시작하면 발급한 토큰을 검증할 수 없다 | 저장해 둔 키를 읽는다 |
| accounts 앱 등록, 발급한 토큰 | 메모리 | DB |
| 세션 | 서버 메모리. 재시작하면 모두 로그아웃된다 | DB (Spring Session JDBC) |
| 인증 메일 | 서버 로그에 출력 | SMTP로 발송, 발송 횟수 제한 |
| 비밀 값과 주소 | Git에서 제외한 로컬 `application.yml` | 환경 변수 |
| 자동 테스트 | Git에서 제외한 로컬 `application.yml`에 기대서, 다른 컴퓨터와 CI에서는 실패한다 | 테스트 전용 설정을 저장소에 둔다 |
| 쿠키, 주소 | `http://localhost`, `Secure` 없음 | `https://`, `Secure` 쿠키 |
| 화면 | 브라우저 확인을 하지 않았다 (3단계에서 미룸) | 브라우저로 확인 |

**이 단계에서 하지 않는 것:** 서버·도메인·HTTPS·CI/CD 구성(6단계), 약관과 소개 문구(내용은 사용자가 쓴다), 준비 중인 블로그 기능.

> **환경 변수란?**
> 프로그램 바깥(서버 설정)에서 프로그램에 넘겨주는 값이다. 비밀 값을 코드나 설정 파일에 넣지 않고 서버에만 두려고 쓴다. Spring은 `ACCOUNTS_ISSUER` 같은 환경 변수를 `accounts.issuer` 설정으로 읽을 수 있다.

## 2. 정한 것
| 질문 | 결정 |
|---|---|
| 단계 나누기 | 5단계 운영 준비(코드·설정), 6단계 실제 배포(서버, 도메인, HTTPS, CI/CD) |
| DB | **MySQL** |
| Docker | 지금은 쓰지 않는다. 5-7 로컬 리허설 때 다시 정한다. 그때를 위해 주소와 비밀 값은 모두 환경 변수로 받는다. 옮길 가능성을 생각하면 6단계 배포는 Docker 이미지가 유리하다 |
| 배포 대상 | 처음에는 AWS. DB와 백엔드는 나중에 다른 곳으로 옮길 수 있다 (아래 "이식성") |
| 로컬 개발 | 지금처럼 H2 메모리 DB로 빠르게 실행한다. MySQL은 `prod` 프로필에서 쓴다 |

### 다른 곳으로 옮길 수 있게 (이식성)
처음에는 AWS에 배포하지만, **DB와 백엔드 서버는 나중에 AWS가 아닌 곳으로 옮길 수 있다.** 그래서 특정 클라우드에만 있는 기능에 기대지 않는다.

> **이식성, 벤더 종속이란?**
> 이식성은 프로그램을 다른 실행 환경으로 옮길 때 고쳐야 할 것이 적은 정도다. 특정 회사만 제공하는 기능에 기댈수록 옮기기 어려워지는데, 이것을 벤더 종속(lock-in)이라고 한다.

| 원칙 | 이렇게 한다 | 피하는 것 (AWS 전용) |
|---|---|---|
| 설정은 환경 변수로만 | DB 주소, 비밀 값, 서비스 주소 모두 환경 변수 (5-1) | 코드에서 Secrets Manager, Parameter Store를 직접 부르기. AWS에서는 배포 설정이 값을 환경 변수로 넣어 주게만 한다 |
| DB는 표준 MySQL | 표준 SQL, Flyway 스크립트 (5-2). 옮길 때는 `mysqldump`로 내보내고 복원 | Aurora 전용 기능, IAM DB 인증 |
| 세션도 같은 MySQL에 | Spring Session JDBC (5-4) | ElastiCache 전용 설정 |
| 메일은 SMTP로 | `spring.mail.*` (5-5). AWS SES를 쓰더라도 SMTP 방식으로 연결 | SES 전용 SDK |
| 서명 키는 파일로 | PEM 파일 경로를 환경 변수로 (5-3) | AWS KMS로 서명 |
| 실행 단위는 표준으로 | JAR, 나아가 Docker 이미지 | Elastic Beanstalk 전용 설정(`.ebextensions`)에 많은 것을 넣기 |
| (나중) 이미지 저장 | 저장 기능을 인터페이스로 감싸고 S3 호환 API를 쓴다 | 코드 곳곳에서 AWS SDK를 직접 부르기 |

**옮길 때 함께 옮길 것:** accounts 서명 키(바뀌면 앱이 가진 토큰을 검증할 수 없다), DB 데이터. **도메인은 그대로 두고** DNS가 가리키는 곳만 바꾼다. issuer, redirect_uri, Google의 승인된 리디렉션 URI가 모두 도메인 기준이라 바꿀 필요가 없다.

### Docker 없이 MySQL을 시험하는 방법
MySQL을 테스트 중에 잠깐 띄우는 도구(Testcontainers)는 Docker가 필요하다. 그래서 두 가지로 나눈다.

| 확인 | 방법 | 한계 |
|---|---|---|
| 자동 테스트 | H2를 MySQL 호환 모드(`MODE=MySQL`)로 띄우고, Flyway 스크립트로 테이블을 만든 뒤 엔티티와 테이블이 맞는지 검사(`ddl-auto: validate`) | H2는 MySQL을 흉내 낼 뿐이다. MySQL에서만 생기는 문제는 잡지 못한다 |
| 실제 MySQL | 5-7 리허설에서 진짜 MySQL로 서버를 띄워 확인 | 사람이 직접 실행해야 한다 |

Flyway 스크립트는 두 DB 모두에서 도는 표준 SQL로 쓴다.

> **Flyway란?**
> DB 테이블을 만들고 바꾸는 SQL을 `V1__init.sql`, `V2__add_category.sql`처럼 번호를 붙여 저장해 두고, 서버가 켜질 때 아직 적용하지 않은 것만 순서대로 실행하는 도구다. 어떤 스크립트를 적용했는지는 DB의 `flyway_schema_history` 테이블에 기록한다.

> **왜 Hibernate의 자동 생성을 그만 쓰나**
> `ddl-auto: create`는 서버를 켤 때마다 테이블을 지우고 다시 만든다. `update`는 지우지는 않지만, 컬럼 이름 변경이나 데이터 옮기기 같은 일을 할 수 없고 무엇이 바뀌었는지 기록도 남지 않는다. 운영 데이터가 있는 DB는 사람이 쓴 SQL로, 순서대로, 기록을 남기며 바꿔야 한다.

## 3. 세부 단계
| 단계 | 내용 | 저장소 |
|---|---|---|
| 5-1 | 설정 정리: 공통·로컬·운영(`prod`) 프로필 분리, 비밀 값과 주소를 환경 변수로, 테스트 전용 설정 | 두 저장소 |
| 5-2 | MySQL과 Flyway: 테이블 생성 스크립트, `prod`에서 MySQL 연결 | 두 저장소 |
| 5-3 | accounts 영구 저장: 서명 키를 파일에서 읽기, 앱 등록·발급 토큰·동의 정보를 DB에 | accounts |
| 5-4 | 세션을 DB에 (Spring Session JDBC) | 두 저장소 |
| 5-5 | 메일: SMTP 발송 설정, 인증번호 발송 횟수 제한 | accounts |
| 5-6 | HTTPS 대비: `Secure` 쿠키, `X-Forwarded-Proto` 전달 확인 | 두 저장소 |
| 5-7 | 로컬 운영 리허설: MySQL과 `prod` 설정으로 네 서버를 띄워 전체 흐름 확인, 미뤄 둔 브라우저 확인 | 두 저장소 |
| 5-8 | 기록: 노트, README | 두 저장소 |

## 4. 수행 방안

### 5-1. 설정 정리
| 파일 | 내용 | Git |
|---|---|---|
| `application.yml` | 공통 설정 (포트, 쿠키 이름, `forward-headers-strategy`). 주소와 비밀 값은 `${ACCOUNTS_ISSUER:http://localhost:3001}`처럼 환경 변수로 읽고, 없으면 로컬 기본값 | **올린다** |
| `application-local.yml` | 로컬 전용 비밀 값 (Google Client Secret 등) | 제외 |
| `application-prod.yml` | 운영 설정 (MySQL, SQL 출력 끄기, H2 콘솔 끄기). 값은 모두 환경 변수 | 올린다 |
| `src/test/resources/application.yml` | 테스트 전용 설정. 가짜 비밀 값 | 올린다 |

지금의 Git 제외 `application.yml`은 `application-local.yml`로 바꾸고, 로컬에서는 `local` 프로필로 실행한다. 저장소를 처음 받은 사람은 `application-local.yml.example`을 복사해 쓴다.

> **프로필이란?**
> Spring에서 환경마다 다른 설정을 고르는 방법이다. `application.yml`은 항상 읽고, `application-prod.yml`은 `prod` 프로필로 켰을 때만 더해서 읽는다. 같은 설정이 둘 다에 있으면 프로필 쪽 값이 이긴다.

확인: 로컬 `application.yml` 없이 `./gradlew test`가 통과한다.

### 5-2. MySQL과 Flyway
- 의존성: `spring-boot-starter-flyway`, `flyway-mysql`, MySQL JDBC 드라이버.
- `src/main/resources/db/migration/V1__init.sql`: 지금 엔티티의 테이블(accounts: `users`, `user_identities`, `email_verifications`, `pending_signups` / blog: `users`, `article`).
- `ddl-auto`는 `validate`로 바꿔, 엔티티와 스크립트가 다르면 서버가 켜지지 않게 한다.
- blog의 예시 글(`import.sql`)은 로컬 프로필에서만 넣는다.
- 확인: 테스트(H2 MySQL 모드)에서 Flyway가 스크립트를 적용하고 `validate`를 통과한다.

### 5-3. accounts 영구 저장
| 할 일 | 방법 |
|---|---|
| 서명 키 | RSA 개인 키를 PEM 파일로 만들어 두고, 경로를 환경 변수로 받는다. 키가 없는 로컬에서는 지금처럼 켤 때 만든다 |
| 앱 등록 | `JdbcRegisteredClientRepository`. 서버를 켤 때 설정값으로 blog 클라이언트를 등록(이미 있으면 갱신) |
| 발급한 토큰, 동의 | `JdbcOAuth2AuthorizationService`, `JdbcOAuth2AuthorizationConsentService`. Spring Authorization Server가 제공하는 테이블 스크립트를 Flyway 스크립트로 둔다 |
| 확인 | 서버를 재시작해도 이전에 받은 리프레시 토큰으로 재발급되고, 이전 ID 토큰의 서명이 검증된다 |

> **PEM이란?**
> 키나 인증서를 `-----BEGIN PRIVATE KEY-----`로 시작하는 텍스트로 저장하는 형식이다. 서명 키가 바뀌지 않아야 앱(blog)이 이전에 받은 토큰을 계속 검증할 수 있다.

### 5-4. 세션을 DB에
- `spring-boot-starter-session-jdbc`(Spring Session JDBC). 세션 테이블(`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`)을 Flyway 스크립트로 둔다.
- 쿠키 이름(`ACCOUNTS_SESSION`, `BLOG_SESSION`)은 그대로 쓴다.
- 확인: 로그인한 뒤 서버를 재시작해도 로그인이 유지된다. 서버를 여러 대로 늘려도 같은 세션을 본다.

> **Spring Session JDBC란?**
> 세션을 서버 메모리 대신 DB 테이블에 저장하게 해 주는 라이브러리다. 서버를 재시작하거나 여러 대로 늘려도 같은 세션을 쓸 수 있다.

### 5-5. 메일
- `prod` 프로필에서 `spring.mail.*`를 환경 변수로 받는다. 설정되면 1-7의 `SmtpVerificationMailSender`가 실제로 보낸다. 메일 서비스(Gmail SMTP, AWS SES 등)는 6단계에서 정한다.
- 발송 횟수 제한: 같은 이메일로 인증번호 재전송은 1분에 1번, 하루 10번까지. 넘으면 429(Too Many Requests)와 안내 문구. 화면에도 이 문구를 보여 준다.

### 5-6. HTTPS 대비
| 할 일 | 방법 |
|---|---|
| 세션, 로그인 상태 유지, CSRF 쿠키 | `prod`에서 `Secure`. HTTPS 연결에서만 브라우저가 보낸다 |
| 원래 요청이 HTTPS였다는 정보 | 운영에서는 브라우저 → (HTTPS 종료 지점) → Next.js → Spring 순서로 전달된다. Spring이 `https://` 주소로 리다이렉트하려면 `X-Forwarded-Proto: https`가 Spring까지 와야 한다. Next.js rewrites가 이 헤더를 넘기는지 확인하고, 넘기지 않으면 Next.js에서 붙인다 |

> **`Secure` 쿠키란?**
> HTTPS 연결에서만 브라우저가 서버로 보내는 쿠키다. 이 설정이 없으면 암호화되지 않은 HTTP 요청에도 세션 쿠키가 실려 가로챌 수 있다.

### 5-7. 로컬 운영 리허설
- Docker를 쓸지 이때 정한다. 쓰지 않으면 WSL에 MySQL과 JDK 25를 설치하고 네 서버를 모두 WSL에서 실행한다.
- `prod` 프로필 + 환경 변수로 실행: 가입, 로그인, SSO, 글 쓰기, 재시작 후 로그인 유지, 재시작 후 토큰 재발급, 로그아웃.
- 3단계에서 미룬 브라우저 확인: PC·모바일, 라이트·다크, 로그인 후 원래 화면으로 돌아오기.

### 5-8. 기록
이 폴더에 5단계 구현 노트, 두 저장소의 README(실행 방법, 환경 변수 목록).
