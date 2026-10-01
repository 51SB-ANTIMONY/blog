---
title: API란?
date: 2026-10-01
tags: [api, http]
---

# API

## 정의
API(Application Programming Interface)는 어떤 프로그램이나 라이브러리가 다른 코드에서 자기 기능을 사용할 수 있도록 공개한 메서드, 클래스, 규칙의 모음이다.
API를 사용하는 쪽은 그 기능이 내부에서 어떻게 동작하는지 몰라도, 정해진 방식대로 요청하면 결과를 받을 수 있다.

## API의 종류
### 라이브러리 API
Java 코드에서 라이브러리의 메서드를 호출하는 것도 API를 사용하는 것이다.
```java
List<String> list = new ArrayList<>();
list.add("스프링");          // Java 컬렉션 API
list.size();
```

### 웹 API
웹에서는 특정 URL로 HTTP 요청을 보내면 데이터를 돌려주는 서버의 기능을 API라고 한다.
Spring Boot에서는 `@RestController`로 웹 API를 만든다.
```
요청: GET http://localhost:8080/api/posts
응답: [{"title":"첫 글","content":"안녕하세요"}, ...]
```
이 글부터는 웹 API를 기준으로 설명한다.

## 클라이언트와 서버
| 역할 | 하는 일 | 예 |
|---|---|---|
| 클라이언트 | API에 요청을 보내고 응답을 받아 사용 | 브라우저, 프런트엔드 앱, 모바일 앱 |
| 서버 | 요청을 받아 처리하고 응답을 돌려줌 | Spring Boot 애플리케이션 |

클라이언트는 서버가 어떤 DB를 쓰는지, 어떤 언어로 만들어졌는지 몰라도 된다. API의 URL과 요청, 응답 형식만 알면 된다.

## HTTP 요청과 응답의 구성
웹 API는 HTTP로 데이터를 주고받는다.

> **HTTP란?**
> 브라우저(또는 앱)와 서버가 데이터를 주고받을 때 사용하는 통신 규칙이다. ([Spring Framework](../01_spring/01_spring_framework.md))

### 요청(Request)
```
POST /api/posts HTTP/1.1          ← 요청 줄: HTTP 메서드, URL
Host: localhost:8080                 ← 헤더
Content-Type: application/json       ← 헤더

{"title":"제목","content":"내용"}     ← 본문
```

### 응답(Response)
```
HTTP/1.1 201 Created                 ← 상태 줄: 상태 코드
Content-Type: application/json       ← 헤더

{"id":1,"title":"제목","content":"내용"}  ← 본문
```

| 구성 요소 | 의미 |
|---|---|
| HTTP 메서드 | 요청의 종류 (`GET` 조회, `POST` 생성, `PUT` 수정, `DELETE` 삭제 등) |
| URL | 요청 대상의 주소 |
| 헤더 | 요청이나 응답에 대한 부가 정보 (본문의 형식, 인증 정보 등) |
| 본문(body) | 실제로 주고받는 데이터 |
| 상태 코드 | 서버가 요청을 어떻게 처리했는지 나타내는 세 자리 숫자 |

> **Content-Type이란?**
> 본문 데이터가 어떤 형식인지 알려 주는 헤더다. `application/json`은 본문이 JSON이라는 뜻이다.

웹 API를 설계하는 대표적인 방식이 REST다. → [REST API](./02_rest_api.md)
