---
title: 토큰 기반 인증의 흐름과 HTTP 메시지 형식
date: 2026-10-03
tags: [authentication, token, http, protocol, bearer]
---

# 토큰 기반 인증의 흐름과 HTTP 메시지 형식

> 이 노트는 토큰 기반 인증의 **공통 흐름**과, 토큰을 실어 나르는 **HTTP 메시지의 바이트 구성**을 다룬다.
> 토큰 자체의 내부 형식은 토큰 종류마다 다르므로, JWT의 형식은 [JWT의 구조](./04_jwt_structure.md)에서 따로 자세히 본다.

## 1. 전체 흐름 6단계
```
 클라이언트                                                   서버
     │                                                         │
     │ ① 로그인 요청  POST /login  {아이디, 비밀번호}           │
     │────────────────────────────────────────────────────────▶│
     │                                                         │ ② 자격 증명 확인
     │                                                         │   토큰 생성 (서명)
     │ ③ 토큰 응답   200  {"accessToken": "...", ...}          │
     │◀────────────────────────────────────────────────────────│
     │ ④ 토큰 저장                                              │
     │   (메모리, localStorage, 쿠키 등)                         │
     │                                                         │
     │ ⑤ 토큰과 함께 요청  Authorization: Bearer <토큰>          │
     │────────────────────────────────────────────────────────▶│
     │                                                         │ ⑥ 토큰 검증
     │                                                         │   서명, 만료, 발급자 확인
     │ ⑦ 응답  200 (성공) / 401 (토큰 문제)                     │
     │◀────────────────────────────────────────────────────────│
```
이 흐름은 토큰 종류와 상관없이 같다. 토큰 종류에 따라 달라지는 것은 ②에서 토큰을 **만드는 방법**과 ⑥에서 **검증하는 방법**이다.

이 노트에서는 각 단계의 HTTP 메시지가 실제로 어떤 바이트로 이루어져 있는지 본다. 그 전에 HTTP 메시지가 네트워크에서 어떻게 실려 가는지부터 정리한다.

## 2. HTTP 메시지는 어디에 실려 가는가
HTTP 메시지는 혼자 전송되지 않는다. 아래 계층의 프로토콜에 차례로 감싸져서 전송된다. 이것을 **캡슐화(Encapsulation)**라고 한다.

> **프로토콜(Protocol)이란?**
> 통신하는 양쪽이 지키기로 정한 메시지 형식과 주고받는 순서의 규칙이다.

```
┌──────────────────────────────────────────────────────────────────────┐
│ 이더넷 헤더 14B │ IP 헤더 20B │ TCP 헤더 20B │ [TLS 레코드 헤더 5B] │ HTTP 메시지 │ ...
└──────────────────────────────────────────────────────────────────────┘
  링크 계층        네트워크 계층   전송 계층      보안 계층(HTTPS일 때)   응용 계층
```

| 계층 | 프로토콜 | 하는 일 |
|---|---|---|
| 응용 | HTTP | 요청과 응답의 의미 (메서드, 경로, 헤더, 본문) |
| 보안 | TLS | HTTP 메시지를 암호화한다. HTTPS = HTTP + TLS |
| 전송 | TCP | 데이터를 순서대로, 빠짐없이 전달한다. 포트 번호로 프로그램을 구분한다. |
| 네트워크 | IP | IP 주소로 목적지 컴퓨터까지 경로를 찾아 전달한다. |
| 링크 | 이더넷, Wi-Fi | 같은 네트워크 안의 다음 장비까지 전달한다. |

`localhost`로 보내는 요청은 컴퓨터 밖으로 나가지 않으므로 이더넷 계층을 거치지 않고, 운영체제 내부의 루프백 장치로 바로 전달된다.

### 각 계층 헤더의 비트 구성
토큰 인증과 직접 관련된 부분은 HTTP 메시지지만, 그 메시지가 실제로 어떤 형태로 이동하는지 알기 위해 아래 계층 헤더의 구성을 정리한다.

