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
| **3단계** blog 화면을 Next.js로 | 임시 Thymeleaf 화면을 Next.js로 바꾼다 | 계획 (결정 대기) | [계획](./08_step3_blog_frontend_plan.md) |
| 이후: 배포 준비 | accounts를 운영 환경에서 쓸 수 있게 한다 | 예정 | 아래 4장 |
| 이후: 저장소 분리 | `accounts/`를 새 Git 저장소로 옮긴다 | 예정 | 아래 4장 |

## 4. 이후 단계 (아직 세부 계획 전)
### 배포 준비
| 할 일 | 이유 |
|---|---|
| 서명 키를 저장해 두고 재사용 | 지금은 서버를 켤 때마다 새로 만들어서, 재시작하면 발급한 토큰을 검증할 수 없다 |
| 회원, 등록된 앱, 발급한 토큰을 영구 DB에 저장 | 지금은 메모리 DB(H2)라 재시작하면 사라진다 |
| 인증 메일을 실제로 발송 (SMTP) | 지금은 서버 로그에만 인증번호를 찍는다 |
| 인증번호 발송 횟수 제한 | 같은 이메일로 메일을 계속 보내게 하는 것을 막으려고 |
| 실제 주소와 HTTPS (`https://accounts.antimony.kr` 등), `Secure` 쿠키, `X-Forwarded-Proto` 전달 확인 | 지금은 `http://localhost` 기준이다 |
| 약관, 개인정보 처리방침, 소개 문구 | 화면의 자리 표시 문구를 실제 내용으로 |

### 저장소 분리
```bash
git subtree split --prefix=accounts -b accounts-only   # accounts/ 이력만 뽑은 브랜치
```
뽑은 브랜치를 새 저장소에 올린 뒤, blog 저장소에서는 `accounts/`를 지운다. 2장의 원칙을 지켜 왔으므로 코드 수정 없이 옮길 수 있어야 한다.

## 5. 현재 구조 (2단계 이후)
```
브라우저
  ├─ localhost:8080  blog (Spring)
  │     · 로그인: accounts에 맡김 (oauth2Login)
  │     · 세션 BLOG_SESSION, CSRF XSRF-TOKEN
  │        │ 서버끼리: 토큰 교환, 공개 키
  │        ▼
  │   localhost:9000  accounts backend (Spring Authorization Server)
  │        ▲ rewrites
  └─ localhost:3001  accounts frontend (Next.js) ← 브라우저: 로그인, 로그아웃 화면
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
| [08 3단계: blog 화면을 Next.js로 (계획)](./08_step3_blog_frontend_plan.md) | 목적, 세부 단계 3-0~3-7, 수행 방안, 정할 것 |
