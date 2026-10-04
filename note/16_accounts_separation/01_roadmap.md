---
title: accounts 분리하기 - 전체 로드맵
date: 2026-10-03
tags: [sso, oidc, accounts, roadmap]
---

# accounts 분리하기 - 전체 로드맵

> 왜 OIDC로 통합 로그인을 만드는지, 전체 설계는 [15_sso](../15_sso)에 정리했다.
> 이 폴더는 blog 안에 있던 로그인을 **accounts**라는 별도 서버로 분리해 가는 과정을 단계별로 기록한다.
> 단계마다 **목적, 세부 단계, 구체적인 수행 방안**을 먼저 정하고, 구현한 뒤 결과와 겪은 문제를 남긴다.

## 1. 전체 목적
blog 서버가 직접 하던 로그인(Google 로그인, JWT 발급)을 떼어 내, antimony.kr 전체가 함께 쓰는 로그인 서버 `accounts.antimony.kr`을 만든다.

| 목표 | 내용 |
|---|---|
| 통합 로그인 (SSO) | accounts에서 한 번 로그인하면 antimony.kr의 모든 앱에서 로그인이 유지된다 |
| 다른 저장소의 앱도 연결 | 이 저장소 밖에서 만든 앱도 OIDC 표준으로 accounts에 붙을 수 있다 |
| 로그인 방식 | 아이디·비밀번호와 소셜 로그인(Google, 이후 카카오·네이버)을 함께 지원한다 |
| 화면 | accounts, blog 모두 Next.js로 만든다 |

> **OIDC(OpenID Connect)란?**
> OAuth 2.0 위에 "누가 로그인했는지"를 알려 주는 ID 토큰을 더한 표준이다. 로그인을 맡는 서버(인가 서버, 여기서는 accounts)와 로그인을 맡기는 앱(클라이언트, 여기서는 blog)이 이 표준대로만 통신하면, 서로의 코드를 몰라도 연결된다.

## 2. 지키는 원칙
| 원칙 | 이유 |
|---|---|
| accounts는 `blog/accounts/` 폴더에서 시작하되, **완전히 독립**시킨다 (자체 Gradle, `.gitignore`, README. blog 코드 import 금지) | 나중에 이 폴더만 떼어 새 저장소로 옮겨도 그대로 빌드되게 |
| 커밋 하나에는 `accounts/` 안의 파일만, 또는 밖의 파일만 넣는다 | `git subtree split --prefix=accounts`로 accounts 이력만 깔끔하게 뽑아내려고 |
| blog와 accounts는 **설정값(주소, client_id, Secret)과 OIDC 표준**으로만 연결한다 | 두 서버가 서로의 내부를 몰라도 동작하게 |
| "옮기기"와 "구조 바꾸기"를 다른 커밋으로 나눈다 | 각 커밋에서 무엇이 바뀌었는지 diff로 분명히 보이게 |
| 화면이 필요하면 프로토타입(디자인 아티팩트)에 먼저 만들고, 그대로 구현한다. 모바일도 함께 만든다 | 화면을 코드에서 즉흥적으로 정하지 않으려고 |
| 단계마다 자동 테스트와, 실제 서버를 띄운 흐름 확인을 함께 한다 | 테스트 환경(MockMvc)과 실제 서버의 차이에서 생기는 문제를 놓치지 않으려고 |

## 3. 단계 한눈에 보기
| 단계 | 목적 | 상태 | 문서 |
|---|---|---|---|
| **0단계** blog 준비 | accounts로 옮기기 전에 blog 코드를 정리한다 | 완료 | [0단계](./02_step0_blog_preparation.md) |
| **1단계** accounts 서버 | blog의 로그인 코드를 accounts로 옮기고, 표준 OIDC 인가 서버와 로그인 화면을 만든다 | 완료 | [계획](./03_step1_accounts_server.md), [구현](./04_step1_accounts_implementation.md), [확인·트러블슈팅](./05_step1_accounts_verification_and_troubleshooting.md) |
| **2단계** blog를 OIDC 클라이언트로 | blog가 로그인을 직접 하지 않고 accounts에 맡긴다 | 완료 | [계획](./06_step2_blog_oidc_client_plan.md), [구현·확인·트러블슈팅](./07_step2_blog_oidc_client.md) |
| **3단계** blog 화면을 Next.js로 | 임시 Thymeleaf 화면을 Next.js로 바꾼다 | 완료 (브라우저 확인 남음) | [계획](./08_step3_blog_frontend_plan.md), [구현·확인·트러블슈팅](./09_step3_blog_frontend.md) |
| **4단계** 저장소 분리 | `accounts/`를 새 Git 저장소(`51SB-ANTIMONY/accounts`)로 옮긴다 | 완료 | [계획·결과](./10_step4_repository_split.md) |
| **5단계** 운영 준비 | 운영에 필요한 코드와 설정: MySQL, Flyway, 서명 키·토큰·세션 영구 저장, 메일, 환경 변수, HTTPS 대비. 미뤄 둔 브라우저 확인 포함 | 완료 (발송 횟수 제한·로컬 리허설은 배포 뒤로) | [계획](./11_step5_production_readiness_plan.md), [구현·확인·트러블슈팅](./12_step5_production_readiness.md) |
| **6단계** 실제 배포 | Elastic Beanstalk + RDS + Vercel + Cloudflare로 실제 주소에서 운영한다 | 배포 완료 (브라우저 전체 흐름 확인 남음) | [구성·순서·트러블슈팅](./13_step6_deployment_plan.md), [CI/CD](./14_step6_cicd.md) |
| **7단계** 아이디·비밀번호 설정과 변경 | 소셜로만 가입한 회원이 아이디·비밀번호를 정하고, 비밀번호가 있는 회원이 로그인한 채로 비밀번호를 바꾼다 | 구현 완료 (운영 확인 남음) | [계획·구현·확인](./15_step7_account_credentials_plan.md) |