**IPv4 헤더 (옵션 없을 때 20바이트)**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1   ← 비트 번호
+-------+-------+-----------+---+-------------------------------+
|Version|  IHL  |   DSCP    |ECN|         Total Length          |
+-------+-------+-----------+---+-----+-------------------------+
|        Identification         |Flags|     Fragment Offset     |
+---------------+---------------+-----+-------------------------+
|  Time to Live |   Protocol    |        Header Checksum        |
+---------------+---------------+-------------------------------+
|                        Source Address                         |
+---------------------------------------------------------------+
|                     Destination Address                       |
+---------------------------------------------------------------+
```
| 필드 | 크기 | 의미 |
|---|---|---|
| Version | 4비트 | IP 버전. IPv4는 `4` |
| IHL | 4비트 | 헤더 길이(4바이트 단위). 옵션이 없으면 `5` (= 20바이트) |
| DSCP, ECN | 6비트, 2비트 | 우선순위, 혼잡 알림 |
| Total Length | 16비트 | 헤더 + 데이터 전체 길이(바이트) |
| Identification, Flags, Fragment Offset | 16, 3, 13비트 | 큰 패킷을 쪼갤 때 다시 합치는 정보 |
| Time to Live | 8비트 | 거쳐 갈 수 있는 장비 수. 0이 되면 버린다. |
| Protocol | 8비트 | 안에 든 프로토콜. TCP는 `6` |
| Header Checksum | 16비트 | 헤더 오류 검사 값 |
| Source / Destination Address | 각 32비트 | 보내는 쪽, 받는 쪽 IP 주소 |

**TCP 헤더 (옵션 없을 때 20바이트)**
```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-------------------------------+-------------------------------+
|          Source Port          |       Destination Port        |
+-------------------------------+-------------------------------+
|                        Sequence Number                        |
+---------------------------------------------------------------+
|                    Acknowledgment Number                      |
+-------+-------+---------------+-------------------------------+
| Data  |       |C|E|U|A|P|R|S|F|                               |
|Offset | Rsrvd |W|C|R|C|S|S|Y|I|            Window             |
|       |       |R|E|G|K|H|T|N|N|                               |
+-------+-------+---------------+-------------------------------+
|           Checksum            |        Urgent Pointer         |
+-------------------------------+-------------------------------+
```
| 필드 | 크기 | 의미 |
|---|---|---|
| Source / Destination Port | 각 16비트 | 보내는 프로그램, 받는 프로그램의 포트. Spring Boot 서버는 `8080` |
| Sequence Number | 32비트 | 이 데이터의 첫 바이트가 전체 흐름에서 몇 번째인지 |
| Acknowledgment Number | 32비트 | 다음에 받기를 기대하는 바이트 번호 (여기까지 받았다는 확인) |
| Data Offset | 4비트 | 헤더 길이(4바이트 단위) |
| 플래그 8개 | 각 1비트 | `SYN`(연결 시작), `ACK`(확인), `FIN`(연결 종료), `PSH`(바로 전달), `RST`(연결 초기화) 등 |
| Window | 16비트 | 받는 쪽이 지금 더 받을 수 있는 양 |
| Checksum | 16비트 | 오류 검사 값 |

**TLS 레코드 헤더 (HTTPS일 때, 5바이트)**
| 바이트 | 필드 | 값 |
|---|---|---|
| 0 | Content Type | `0x17` (23, application_data: 암호화된 HTTP 데이터) |
| 1–2 | Legacy Version | `0x03 0x03` (TLS 1.2로 표기. TLS 1.3도 호환을 위해 이 값을 쓴다) |
| 3–4 | Length | 뒤따르는 암호화된 데이터 길이 |

HTTPS에서는 TLS 레코드 헤더 뒤의 내용이 **전부 암호화**되어 있다. 그래서 네트워크 중간에서 엿보는 사람은 `Authorization` 헤더의 토큰을 볼 수 없다. **HTTP(TLS 없이)로 토큰을 보내면 토큰이 그대로 노출되므로, 토큰 인증은 반드시 HTTPS와 함께 써야 한다.**

## 3. HTTP/1.1 메시지의 바이트 구성
HTTP/1.1 메시지는 **ASCII 텍스트**로 이루어져 있다. 각 글자는 1바이트이며, 줄의 끝은 `CR LF` 두 바이트로 표시한다.

> **ASCII란?**
> 영문자, 숫자, 기호를 0~127 사이의 숫자로 정해 둔 표준 문자 표다. `A`는 65(16진수 `0x41`), 공백은 32(`0x20`)다.

| 기호 | 바이트 | 의미 |
|---|---|---|
| `SP` | `0x20` | 공백 |
| `CR` | `0x0D` | Carriage Return |
| `LF` | `0x0A` | Line Feed |
| `CRLF` | `0x0D 0x0A` | 줄 끝 |
| `:` | `0x3A` | 헤더 이름과 값의 구분자 |

### 요청 메시지의 문법
```
요청 메시지  = 요청 줄
              *( 헤더 줄 )
              CRLF
              [ 본문 ]

