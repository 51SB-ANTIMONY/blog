---
title: 대형 서비스의 통합 로그인(SSO) 구조 - Google, 네이버, 삼성
date: 2026-10-03
tags: [sso, oidc, oauth, idp, google, naver, samsung]
---

# 대형 서비스의 통합 로그인(SSO) 구조 - Google, 네이버, 삼성

> OAuth 2.0과 OpenID Connect는 [OAuth 2.0이란 무엇인가](../14_oauth/01_oauth.md), 권한 부여 코드 방식은 [권한 부여 코드 승인 방식](../14_oauth/02_authorization_code_grant.md), 쿠키의 도메인 범위는 [쿠키](../14_oauth/03_cookie.md)에서 설명했다.
> 이 노트의 설정 값은 2026-10-03에 각 회사가 공개한 OpenID Connect 설정 문서를 직접 받아 확인한 것이다.

## 1. 용어

> **SSO란?**
> Single Sign-On. 한 번 로그인하면 연결된 여러 서비스를 다시 로그인하지 않고 쓸 수 있게 하는 방식이다.

> **IdP란?**
> Identity Provider. 사용자를 인증하고, 다른 서비스에 "이 사용자는 누구다"라는 정보를 제공하는 서버다. Google 계정 서버, 네이버 계정 서버가 IdP다.

> **RP란?**
> Relying Party. IdP의 인증 결과를 믿고 사용하는 서비스다. OpenID Connect에서 클라이언트를 부르는 다른 이름이다.

> **OIDC 설정 문서(Discovery Document)란?**
> IdP가 `https://<issuer>/.well-known/openid-configuration` 주소에 공개하는 JSON 문서다. 권한 요청 주소, 토큰 주소, 공개 키 주소, 지원하는 기능 목록이 들어 있다. 클라이언트는 issuer 주소 하나만 알면 이 문서를 읽어서 나머지 설정을 자동으로 채운다. OpenID Connect Discovery 1.0에 정의되어 있다.

## 2. 세 회사의 공통 구조
```
                       계정 전용 도메인 (IdP)
         accounts.google.com / nid.naver.com / account.samsung.com
           · 아이디·비밀번호, 2단계 인증, 패스키 등 모든 로그인 화면
           · 로그인 세션 쿠키 (SSO의 근거)
           · OIDC 엔드포인트 (authorize, token, jwks, userinfo)
                │                              │
       자사 서비스 (1st party)              외부 서비스 (3rd party)
       같은 상위 도메인: 쿠키로 세션 공유      표준 OIDC 클라이언트로 등록
       다른 도메인: 리다이렉트로 교환          (client_id, redirect_uri)
```
| 공통점 | 내용 |
|---|---|
| 계정 전용 도메인 | 로그인 화면, 비밀번호 확인, 2단계 인증은 모두 이 도메인에서만 한다. 각 서비스는 로그인 화면을 갖지 않는다. |
| SSO의 근거 | 계정 도메인에 남은 **로그인 세션**. 다른 서비스가 로그인을 요청하면, 세션이 있는 경우 로그인 화면 없이 바로 결과를 돌려준다. |
| 외부 연동 | 표준 OAuth 2.0 / OpenID Connect. `/.well-known/openid-configuration` 공개 |
| 토큰 서명 | `RS256` (개인 키로 서명, 공개 키로 검증) |
| 공개 키 | JWKS 주소로 공개 |

## 3. Google

### 공개 OIDC 설정 (`https://accounts.google.com/.well-known/openid-configuration`)
| 항목 | 값 |
|---|---|
| `issuer` | `https://accounts.google.com` |
| `authorization_endpoint` | `https://accounts.google.com/o/oauth2/v2/auth` |
| `token_endpoint` | `https://oauth2.googleapis.com/token` |
| `userinfo_endpoint` | `https://openidconnect.googleapis.com/v1/userinfo` |
| `revocation_endpoint` | `https://oauth2.googleapis.com/revoke` |
| `jwks_uri` | `https://www.googleapis.com/oauth2/v3/certs` |
| `scopes_supported` | `openid`, `email`, `profile` |
| `response_types_supported` | `code`, `token`, `id_token` 및 그 조합, `none` |
| `grant_types_supported` | `authorization_code`, `refresh_token`, 디바이스 코드, JWT Bearer |
| `code_challenge_methods_supported` | `plain`, `S256` |
| `id_token_signing_alg_values_supported` | `RS256` |
| `token_endpoint_auth_methods_supported` | `client_secret_post`, `client_secret_basic` |