## 4. 6단계 실제 배포 (아직 세부 계획 전)
코드와 설정은 5단계에서 준비하고, 6단계에서는 실제로 운영할 곳을 꾸린다.

| 할 일 | 내용 |
|---|---|
| 호스팅 | Spring 서버 2개(accounts, blog), Next.js 2개, MySQL을 어디서 실행할지 (후보: AWS Elastic Beanstalk + RDS, Vercel 등) |
| 도메인, HTTPS | `accounts.antimony.kr`, `blog.antimony.kr`의 DNS와 인증서 |
| 비밀 값 | 운영용 Client Secret, 로그인 상태 유지 키, 서명 키, DB 비밀번호를 새로 만들어 환경 변수로 |
| 메일 서비스 | SMTP 제공자 (Gmail SMTP, AWS SES 등) |
| Google 로그인 | 운영 주소의 승인된 리디렉션 URI 등록, OAuth 동의 화면 게시 |
| CI/CD | 저장소마다 GitHub Actions로 테스트, 빌드, 배포 |
| 화면 문구 | 약관, 개인정보 처리방침, 소개 문구 (내용은 사용자가 쓴다) |

## 5. 현재 구조 (4단계 이후)
```
브라우저
  ├─ localhost:3000  blog frontend (Next.js)        홈, 글 상세, 글 쓰기·수정          ← blog 저장소
  │        │ rewrites: /api/**, /oauth2/**, /login/oauth2/**, /logout
  │        ▼
  │   localhost:8080  blog backend (Spring)          글 API, 로그인 처리, 세션 BLOG_SESSION, CSRF
  │        │ 서버끼리: 토큰 교환, 공개 키
  │        ▼
  │   localhost:9000  accounts backend (Spring Authorization Server)   ← accounts 저장소
  │        ▲ rewrites
  └─ localhost:3001  accounts frontend (Next.js)    로그인, 회원가입, 내 계정
```

## 6. 이 폴더의 문서
| 문서 | 내용 |
|---|---|
| [01 로드맵](./01_roadmap.md) | 이 문서 |
| [02 0단계: blog 준비](./02_step0_blog_preparation.md) | 목적, 세부 단계, 수행 방안, 결과 |
| [03 1단계: accounts 서버 (계획)](./03_step1_accounts_server.md) | 목적, 세부 단계 1-1~1-8, 수행 방안 |
| [04 1단계: 구현 과정](./04_step1_accounts_implementation.md) | 단계별 코드와 이유 |
| [05 1단계: 확인과 트러블슈팅](./05_step1_accounts_verification_and_troubleshooting.md) | 자동 테스트, 실제 흐름, 겪은 문제 14개 |
| [06 2단계: blog를 OIDC 클라이언트로 (계획)](./06_step2_blog_oidc_client_plan.md) | 목적, 세부 단계 2-1~2-6, 수행 방안 |
| [07 2단계: 구현, 확인, 트러블슈팅](./07_step2_blog_oidc_client.md) | 단계별 코드, blog의 JWT를 지운 이유, 확인 결과, 겪은 문제 |
| [08 3단계: blog 화면을 Next.js로 (계획)](./08_step3_blog_frontend_plan.md) | 목적, 세부 단계 3-0~3-7, 수행 방안, 정한 것 |
| [09 3단계: 구현, 확인, 트러블슈팅](./09_step3_blog_frontend.md) | API 보완, Next.js 뒤에 두기, 화면 구현, Thymeleaf 제거, 확인 결과, 겪은 문제 |
| [10 4단계: 저장소 분리](./10_step4_repository_split.md) | 목적, 분리 전 점검, 세부 단계 4-1~4-6, 수행 방안, 결과 |
| [11 5단계: 운영 준비 (계획)](./11_step5_production_readiness_plan.md) | 목적, 정한 것(MySQL, Docker는 나중에), 세부 단계 5-1~5-8, 수행 방안 |
| [12 5단계: 구현, 확인, 트러블슈팅](./12_step5_production_readiness.md) | 설정 분리, Flyway, 서명 키·토큰·세션 영구 저장, HTTPS 대비, 헬스 체크 |
| [13 6단계: 실제 배포](./13_step6_deployment_plan.md) | 구성, 콘솔에서 따라 할 순서, 환경 변수, 배포 뒤 확인, 비용, 트러블슈팅 |
| [14 6단계: CI/CD](./14_step6_cicd.md) | CI/CD와 GitHub Actions 개념, 워크플로 코드 설명, 예제에서 고친 것, 알아 둘 점 |
| [15 7단계: 아이디·비밀번호 설정과 변경](./15_step7_account_credentials_plan.md) | 배포 뒤 확인에서 나온 문제, 정한 것, 세부 단계 7-1~7-5, 결과와 확인 |
