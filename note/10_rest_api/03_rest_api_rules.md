---
title: REST API 사용 규칙
date: 2026-10-01
tags: [api, rest, http]
---

# REST API 사용 규칙

> 자원, HTTP 메서드, 상태 코드 같은 용어는 [API](./01_api.md)와 [REST API](./02_rest_api.md)에서 설명했다.

## 규칙 1. URL에는 동사가 아니라 자원(명사)을 쓴다
무엇을 할지는 HTTP 메서드가 나타내므로, URL에는 대상만 적는다.
```
좋은 예: GET /api/posts/1
나쁜 예: GET /api/getPost/1
```
자원 모음은 보통 **복수형**으로 쓴다. (`/posts`, `/members`)

## 규칙 2. 동작은 HTTP 메서드로 나타낸다
| HTTP 메서드 | 의미 | 예시 |
|---|---|---|
| `GET` | 조회 | `GET /api/posts` |
| `GET` | 하나 조회 | `GET /api/posts/1` |
| `POST` | 생성 | `POST /api/posts` |
| `PUT` | 전체 수정 (자원 전체를 보낸 값으로 바꿈) | `PUT /api/posts/1` |
| `PATCH` | 일부 수정 (보낸 필드만 바꿈) | `PATCH /api/posts/1` |
| `DELETE` | 삭제 | `DELETE /api/posts/1` |

> **PUT과 PATCH의 차이**
> `PUT`은 보낸 내용으로 자원 전체를 교체한다. `{"title":"새 제목"}`만 보내면 content는 비워지는 것이 원칙이다.
> `PATCH`는 보낸 필드만 바꾸고 나머지는 그대로 둔다.

## 규칙 3. 계층 관계는 슬래시(`/`)로 나타낸다
```
/api/posts/1/comments     ← 1번 글의 댓글 목록
/api/posts/1/comments/3   ← 1번 글의 3번 댓글
```

## 규칙 4. URL 끝에 슬래시를 붙이지 않는다
```
좋은 예: /api/posts
나쁜 예: /api/posts/
```

## 규칙 5. 소문자를 쓰고, 단어는 하이픈(`-`)으로 연결한다
```
좋은 예: /api/blog-posts
나쁜 예: /api/blogPosts, /api/blog_posts
```
URL은 대소문자를 구분할 수 있어서 소문자로 통일한다. 밑줄(`_`)은 링크 밑줄과 겹쳐 보이기 쉬워서 쓰지 않는다.

## 규칙 6. 파일 확장자를 URL에 넣지 않는다
응답 형식은 헤더(`Accept`, `Content-Type`)로 나타낸다.
```
좋은 예: GET /api/posts/1      (헤더 Accept: application/json)
나쁜 예: GET /api/posts/1.json
```

> **Accept 헤더란?**
> 클라이언트가 "이 형식으로 응답해 달라"고 서버에 알리는 헤더다.

## 규칙 7. 결과는 알맞은 상태 코드로 알린다
| 범위 | 의미 |
|---|---|
| 2xx | 성공 |
| 3xx | 다른 주소로 이동 (리다이렉트) |
| 4xx | 클라이언트의 잘못된 요청 |
| 5xx | 서버 내부 오류 |

| 상태 코드 | 의미 | 쓰는 경우 |
|---|---|---|
| 200 OK | 성공 | 조회, 수정, 삭제 성공 |
| 201 Created | 생성 성공 | `POST`로 자원을 만들었을 때 |
| 204 No Content | 성공, 응답 본문 없음 | 삭제 성공 후 돌려줄 데이터가 없을 때 |
| 400 Bad Request | 요청 형식이 잘못됨 | 필수 값 누락, 검증 실패 |
| 401 Unauthorized | 인증이 필요함 | 로그인하지 않음 |
| 403 Forbidden | 권한이 없음 | 다른 사람의 글을 수정하려 함 |
| 404 Not Found | 자원이 없음 | 없는 id를 조회 |
| 405 Method Not Allowed | 그 URL에서 지원하지 않는 HTTP 메서드 | |
| 500 Internal Server Error | 서버 내부 오류 | 처리하지 않은 예외 |

> **리다이렉트란?**
> 서버가 클라이언트에게 "다른 URL로 다시 요청하라"고 알려 주는 것이다.