> 참고: Spring Security의 Google 기본값(`CommonOAuth2Provider.GOOGLE`)은 토큰 주소로 `https://www.googleapis.com/oauth2/v4/token`, 사용자 정보 주소로 `https://www.googleapis.com/oauth2/v3/userinfo`를 쓴다. 설정 문서의 주소와는 다르지만 둘 다 동작하는 주소다.

### 자사 서비스 간 SSO
- Google Cloud 문서: Google Cloud, YouTube를 포함한 Google 서비스는 기본적으로 Google 로그인 서비스로 사용자를 인증한다. 인증이 필요하면 `accounts.google.com`으로 보낸다.
- Google 쿠키 정책: `SID`, `HSID` 쿠키에 **Google 계정 ID와 최근 로그인 시각을 디지털 서명하고 암호화한 기록**이 담긴다. 이 쿠키 조합으로 폼 내용 탈취 같은 공격을 막는다. 유효 기간은 2년이다.

### 상위 도메인이 다른 자사 서비스: 도메인 간 인증
`google.com`의 쿠키는 `youtube.com`으로 전송되지 않는다. 쿠키는 그것을 설정한 도메인과 하위 도메인에만 가기 때문이다([쿠키](../14_oauth/03_cookie.md) 5장).

Google 특허 US8276190 "Cross-domain authentication"(2008년 출원, 2012년 등록)에 해결 방식이 나온다.
```
1. 사용자가 첫 도메인(예: mail.google.com)에 로그인 → 인증 쿠키
2. 두 번째 도메인(예: youtube.com) 페이지가 브라우저를 첫 도메인의 인증 서비스로 보냄
3. 첫 도메인이 브라우저의 인증 쿠키를 확인
4. 첫 도메인이 쿠키와 다른 "도메인 간 인증 정보"를 발급
5. 브라우저가 그것을 두 번째 도메인에 전달
6. 두 번째 도메인이 첫 도메인과 공유한 정보로 검증 → 자기 도메인에 로그인 상태 생성
```
사용자는 로그인 화면을 보지 않는다. 구조는 OIDC의 권한 부여 코드 방식과 같다. "도메인 간 인증 정보"가 권한 부여 코드, "공유한 정보로 검증"이 토큰 교환과 서명 검증에 해당한다.

## 4. 네이버

### 공개 OIDC 설정 (`https://nid.naver.com/.well-known/openid-configuration`)
| 항목 | 값 |
|---|---|
| `issuer` | `https://nid.naver.com` |
| `authorization_endpoint` | `https://nid.naver.com/oauth2/authorize` |
| `token_endpoint` | `https://nid.naver.com/oauth2/token` |
| `jwks_uri` | `https://nid.naver.com/oauth2/jwks` |
| `userinfo_endpoint` | `https://openapi.naver.com/v1/nid/me` |
| `scopes_supported` | `openid`, `profile` |
| `response_types_supported` | `code` |
| `grant_types_supported` | `authorization_code` |
| `token_endpoint_auth_methods_supported` | `client_secret_post`, `none` |
| `code_challenge_methods_supported` | `S256` |
| `subject_types_supported` | **`pairwise`** |
| `id_token_signing_alg_values_supported` | `RS256` |

