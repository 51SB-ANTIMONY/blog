---
title: antimony.kr 통합 로그인 설계
date: 2026-10-03
tags: [sso, oidc, authorization-server, bff, design]
---

# antimony.kr 통합 로그인 설계

> 대형 서비스의 구조는 [대형 서비스의 통합 로그인(SSO) 구조](./01_big_tech_sso.md)에서 조사했다.
> 이 노트는 그 구조를 antimony.kr에 적용하는 설계안이다. 아직 구현 전이며, 구현하면서 바뀔 수 있다.

## 1. 목표
- `accounts.antimony.kr`이 antimony.kr 전체의 로그인을 담당한다.
- 한 번 로그인하면 antimony.kr의 모든 앱(`blog.antimony.kr` 등)에서 로그인이 유지된다.
- 이 저장소 밖의 다른 저장소에서 만든 앱도 accounts로 로그인할 수 있다.
- 로그인 방식은 **아이디·비밀번호**와 **소셜 로그인(Google 등)**을 함께 지원한다.

## 2. 전체 구조
```
                           Google (외부 IdP)
                              ▲  "Google로 로그인"
                              │
            ┌─────────────────┴──────── accounts.antimony.kr ─────────────────┐
            │ OpenID Connect 인가 서버                                          │
            │  · 로그인 화면: 아이디·비밀번호 / 소셜 로그인                       │
            │  · 로그인 세션 쿠키 (accounts 전용)  ← SSO의 근거                   │
            │  · 등록된 앱 목록 (client_id, redirect_uri, scope)                 │
            │  · /.well-known/openid-configuration                             │
            │  · /oauth2/authorize  /oauth2/token  /oauth2/jwks  /userinfo     │
            │  · users, user_identities 테이블                                  │
            └───────▲────────────────────▲────────────────────▲───────────────┘
                    │ OIDC               │ OIDC               │ OIDC
             blog.antimony.kr      app2.antimony.kr     (다른 저장소의 앱)
             client_id=blog        client_id=app2
```
| 역할 | 서버 |
|---|---|
| IdP (OIDC 인가 서버) | `accounts.antimony.kr` |
| RP (OIDC 클라이언트) | `blog.antimony.kr`, 이후의 모든 앱 |
| 외부 IdP | Google (이후 네이버, 카카오 추가 가능) |

accounts는 각 앱에게는 IdP이고, Google에게는 클라이언트다. Google 로그인 결과를 받아 자기 회원으로 연결한 뒤, 자기 이름으로 토큰을 발급한다.

## 3. SSO가 동작하는 방식
```
[처음 blog 접속]
blog → 302 accounts.antimony.kr/oauth2/authorize?client_id=blog&...
accounts: 세션 없음 → 로그인 화면
          → 아이디·비밀번호 또는 Google 로그인 → 세션 생성
          Set-Cookie: <세션>; (accounts.antimony.kr 전용) HttpOnly; Secure; SameSite=Lax
accounts → 302 blog.antimony.kr/login/oauth2/code/accounts?code=...
blog: 코드를 토큰으로 교환 → blog 로그인 완료

[그 다음 app2 접속]
app2 → 302 accounts.antimony.kr/oauth2/authorize?client_id=app2&...
accounts: 세션 있음 → 로그인 화면 없이
accounts → 302 app2/...?code=...
app2: 로그인 완료
```

**accounts 세션 쿠키의 `SameSite`는 `Lax`여야 한다.** Google 로그인 후 `accounts.google.com`에서 accounts로 돌아오는 리다이렉트는 다른 사이트에서 시작된 요청이다. `Strict`면 이때 세션 쿠키가 전송되지 않는다([쿠키](../14_oauth/03_cookie.md) 6장).

**공유 쿠키(`Domain=antimony.kr`) 방식을 쓰지 않는 이유**
자세한 비교와 OIDC 설명은 [공유 쿠키 방식과 OpenID Connect](./03_shared_cookie_vs_oidc.md)에 정리했다.

| | 공유 쿠키 | OIDC |
|---|---|---|
| 하위 도메인 하나가 뚫리면 | 모든 앱의 로그인 쿠키가 그 서버로 전송됨 | 각 앱은 자기 토큰만 가짐 |
| 앱별 권한, 앱별 차단 | 어려움 | `client_id`, `aud`, `scope`로 구분 |
| 다른 언어·프레임워크 앱 | 쿠키 형식과 키를 직접 맞춰야 함 | 표준 OIDC 라이브러리로 연결 |
| antimony.kr 밖의 도메인 | 불가능 | 가능 |

## 4. accounts 구현 방법
| 선택지 | 내용 |
|---|---|
| **Spring Authorization Server** (선택) | Spring 기반 OIDC 인가 서버. 권한 부여 코드, PKCE, 토큰 발급, JWKS, 설정 문서, 클라이언트 등록 저장소를 제공 |
| Keycloak | 완성된 IdP 서버. 설정만으로 동작 |
| 직접 구현 | 표준을 정확히 맞추기 어렵고 보안 실수 위험이 큼 |

Spring Authorization Server를 쓰면 accounts에는 다음이 들어간다.
- 인가 서버 설정: 토큰 서명 키(RS256), 설정 문서, 토큰 수명
- 로그인: 폼 로그인(아이디·비밀번호) + `oauth2Login()`(Google)
- 등록된 클라이언트 저장소(DB)
- 회원가입, 비밀번호 재설정, 소셜 계정 연결 화면