요청 줄      = 메서드 SP 요청 대상 SP HTTP-버전 CRLF
헤더 줄      = 헤더 이름 ":" 선택적 공백 헤더 값 선택적 공백 CRLF
```
- 헤더 이름은 대소문자를 구분하지 않는다. `Authorization`과 `authorization`은 같다.
- 헤더가 끝나면 **빈 줄**(`CRLF` 하나, 즉 `0x0D 0x0A`)이 온다. 그래서 헤더 영역의 끝은 `0x0D 0x0A 0x0D 0x0A` 네 바이트로 나타난다.
- 본문 길이는 `Content-Length` 헤더(바이트 수)로 알린다. 서버는 빈 줄 다음부터 정확히 그 바이트 수만큼 읽는다.

### 응답 메시지의 문법
```
상태 줄 = HTTP-버전 SP 상태 코드 SP [ 상태 문구 ] CRLF
```
예: `HTTP/1.1 200 OK` 또는 상태 문구를 생략한 `HTTP/1.1 200 ` (Tomcat은 상태 문구를 생략한다).

## 4. 단계별 메시지

### ① 로그인 요청
토큰 기반 API는 보통 아이디와 비밀번호를 JSON으로 보낸다.
```http
POST /login HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Content-Length: 37

{"email":"a@a.com","password":"1234"}
```
**바이트 단위로 본 모습 (총 135바이트)**
```
오프셋 16진수 바이트                                        ASCII
0000  50 4f 53 54 20 2f 6c 6f 67 69 6e 20 48 54 54 50   POST /login HTTP
0010  2f 31 2e 31 0d 0a 48 6f 73 74 3a 20 6c 6f 63 61   /1.1..Host: loca
0020  6c 68 6f 73 74 3a 38 30 38 30 0d 0a 43 6f 6e 74   lhost:8080..Cont
0030  65 6e 74 2d 54 79 70 65 3a 20 61 70 70 6c 69 63   ent-Type: applic
0040  61 74 69 6f 6e 2f 6a 73 6f 6e 0d 0a 43 6f 6e 74   ation/json..Cont
0050  65 6e 74 2d 4c 65 6e 67 74 68 3a 20 33 37 0d 0a   ent-Length: 37..
0060  0d 0a 7b 22 65 6d 61 69 6c 22 3a 22 61 40 61 2e   ..{"email":"a@a.
0070  63 6f 6d 22 2c 22 70 61 73 73 77 6f 72 64 22 3a   com","password":
0080  22 31 32 33 34 22 7d                              "1234"}
```
| 오프셋 | 바이트 | 의미 |
|---|---|---|
| `0x00`–`0x03` | `50 4f 53 54` | 메서드 `POST` |
| `0x04` | `20` | 공백 |
| `0x05`–`0x0A` | `2f 6c 6f 67 69 6e` | 요청 대상 `/login` |
| `0x0B` | `20` | 공백 |
| `0x0C`–`0x13` | `48 54 54 50 2f 31 2e 31` | 버전 `HTTP/1.1` |
| `0x14`–`0x15` | `0d 0a` | 요청 줄 끝 |
| `0x16`–`0x2B` | | `Host: localhost:8080` + `0d 0a` |
| `0x2C`–`0x4B` | | `Content-Type: application/json` + `0d 0a` |
| `0x4C`–`0x5F` | | `Content-Length: 37` + `0d 0a` |
| `0x60`–`0x61` | `0d 0a` | **빈 줄. 헤더 끝** |
| `0x62`–`0x86` | | 본문 37바이트. `Content-Length: 37`과 정확히 같다. |

본문의 `"1234"`처럼 **비밀번호가 평문으로 들어 있다.** HTTPS가 이 바이트 전체를 암호화해 주어야 안전하다.

### ② 서버의 처리
1. 본문 JSON에서 이메일과 비밀번호를 꺼낸다.
2. DB에서 이메일로 사용자를 찾고, 비밀번호 해시를 비교한다.
3. 성공하면 토큰을 만든다. 토큰을 만드는 방법은 토큰 종류에 따라 다르다. JWT는 [JWT의 서명과 검증](./05_jwt_signature_and_validation.md)에서 본다.
4. 리프레시 토큰을 쓰는 구조라면 리프레시 토큰도 만들어 DB에 저장한다([리프레시 토큰](./06_refresh_token.md)).

### ③ 토큰 응답
토큰을 전달하는 방법은 두 가지다.

**방법 A. 응답 본문(JSON)에 담기**
```http
HTTP/1.1 200 
Content-Type: application/json
Cache-Control: no-store

