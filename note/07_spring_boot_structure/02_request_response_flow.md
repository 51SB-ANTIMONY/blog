---
title: 스프링 부트 요청-응답 과정
date: 2026-10-01
tags: [spring-boot, spring-mvc]
---

# 스프링 부트 요청-응답 과정

> 이 글에 나오는 DispatcherServlet, Tomcat, 계층 같은 용어는 [Spring Framework](../01_spring/01_spring_framework.md), [차이점 2](../02_spring_boot/03_difference_embedded_was.md), [계층 구조](./01_layers.md)에서 설명했다.

[Spring Framework](../01_spring/01_spring_framework.md)에서 요청 흐름을 간단히 봤다.
여기서는 `GET /api/posts/1` 요청이 들어왔을 때 Spring Boot 안에서 일어나는 일을 조금 더 자세히 본다.

## 전체 흐름
```
① 클라이언트가 GET /api/posts/1 요청을 보냄
   ↓
② 내장 Tomcat이 요청을 받아 DispatcherServlet에 넘김
   ↓
③ DispatcherServlet이 HandlerMapping에 "이 요청을 처리할 메서드가 뭐야?"라고 물음
   ↓
④ HandlerMapping이 @GetMapping("/api/posts/{id}")가 붙은
   PostController.findPost()를 찾아 줌
   ↓
⑤ DispatcherServlet이 HandlerAdapter를 통해 그 메서드를 호출
   (이때 URL의 1을 @PathVariable long id에 넣어 줌)
   ↓
⑥ Controller → Service → Repository → DB 순서로 처리
   ↓
⑦ Controller가 ResponseEntity<PostResponse>를 반환
   ↓
⑧ HttpMessageConverter(Jackson)가 PostResponse 객체를 JSON으로 변환
   ↓
⑨ Tomcat이 HTTP 응답을 클라이언트에 보냄
   200 OK
   {"title":"첫 글","content":"안녕하세요"}
```

## 단계별 구성 요소
| 구성 요소 | 하는 일 |
|---|---|
| Tomcat | HTTP 요청을 받고 응답을 보내는 서버 |
| DispatcherServlet | 모든 요청을 가장 먼저 받아서 아래 구성 요소들에게 일을 나눠 맡김 |
| HandlerMapping | 요청 URL과 HTTP 메서드를 보고, 처리할 Controller 메서드를 찾음 |
| HandlerAdapter | 찾은 Controller 메서드를 실제로 호출함. 매개변수(`@PathVariable`, `@RequestBody`)에 값을 채워 넣음 |
| HttpMessageConverter | 요청 본문 JSON을 Java 객체로, 반환된 Java 객체를 응답 JSON으로 변환함 |

> **핸들러(handler)란?**
> 요청을 처리하는 대상을 가리키는 말이다. Spring MVC에서는 보통 Controller의 메서드가 핸들러다.

> **HttpMessageConverter란?**
> HTTP 메시지(요청, 응답) 본문과 Java 객체를 서로 변환하는 인터페이스다.
> JSON 변환에는 Jackson을 사용하는 구현체가 쓰인다. Spring Boot 4는 Jackson 3를 사용한다.

## 화면(HTML)을 돌려주는 경우
`@RestController`가 아니라 `@Controller`를 쓰고 문자열(뷰 이름)을 반환하면, ⑧ 단계에서 JSON 변환 대신 **ViewResolver**가 동작한다.
ViewResolver는 뷰 이름(`"postList"`)으로 `templates/postList.html` 같은 템플릿 파일을 찾아 HTML을 만든다.

> **뷰와 템플릿이란?**
> 뷰는 사용자에게 보여 줄 화면이다. 템플릿은 HTML 안에 데이터가 들어갈 자리를 표시해 둔 파일로, 서버가 데이터를 채워서 완성된 HTML을 만든다. (예: Thymeleaf)

REST API만 만들고 화면은 프런트엔드가 따로 담당하는 애플리케이션에서는 ViewResolver를 쓰지 않는다.

## 에러가 나는 경우
처리 중에 예외가 발생해서 아무도 처리하지 않으면, Spring Boot는 기본 에러 응답(500 Internal Server Error)을 보낸다.
예를 들어 Service가 없는 id를 조회할 때 `IllegalArgumentException`을 던지도록 작성했고, 이 예외를 처리하는 코드가 없으면 500 응답이 나간다.
예외를 원하는 상태 코드(예: 404)로 바꾸려면 `@ExceptionHandler`나 `@RestControllerAdvice`를 사용한다.