## 5. 회원 데이터 설계
"계정"과 "로그인 수단"을 나눈다.
```
users                                    user_identities
├── id              PK  ← 모든 앱의 sub  ├── id               PK
├── email           UNIQUE               ├── user_id          → users.id
├── email_verified                       ├── provider         GOOGLE / NAVER / KAKAO
├── password_hash   NULL 가능            ├── provider_subject 그 제공자의 sub
├── nickname                             ├── created_at
├── created_at                           └── UNIQUE(provider, provider_subject)
└── updated_at
```
| 결정 | 이유 |
|---|---|
| 앱에 주는 `sub` = `users.id` (`public` 방식) | 모든 앱이 같은 사용자를 같은 값으로 식별. 이메일은 바뀔 수 있으므로 쓰지 않음 |
| 소셜 사용자는 **제공자의 `sub`**로 찾는다 | 이메일 기준으로 찾으면 이메일이 바뀌거나 다른 제공자의 같은 이메일과 섞일 수 있음 |
| `password_hash`는 NULL 가능 | 소셜 로그인만 쓰는 회원 |
| 한 회원에 여러 로그인 수단 | 비밀번호와 Google을 함께 연결 가능 |

### 계정 연결 정책
소셜 로그인의 이메일이 기존 비밀번호 계정과 같을 때:
| 정책 | 장점 | 위험 |
|---|---|---|
| 자동 연결 | 편함 | 이메일 확인이 안 된 계정이 합쳐지면 계정 탈취 가능 |
| **명시적 연결** (선택) | 안전 | 사용자가 "기존 계정으로 로그인 → 설정에서 Google 연결"을 해야 함 |

## 6. 토큰
| 토큰 | 발급 | 용도 |
|---|---|---|
| ID 토큰 (JWT) | accounts → 앱 | "이 사용자는 누구다". `iss`, `sub`, `aud`(=client_id), `exp`, `iat`, `nonce`, `amr` |
| 액세스 토큰 (JWT) | accounts → 앱 | 앱의 API 호출. `aud`, `scope` |
| 리프레시 토큰 | accounts → 앱 (서버 앱만) | 액세스 토큰 재발급 |

- 서명: RS256. 개인 키는 accounts에만 있고, 공개 키는 `/oauth2/jwks`로 공개한다.
- 앱은 `iss = https://accounts.antimony.kr`, `aud`에 자기 `client_id`가 있는지 확인한다.
- `amr`로 로그인 방법(`pwd`, `fed`)을 알 수 있다.

## 7. 앱이 붙는 방식

### 서버가 있는 앱 (BFF 방식) - blog가 쓸 방식
```
브라우저 (React 등) ──쿠키(앱 세션)──▶ blog.antimony.kr 서버 ──Bearer──▶ blog API
                                        │ OIDC 클라이언트 (기밀)
                                        │ 토큰은 서버 세션에 보관
                                        ▼
                                  accounts.antimony.kr
```
> **BFF란?**
> Backend For Frontend. 프론트엔드 전용 서버가 OIDC 클라이언트가 되어 토큰을 서버에 보관하고, 브라우저에는 `HttpOnly` 세션 쿠키만 주는 구조다. IETF의 "OAuth 2.0 for Browser-Based Applications" 문서가 브라우저 앱에서 가장 안전한 방식으로 소개한다.

| 장점 | 주의할 점 |
|---|---|
| 토큰이 JavaScript에 노출되지 않음 (XSS로 토큰 탈취 불가) | 앱 서버가 세션을 가짐 (상태 있음) |
| Client Secret을 서버에 안전하게 보관 | 쿠키 인증이므로 CSRF 방어 필요 (`SameSite=Lax` + CSRF 토큰) |
| 프론트엔드와 API가 같은 출처라 CORS 불필요 | |

### 서버가 없는 SPA, 모바일 앱
공개 클라이언트(Client Secret 없음) + PKCE. 토큰은 메모리에 보관한다. 다른 저장소의 앱 중 서버가 없는 앱은 이 방식으로 붙는다.

### 등록 정보 예시
| client_id | 종류 | redirect_uri | 인증 방식 |
|---|---|---|---|
| `blog` | 기밀 (BFF) | `https://blog.antimony.kr/login/oauth2/code/accounts` | `client_secret_basic` + PKCE |
| `app2-spa` | 공개 | `https://app2.antimony.kr/callback` | `none` + PKCE |

## 8. 로그아웃
| 범위 | 동작 |
|---|---|
| 앱만 로그아웃 | 앱 세션 삭제. accounts 세션은 남아 있으므로 다시 로그인하면 바로 들어옴 |
| antimony.kr 전체 로그아웃 | 앱 세션 삭제 → accounts의 로그아웃 엔드포인트로 이동(OIDC RP-Initiated Logout) → accounts 세션 삭제 |
| 다른 앱에도 알림 | accounts가 각 앱 서버에 로그아웃을 알림(OIDC Back-Channel Logout). 필요할 때 추가 |

처음에는 "앱 로그아웃 + 전체 로그아웃" 두 가지만 구현한다.

## 9. 보안 체크리스트
- [ ] 모든 통신 HTTPS (`*.antimony.kr` 인증서)
- [ ] 쿠키: `HttpOnly`, `Secure`, `SameSite=Lax`
- [ ] 비밀번호: BCrypt, 로그인 실패 횟수 제한
- [ ] `redirect_uri`는 등록된 값과 정확히 일치할 때만 허용
- [ ] 모든 클라이언트 PKCE 사용
- [ ] Client Secret, 서명 개인 키는 환경 변수 또는 비밀 관리 서비스에 보관
- [ ] 서명 키 교체 절차 (`kid`로 여러 키 공개)

## 10. 로컬 개발
- accounts `:9000`, blog `:8080`처럼 포트를 나눠 실행한다.
- 실제와 비슷한 쿠키 동작을 확인하려면 hosts 파일에 `accounts.antimony.test`, `blog.antimony.test`를 `127.0.0.1`로 등록해 쓴다.