{"accessToken":"eyJ0eXAi...RXQ","tokenType":"Bearer","expiresIn":7200,"refreshToken":"eyJ0eXAi...bnI"}
```
| 필드 | 의미 |
|---|---|
| `accessToken` | API 호출에 쓸 토큰 |
| `tokenType` | 토큰을 보내는 방식. `Bearer` |
| `expiresIn` | 액세스 토큰 유효 시간(초) |
| `refreshToken` | 액세스 토큰을 다시 받을 때 쓸 토큰 |

필드 이름은 서비스마다 자유롭게 정한다. 위 이름들은 OAuth 2.0 표준(RFC 6749)이 쓰는 `access_token`, `token_type`, `expires_in`, `refresh_token`을 따라 지은 것이다.

`Cache-Control: no-store`는 토큰이 담긴 응답이 브라우저나 중간 서버에 저장되지 않게 한다.

**방법 B. 쿠키로 내려 주기**
```http
HTTP/1.1 200 
Set-Cookie: refresh_token=eyJ0eXAi...bnI; Path=/api/token; Max-Age=1209600; HttpOnly; Secure; SameSite=Strict
Content-Type: application/json

{"accessToken":"eyJ0eXAi...RXQ"}
```
액세스 토큰은 본문으로, 리프레시 토큰은 `HttpOnly` 쿠키로 나누어 주는 구성이 많이 쓰인다(5장).

### ④ 토큰 저장
클라이언트가 토큰을 어디에 둘지 정해야 한다. 자세한 비교는 5장에서 한다.

### ⑤ 토큰과 함께 요청
토큰은 `Authorization` 헤더에 **`Bearer` 방식**으로 넣는다. 이 형식은 RFC 6750(OAuth 2.0 Bearer Token Usage)에 정해져 있다.
```
Authorization: Bearer <토큰>
```
문법:
```
credentials = "Bearer" 1개 이상의 공백 b64token
b64token    = 1*( 영문자 / 숫자 / "-" / "." / "_" / "~" / "+" / "/" ) *"="
```
- `Bearer`는 인증 방식(scheme) 이름이다. "이 토큰을 가진 사람(bearer)에게 권한을 주라"는 뜻이다.
- 토큰은 위 문자들로만 이루어져야 한다. JWT는 영문자, 숫자, `-`, `_`, `.`만 쓰므로 그대로 넣을 수 있다.

```http
GET /api/articles HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJpc3N1ZUBhbnRpbW9ueS5rciIsImlhdCI6MTc5MDk5NjQwMCwiZXhwIjoxNzkxMDAzNjAwLCJzdWIiOiJhQGEuY29tIiwiaWQiOjF9.QRy6o9czgmWZwJTK0ws7UytcxwPOnoiqIGeo6XapRXQ

