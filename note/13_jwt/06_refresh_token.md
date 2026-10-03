---
title: 리프레시 토큰
date: 2026-10-03
tags: [jwt, refresh-token, access-token, security]
---

# 리프레시 토큰

> JWT의 구조와 검증은 [JWT의 구조](./04_jwt_structure.md), [JWT의 서명과 검증](./05_jwt_signature_and_validation.md)에서 설명했다.

## 1. 왜 필요한가: 만료 시간의 딜레마
액세스 토큰의 유효 시간을 정할 때 두 요구가 부딪힌다.

| 유효 시간 | 장점 | 단점 |
|---|---|---|
| 길게 (예: 30일) | 사용자가 30일 동안 다시 로그인하지 않아도 된다 | 탈취되면 30일 동안 악용된다. 서버가 취소할 수도 없다(무상태). |
| 짧게 (예: 30분) | 탈취돼도 30분 뒤면 쓸모없다 | 사용자가 30분마다 다시 로그인해야 한다 |

**리프레시 토큰**은 이 둘을 함께 얻기 위한 방법이다. 토큰을 역할에 따라 둘로 나눈다.

| | 액세스 토큰 (Access Token) | 리프레시 토큰 (Refresh Token) |
|---|---|---|
| 용도 | API 요청마다 보내서 인증 | 액세스 토큰이 만료되면 새것을 받을 때만 |
| 보내는 곳 | 모든 API | 재발급 API 한 곳 (`POST /api/token`) |
| 유효 시간 | 짧다 (수 분~수 시간) | 길다 (수 일~수 주) |
| 서버 저장 | 하지 않음 (무상태) | **저장함** (DB, Redis) |
| 취소 | 어려움 (만료를 기다림) | 저장소에서 지우면 즉시 |
| 노출 빈도 | 매우 잦음 | 드묾 |

**결과:**
- 자주 오가서 탈취 위험이 큰 액세스 토큰은 금방 만료된다.
- 오래 쓰는 리프레시 토큰은 드물게만 전송되고, 서버가 저장하고 있으므로 문제가 생기면 지울 수 있다.
- 사용자는 리프레시 토큰이 유효한 동안 다시 로그인하지 않아도 된다.

무상태성을 액세스 토큰 쪽에서 유지하고, 취소 가능성은 리프레시 토큰 쪽에서 얻는 구조다.

## 2. 리프레시 토큰의 구성
두 가지 방식이 있다.

### 방식 A. JWT로 만들기 (이 프로젝트의 방식)
액세스 토큰과 같은 형식에 만료 시간만 길게 한다.
```json
// 헤더
{"typ":"JWT","alg":"HS256"}
// 페이로드
{"iss":"issue@antimony.kr","iat":1790996400,"exp":1792206000,"sub":"a@a.com","id":1}
```
```
exp - iat = 1209600초 = 14일  (2026-10-17 12:00 KST 만료)
```
실제로 계산한 토큰:
```
eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJpc3N1ZUBhbnRpbW9ueS5rciIsImlhdCI6MTc5MDk5NjQwMCwiZXhwIjoxNzkyMjA2MDAwLCJzdWIiOiJhQGEuY29tIiwiaWQiOjF9.SwNvKxXAWWqBsUg8EOO4rFiXfNxhjk4425YQxjIqbnI
```
- 서명과 만료를 JWT 검증으로 바로 확인할 수 있다.
- 서버는 이 문자열을 DB에도 저장해 두고, 재발급할 때 **서명 검증 + DB에 있는지** 두 가지를 모두 확인한다.

같은 비밀 키, 같은 형식이면 액세스 토큰과 리프레시 토큰을 구분할 수 없다. 리프레시 토큰을 API 요청에 넣어도 통과하게 된다. 그래서 다음 중 하나로 구분하는 것이 좋다.
- 클레임으로 구분: `"typ":"refresh"` 같은 클레임을 넣고, API 인증 필터에서는 이런 토큰을 거부한다.
- 키를 분리: 리프레시 토큰은 다른 비밀 키로 서명한다.