### 특징
- **기존 API와 OIDC 엔드포인트가 따로 있다.** 기존 "네이버 아이디로 로그인"은 `/oauth2.0/authorize`, `/oauth2.0/token`을 쓰는 OAuth 2.0 방식이다. OIDC 엔드포인트는 `/oauth2/...` 경로에 따로 있다. 2026년 8월 네이버 공식 저장소(`naver/naveridlogin-API` 이슈 #92)에 OIDC 엔드포인트의 공식 지원 범위를 묻는 질문이 올라왔고 답변 완료 상태다.
- **`token_endpoint_auth_methods_supported`에 `none`이 있다.** Client Secret 없이 PKCE로 토큰을 받는 공개 클라이언트(SPA, 모바일 앱)를 지원한다.
- **`pairwise` subject:** 아래 6장.

### 자사 서비스 간 SSO
네이버 서비스는 대부분 `*.naver.com` 하위 도메인이다. 계정 도메인 `nid.naver.com`은 상위 도메인(`.naver.com`) 범위의 쿠키를 설정할 수 있으므로, 하위 도메인 서비스들이 같은 로그인 쿠키를 받는 구조가 가능하다. 개발자들 사이에서는 `NID_AUT`, `NID_SES` 쿠키로 알려져 있으나, 네이버 공식 문서에서는 확인하지 못했다.

## 5. 삼성

### 공개 OIDC 설정 (`https://api.account.samsung.com/.well-known/openid-configuration`)
| 항목 | 값 |
|---|---|
| `issuer` | `https://account.samsung.com/iam` |
| `authorization_endpoint` | `https://account.samsung.com/iam/oidc/authorize` |
| `token_endpoint` | `https://api.account.samsung.com/auth/oidc/token` |
| `userinfo_endpoint` | `https://api.account.samsung.com/v2/profile/user/oidc/userinfo` |
| `jwks_uri` | `https://account.samsung.com/.well-known/jwks` |
| `scopes_supported` | `openid`, `profile`, `email`, `phone`, `offline_access` |
| `response_types_supported` | `code`, `id_token`, `code id_token` |
| `response_modes_supported` | `query` |
| `grant_types_supported` | `authorization_code` |
| `acr_values_supported` | `urn:samsungaccount:acr:default`, `basic1`, `basic2`, `basic3`, `aal1` |
| `subject_types_supported` | `public` |
| `id_token_signing_alg_values_supported` | `RS256` |
| `token_endpoint_auth_methods_supported` | `client_secret_basic`, `client_secret_post` |

### 특징
- **`acr_values`(인증 수준):** 아래 7장.
- **`offline_access` scope:** OIDC에서 리프레시 토큰을 요청할 때 쓰는 표준 범위다.
- **자사 서비스:** 삼성 안내에 따르면 Samsung Health, Samsung Wallet, Galaxy Store, Samsung Cloud, SmartThings 등을 삼성 계정 하나로 로그인한다. 삼성 서비스는 도메인이 서로 다르므로 쿠키 공유보다 OIDC 같은 리다이렉트 방식이 중심이 된다. 갤럭시 기기에서는 기기에 등록된 삼성 계정이 앱들에 로그인 상태를 제공한다.

## 6. subject 방식: `public`과 `pairwise`
`sub`(subject)는 ID 토큰에서 사용자를 식별하는 값이다. 이 값을 앱마다 같게 줄지, 다르게 줄지를 정한다(OpenID Connect Core 8장).

| 방식 | 의미 | 예 |
|---|---|---|
| `public` | 모든 클라이언트에 **같은** `sub` | 삼성 |
| `pairwise` | 클라이언트(정확히는 클라이언트의 도메인)마다 **다른** `sub` | 네이버 |

**`pairwise`를 쓰는 이유:** 서로 다른 외부 앱 A와 B가 같은 사용자의 `sub`를 받으면, 두 회사가 `sub`를 맞춰 보고 사용자의 활동을 합칠 수 있다. 앱마다 다른 `sub`를 주면 이것이 불가능하다. 외부 앱이 많은 서비스의 개인정보 보호 방식이다.

**자사 앱끼리는 `public`이 편하다:** 블로그 글의 작성자를 다른 자사 앱에서도 같은 사용자로 알아봐야 할 때, `sub`가 같아야 한다.

## 7. 인증 수준: `acr`와 `amr`
| 클레임 | 이름 | 의미 | 예 |
|---|---|---|---|
| `acr` | Authentication Context Class Reference | 이 로그인이 **어느 수준**의 인증이었는가 | 삼성의 `basic1`, `aal1` |
| `amr` | Authentication Methods References | **어떤 방법**으로 인증했는가 (RFC 8176) | `pwd`(비밀번호), `otp`, `mfa`, `fed`(외부 IdP로 로그인) |

클라이언트는 권한 요청에 `acr_values=...`를 붙여 "이 수준 이상으로 인증해 달라"고 요청할 수 있다. 예를 들어 평소에는 비밀번호만으로 로그인하고, 결제나 비밀번호 변경 같은 민감한 작업 직전에만 2단계 인증을 다시 요구하는 데 쓴다(단계적 인증, step-up authentication).

## 8. 참고: 카카오
카카오도 같은 구조다. `https://kauth.kakao.com/.well-known/openid-configuration`

| 항목 | 값 |
|---|---|
| `issuer` | `https://kauth.kakao.com` |
| 엔드포인트 | authorize `kauth.kakao.com/oauth/authorize`, token `kauth.kakao.com/oauth/token`, userinfo `kapi.kakao.com/v1/oidc/userinfo`, jwks `kauth.kakao.com/.well-known/jwks.json` |
| `response_types_supported` | `code` |
| 서명 / PKCE | `RS256` / `S256` |

## 9. 비교
| 항목 | Google | 네이버 | 삼성 |
|---|---|---|---|
| 계정 도메인 | `accounts.google.com` | `nid.naver.com` | `account.samsung.com` |
| OIDC 설정 문서 | 공개 | 공개 (기존 OAuth 2.0 API와 별도 경로) | 공개 |
| 서명 | RS256 | RS256 | RS256 |
| PKCE | `plain`, `S256` | `S256` | (설정 문서에 표기 없음) |
| 공개 클라이언트 (`none`) | (표기 없음) | 지원 | (표기 없음) |
| subject | (표기 없음) | `pairwise` | `public` |
| 인증 수준 `acr` | (표기 없음) | (표기 없음) | 지원 |
| 자사 서비스 도메인 | 여러 개 (`google.com`, `youtube.com`, ...) | 대부분 `*.naver.com` | 여러 개 |
| 자사 SSO 방식 | 계정 도메인 세션 + 도메인 간 인증 교환 | 상위 도메인 쿠키 | 계정 하나로 OIDC, 기기 계정 |

## 10. 정리
- 대형 서비스는 모두 **계정 전용 도메인 하나**가 로그인을 전담한다.
- SSO는 **그 도메인의 로그인 세션**으로 이루어진다. 다른 서비스는 리다이렉트로 "이 사용자 누구인가"를 묻는다.
- 외부 앱에는 **표준 OIDC**로 로그인을 제공하고, 설정 문서와 공개 키를 공개한다.
- 상위 도메인이 같은 자사 서비스는 쿠키를 공유할 수도 있지만, 도메인이 다르면 결국 OIDC와 같은 리다이렉트 교환이 필요하다.

이 구조를 antimony.kr에 적용하는 설계는 [antimony.kr 통합 로그인 설계](./02_antimony_accounts_design.md)에 정리했다.

## 출처
- [Google OpenID Connect 설정 문서](https://accounts.google.com/.well-known/openid-configuration)
- [Google Cloud – Single sign-on](https://docs.cloud.google.com/architecture/identity/single-sign-on)
- [Google – How Google uses cookies](https://policies.google.com/technologies/cookies?hl=en-US)
- [Google 특허 US8276190B1 – Cross-domain authentication](https://patents.google.com/patent/US8276190)
- [네이버 OpenID Connect 설정 문서](https://nid.naver.com/.well-known/openid-configuration)
- [naver/naveridlogin-API 이슈 #92 – 공개 OIDC 지원 범위 문의](https://github.com/naver/naveridlogin-API/issues/92)
- [삼성 계정 OpenID Connect 설정 문서](https://api.account.samsung.com/.well-known/openid-configuration)
- [Samsung Account Developer Console](https://developer.account.samsung.com/intro)
- [Samsung – Why create a Samsung Account?](https://www.samsung.com/in/why-samsung-account/)
- [카카오 OpenID Connect 설정 문서](https://kauth.kakao.com/.well-known/openid-configuration)
- [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)