```
**바이트 단위로 본 모습 (총 269바이트, 일부)**
```
0000  47 45 54 20 2f 61 70 69 2f 61 72 74 69 63 6c 65   GET /api/article
0010  73 20 48 54 54 50 2f 31 2e 31 0d 0a 48 6f 73 74   s HTTP/1.1..Host
0020  3a 20 6c 6f 63 61 6c 68 6f 73 74 3a 38 30 38 30   : localhost:8080
0030  0d 0a 41 75 74 68 6f 72 69 7a 61 74 69 6f 6e 3a   ..Authorization:
0040  20 42 65 61 72 65 72 20 65 79 4a 30 65 58 41 69    Bearer eyJ0eXAi
 ...  (토큰 계속)
00d0  74 49 69 77 69 61 57 51 69 4f 6a 46 39 2e 51 52   tIiwiaWQiOjF9.QR
00e0  79 36 6f 39 63 7a 67 6d 57 5a 77 4a 54 4b 30 77   y6o9czgmWZwJTK0w
00f0  73 37 55 79 74 63 78 77 50 4f 6e 6f 69 71 49 47   s7UytcxwPOnoiqIG
0100  65 6f 36 58 61 70 52 58 51 0d 0a 0d 0a            eo6XapRXQ....
```
| 부분 | 바이트 수 |
|---|---|
| 요청 줄 `GET /api/articles HTTP/1.1` + CRLF | 28 |
| `Host: localhost:8080` + CRLF | 22 |
| `Authorization: Bearer ` | 22 |
| 토큰 | 193 |
| CRLF (헤더 줄 끝) + CRLF (빈 줄) | 4 |
| **합계** | **269** |

같은 요청을 세션 방식으로 보내면 `Cookie: JSESSIONID=`(19바이트) + 세션 ID 32바이트 + CRLF 2바이트 = 53바이트다. 토큰 방식은 **요청마다 토큰 길이만큼 더 보낸다.**

토큰을 헤더가 아니라 URL 쿼리(`?access_token=...`)에 넣는 방법도 RFC 6750에 있지만 권장하지 않는다. URL은 서버 접속 기록, 브라우저 방문 기록에 그대로 남기 때문이다.

### ⑥ 토큰 검증
서버가 `Authorization` 헤더를 읽고 다음을 확인한다.
1. 헤더가 있는가, `Bearer `로 시작하는가
2. 토큰 형식이 올바른가
3. 서명이 올바른가 (위조되지 않았는가)
4. 만료되지 않았는가
5. 발급자, 대상 등 그 밖의 조건이 맞는가

JWT의 구체적인 검증 방법은 [JWT의 서명과 검증](./05_jwt_signature_and_validation.md)에서 본다.

### ⑦ 응답
**성공**
```http
HTTP/1.1 200 
Content-Type: application/json