### 방식 B. 불투명 토큰으로 만들기
의미 없는 난수 문자열을 쓴다.
```
보안 난수 32바이트 → Base64URL → "q3Z9xH...Lk2w" (43글자)
```
- 정보가 없으므로 반드시 DB에서 조회해야 한다. 어차피 DB를 조회하는 리프레시 토큰에는 자연스러운 방식이다.
- DB에는 원문 대신 **SHA-256 해시를 저장**한다. DB가 유출돼도 해시로는 토큰을 쓸 수 없다.

## 3. 서버의 저장 구조
이 프로젝트의 `RefreshToken` 엔티티가 만드는 테이블:
```sql
CREATE TABLE refresh_token (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE,   -- 회원 한 명당 하나
    refresh_token VARCHAR(255) NOT NULL
);
```
| id | user_id | refresh_token |
|---|---|---|
| 1 | 1 | `eyJ0eXAi...qbnI` |
| 2 | 7 | `eyJ0eXAi...Xk3a` |

`user_id`가 `UNIQUE`라서 회원 한 명은 리프레시 토큰을 하나만 가진다. 다시 로그인하면 기존 행의 토큰을 새것으로 바꾼다. 그 결과 한 기기에서 로그인하면 다른 기기의 리프레시 토큰은 무효가 된다. 여러 기기 로그인을 허용하려면 `UNIQUE`를 빼고 기기 정보 컬럼을 추가한다.

## 4. 전체 흐름

### ① 로그인: 두 토큰 발급
```
클라이언트 ── POST /login {email, password} ─────────────────▶ 서버
                                                             1. 비밀번호 확인
                                                             2. 액세스 토큰 생성 (2시간)
                                                             3. 리프레시 토큰 생성 (14일)
                                                             4. refresh_token 테이블에 저장
                                                                (user_id=1 행이 있으면 update,
                                                                 없으면 insert)
클라이언트 ◀── 200 ─────────────────────────────────────────── 서버
```
```http
HTTP/1.1 200 
Content-Type: application/json
Cache-Control: no-store
Set-Cookie: refresh_token=eyJ0eXAi...qbnI; Path=/api/token; Max-Age=1209600; HttpOnly; Secure; SameSite=Strict

{"accessToken":"eyJ0eXAi...RXQ"}
```
쿠키의 `Max-Age=1209600`(14일)은 리프레시 토큰의 `exp`와 맞춘다. `Path=/api/token`이라서 이 쿠키는 재발급 API로 가는 요청에만 붙는다.

### ② API 사용 (액세스 토큰 유효)
```http
GET /api/articles HTTP/1.1
Authorization: Bearer eyJ0eXAi...RXQ
```
→ `200`. 리프레시 토큰은 쓰이지 않는다.

### ③ 액세스 토큰 만료
14:00 이후의 요청:
```http
GET /api/articles HTTP/1.1
Authorization: Bearer eyJ0eXAi...RXQ
```
```http
HTTP/1.1 401 
WWW-Authenticate: Bearer error="invalid_token", error_description="The access token expired"
```

### ④ 재발급 요청
클라이언트가 `401`을 받으면 재발급 API를 호출한다.

쿠키로 보내는 경우(브라우저가 자동으로 첨부):
```http
POST /api/token HTTP/1.1
Cookie: refresh_token=eyJ0eXAi...qbnI
```
본문으로 보내는 경우(이 프로젝트의 방식):
```http
POST /api/token HTTP/1.1
Content-Type: application/json

{"refreshToken":"eyJ0eXAi...qbnI"}
```

### ⑤ 서버의 재발급 처리
```
1. 리프레시 토큰의 서명, 만료 검증 (JWT 방식일 때)
     실패 → 401. 다시 로그인해야 함
2. 저장소에서 이 토큰 조회
     SELECT * FROM refresh_token WHERE refresh_token = ?
     없음 → 401. 로그아웃됐거나 교체됐거나 위조된 토큰
3. 찾은 행의 user_id로 회원 조회
     SELECT * FROM users WHERE id = ?
4. 새 액세스 토큰 생성 (iat=지금, exp=지금+2시간)
5. (교체 방식이면) 새 리프레시 토큰을 만들어 저장소의 값을 바꿈
```
2번이 핵심이다. 서명이 올바르고 만료되지 않은 토큰이라도, **저장소에 없으면 거부한다.** 관리자가 행을 지우거나 사용자가 로그아웃하면 그 리프레시 토큰은 즉시 무효가 된다.

### ⑥ 재발급 응답
```http
HTTP/1.1 201 
Content-Type: application/json

{"accessToken":"eyJ0eXAi...(새 토큰)"}
```
클라이언트는 새 액세스 토큰으로 ③에서 실패한 요청을 다시 보낸다. 사용자는 이 과정을 알아채지 못한다.

### ⑦ 로그아웃
```
POST /logout (Authorization: Bearer 액세스 토큰)
  → DELETE FROM refresh_token WHERE user_id = 1
  → (쿠키 방식이면) Set-Cookie: refresh_token=; Max-Age=0
  → 클라이언트는 메모리의 액세스 토큰을 버림
```
이미 발급된 액세스 토큰은 서버가 취소할 수 없으므로 만료 시각까지는 유효하다. 액세스 토큰을 짧게 하는 이유가 이것이다.

## 5. 리프레시 토큰 교체 (Rotation)
재발급할 때마다 **리프레시 토큰도 새것으로 바꾸는** 방식이다.

```
10:00  로그인              → AT1, RT1 발급. 저장소: RT1
12:05  AT1 만료, RT1로 재발급 → AT2, RT2 발급. 저장소: RT1 → RT2
14:10  AT2 만료, RT2로 재발급 → AT3, RT3 발급. 저장소: RT2 → RT3
```

### 재사용 탐지 (Reuse Detection)
공격자가 RT1을 훔쳤다고 하자.
```
12:05  사용자가 RT1로 재발급 → 저장소: RT2
13:00  공격자가 RT1로 재발급 시도
       → 저장소에 RT1 없음 → 거부
       → "이미 교체된 토큰이 다시 쓰였다" = 탈취 의심
       → 이 사용자의 리프레시 토큰을 모두 삭제 → 사용자와 공격자 모두 다시 로그인 필요
```
공격자가 먼저 썼다면 그다음 사용자의 재발급이 실패하게 되고, 이때도 같은 방식으로 탐지된다. 교체하지 않으면 훔친 리프레시 토큰을 만료까지 계속 쓸 수 있다.

이 탐지를 정확히 하려면 "이전에 발급했지만 교체된 토큰"을 구별할 수 있어야 한다. 그래서 같은 로그인에서 이어진 토큰들을 하나의 묶음(family)으로 기록해 두는 구현이 많다.

## 6. 클라이언트의 처리 흐름
```javascript
async function callApi(url, options = {}) {
    let response = await fetch(url, withToken(options, accessToken));

    if (response.status === 401) {
        const refreshed = await fetch('/api/token', { method: 'POST', credentials: 'include' });
        if (!refreshed.ok) {
            location.href = '/login';          // 리프레시 토큰도 만료 → 다시 로그인
            return;
        }
        accessToken = (await refreshed.json()).accessToken;
        response = await fetch(url, withToken(options, accessToken));   // 원래 요청 재시도
    }
    return response;
}

function withToken(options, token) {
    return { ...options, headers: { ...options.headers, Authorization: `Bearer ${token}` } };
}
```
- `credentials: 'include'`: `fetch`가 쿠키(리프레시 토큰)를 함께 보내게 한다.
- 만료 시각(`exp`)을 미리 읽어 두고, 만료 직전에 재발급해서 `401` 자체를 피하는 방법도 있다.
- 여러 요청이 동시에 `401`을 받으면 재발급이 여러 번 일어난다. 교체 방식에서는 두 번째 재발급이 재사용으로 탐지될 수 있으므로, 재발급 요청을 하나로 모으는 처리가 필요하다.

## 정리
| 항목 | 핵심 |
|---|---|
| 필요성 | 짧은 액세스 토큰(안전) + 긴 리프레시 토큰(편의) |
| 구성 | JWT(만료가 긴) 또는 난수 문자열. 서버 저장소에 함께 저장 |
| 재발급 조건 | 서명과 만료 검증 **+ 저장소에 존재** |
| 취소 | 저장소에서 삭제하면 즉시 |
| 교체 | 재발급할 때마다 리프레시 토큰도 바꾸고, 옛 토큰 재사용을 탈취 신호로 본다 |
| 저장 위치 | `HttpOnly; Secure; SameSite=Strict` 쿠키, `Path`를 재발급 API로 제한 |