[{"id":1,"title":"제목 1","content":"내용 1"}, ...]
```

**토큰 문제 (RFC 6750 형식)**
```http
HTTP/1.1 401 
WWW-Authenticate: Bearer realm="blog", error="invalid_token", error_description="The access token expired"
```
| 상황 | 상태 코드 | `error` 값 |
|---|---|---|
| 토큰이 없음 | `401` | (생략. `WWW-Authenticate: Bearer`만) |
| 형식이 잘못된 요청 | `400` | `invalid_request` |
| 토큰이 만료, 위조, 형식 오류 | `401` | `invalid_token` |
| 토큰은 유효하지만 권한 부족 | `403` | `insufficient_scope` |

클라이언트는 `401`을 받으면 리프레시 토큰으로 새 액세스 토큰을 받거나, 다시 로그인한다.

## 5. 토큰은 어디에 저장하는가 (④ 자세히)
브라우저에서 쓸 수 있는 저장 위치와 각각의 위험은 다음과 같다.

> **XSS란?**
> Cross-Site Scripting. 공격자가 웹 페이지에 악성 JavaScript를 끼워 넣어 사용자의 브라우저에서 실행시키는 공격이다. 예를 들어 글 내용에 `<script>`를 넣었는데 화면에서 그대로 실행되는 경우다. 이 스크립트는 그 페이지의 JavaScript가 할 수 있는 모든 일을 할 수 있다.

| 저장 위치 | JavaScript로 읽기 | 새로고침 후 유지 | 요청에 자동 첨부 | 주요 위험 |
|---|---|---|---|---|
| JavaScript 변수 (메모리) | 가능 | **사라짐** | 안 됨 | XSS가 실행 중인 동안만 노출. 새로고침마다 다시 받아야 함 |
| `sessionStorage` | 가능 | 유지 (탭을 닫으면 삭제) | 안 됨 | XSS로 탈취 가능 |
| `localStorage` | 가능 | 유지 (직접 지울 때까지) | 안 됨 | XSS로 탈취 가능. 오래 남음 |
| 쿠키 (`HttpOnly` 없음) | 가능 | `Max-Age`까지 유지 | **됨** | XSS로 탈취 가능 + CSRF |
| 쿠키 (`HttpOnly`) | **불가능** | `Max-Age`까지 유지 | **됨** | XSS로 탈취는 불가능. 대신 CSRF 대비 필요 (`SameSite`, CSRF 토큰) |

어느 한 곳이 완벽하지 않으므로 보통 다음처럼 나눈다.
- **액세스 토큰**: JavaScript 변수(메모리). 짧게 쓰고 버린다.
- **리프레시 토큰**: `HttpOnly; Secure; SameSite=Strict` 쿠키. `Path`를 재발급 API(`/api/token`)로 좁혀서, 그 주소로 가는 요청에만 붙게 한다.
- 새로고침으로 액세스 토큰이 사라지면, 리프레시 토큰 쿠키로 재발급 API를 호출해 다시 받는다.

모바일 앱은 운영체제가 제공하는 보안 저장소(iOS Keychain, Android Keystore)에 저장한다.

## 정리
| 단계 | 핵심 |
|---|---|
| 전송 | HTTP 메시지는 TCP(필요하면 TLS) 위에 실려 간다. 토큰 인증은 HTTPS가 필수다. |
| 메시지 형식 | HTTP/1.1은 ASCII 텍스트. 줄 끝은 `0x0D 0x0A`, 헤더 끝은 빈 줄 |
| 로그인 | 아이디, 비밀번호를 본문으로 보낸다. |
| 토큰 응답 | 본문 JSON 또는 `Set-Cookie`로 전달한다. |
| 토큰 저장 | 액세스 토큰은 메모리, 리프레시 토큰은 `HttpOnly` 쿠키가 일반적이다. |
| 토큰과 함께 요청 | `Authorization: Bearer <토큰>` |
| 검증 실패 | `401` + `WWW-Authenticate: Bearer error="invalid_token"` |
