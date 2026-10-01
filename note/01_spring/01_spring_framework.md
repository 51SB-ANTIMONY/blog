---
title: Spring Framework란?
date: 2026-09-30
tags: [spring]
---

# Spring Framework

## 정의
Spring은 Java 애플리케이션을 만들 때 쓰는 오픈소스 프레임워크다.
객체를 누가 만들고 서로 어떻게 연결할지를 개발자 코드가 아니라 **Spring 컨테이너(ApplicationContext)** 가 관리한다.
이 구조 덕분에 객체 사이의 결합도가 낮아지고, 코드를 테스트하고 교체하기가 쉬워진다.

> **프레임워크란?**
> 애플리케이션의 기본 구조와 실행 흐름을 미리 만들어 둔 코드 모음이다.
> 개발자는 프레임워크가 정한 규칙에 맞춰 필요한 부분의 코드만 작성하고, 그 코드를 **언제 호출할지는 프레임워크가 결정**한다.
>
> 비슷한 말로 **라이브러리**가 있다. 라이브러리는 특정 기능을 하는 코드를 다른 프로젝트에서 가져다 쓸 수 있게 묶어 둔 것으로, **개발자 코드가 필요할 때 직접 호출**한다.
> 둘의 차이는 코드의 실행 흐름을 누가 결정하느냐에 있다.

> **오픈소스란?**
> 소스 코드가 공개되어 있어서 누구나 보고, 사용하고, 수정에 참여할 수 있는 소프트웨어다.

> **결합도란?**
> 한 클래스가 다른 클래스에 얼마나 강하게 묶여 있는지를 나타내는 정도다.
> 결합도가 높으면 한 클래스를 고칠 때 그 클래스를 사용하는 다른 클래스도 함께 고쳐야 한다.

> **컨테이너란?**
> 애플리케이션이 실행될 때 객체를 생성하고, 필요한 곳에 연결하고, 종료될 때 정리하는 Spring의 구성 요소다.
> 자세한 내용은 [빈과 스프링 컨테이너](../03_spring_concepts/03_bean_and_container.md)에서 설명한다.

## Spring의 기본 구조
Spring 애플리케이션은 **Spring이 제공하는 부분**과 **개발자가 작성하는 부분**으로 나뉜다.

| 구분 | 구성 요소 | 하는 일 |
|---|---|---|
| Spring이 제공 | 컨테이너 (`ApplicationContext`) | 개발자가 작성한 클래스의 객체를 만들고, 서로 연결하고, 보관한다 |
| Spring이 제공 | `DispatcherServlet` | 들어온 HTTP 요청을 받아 알맞은 Controller 메서드를 호출한다 |
| Spring이 제공 | 트랜잭션 처리, JSON 변환 등 | 여러 클래스에 공통으로 필요한 기능을 처리한다 |
| 개발자가 작성 | Controller | 요청 URL별로 무엇을 할지 정한다 |
| 개발자가 작성 | Service | 애플리케이션의 규칙(예: 제목이 비면 저장하지 않음)을 처리한다 |
| 개발자가 작성 | Repository | DB에 데이터를 저장하고 조회한다 |

개발자는 Controller, Service, Repository 클래스를 작성하고 `@Controller`, `@Service`, `@Repository` 같은 표시(어노테이션)를 붙인다.
이 클래스들의 객체를 언제 만들고, 언제 어떤 메서드를 호출할지는 Spring이 결정한다.

> **어노테이션이란?**
> `@Service`처럼 `@`로 시작하며 클래스, 메서드, 필드 등에 붙여 추가 정보를 표시하는 Java 문법이다.
> 어노테이션은 그 자체로 동작하지 않는다. Spring 같은 프레임워크가 어노테이션을 읽고 그에 맞는 처리를 한다.
> 예를 들어 `@Service`를 읽은 Spring은 그 클래스의 객체를 Bean으로 등록한다.

> **Controller, Service, Repository란?**
> 웹 애플리케이션은 보통 역할에 따라 클래스를 세 계층으로 나눈다.
> - **Controller**: 사용자의 요청을 받고 응답을 돌려준다.
> - **Service**: 비즈니스 로직을 처리한다.
> - **Repository**: DB에 데이터를 저장하거나 DB에서 조회한다.
>
> 요청은 Controller → Service → Repository 순서로 전달된다. 자세한 내용은 [스프링 부트 계층 구조](../07_spring_boot_structure/01_layers.md)에서 설명한다.

> **트랜잭션이란?**
> DB에서 여러 작업을 하나의 단위로 묶어, 모두 성공하거나 모두 실패하도록 만드는 것이다. 자세한 내용은 [AOP](../03_spring_concepts/04_aop.md)에서 설명한다.

> **JSON이란?**
> `{"title": "첫 글", "content": "안녕하세요"}`처럼 이름과 값을 쌍으로 적는 텍스트 데이터 형식이다. 서버와 브라우저가 데이터를 주고받을 때 주로 사용한다.

## Spring의 실행 흐름
실행 흐름은 **애플리케이션이 시작될 때**와 **요청이 들어올 때** 두 단계로 나뉜다.

**1) 애플리케이션이 시작될 때 (한 번 실행)**
```
① 애플리케이션 실행 (Spring Boot에서는 main 메서드의 SpringApplication.run())
② Spring 컨테이너 생성
③ 컴포넌트 스캔: @Controller, @Service, @Repository 등이 붙은 클래스를 찾음
④ 찾은 클래스의 객체(Bean)를 생성
⑤ 의존성 주입: 각 객체가 필요로 하는 다른 객체를 생성자 등에 넣어 연결
   (예: PostService 객체를 만들 때 PostRepository 객체를 넣어줌)
⑥ 서버(Tomcat)를 시작하고 요청을 기다림
```

> **Bean이란?**
> Spring 컨테이너가 생성하고 관리하는 객체다. → [빈과 스프링 컨테이너](../03_spring_concepts/03_bean_and_container.md)

> **의존성 주입이란?**
> 객체가 필요로 하는 다른 객체를 스스로 `new`로 만들지 않고, 컨테이너가 넣어 주는 방식이다. → [DI](../03_spring_concepts/02_di.md)

> **Tomcat이란?**
> HTTP 요청을 받아 Java 웹 애플리케이션 코드를 실행하고 응답을 돌려주는 서버 프로그램(WAS)이다. → [내장 WAS와 그 밖의 차이](../02_spring_boot/03_difference_embedded_was.md)

**2) HTTP 요청이 들어올 때 (요청마다 반복)**
```
브라우저: GET /api/posts/1 요청
   ↓
Tomcat(서버)이 요청을 받음
   ↓
DispatcherServlet이 URL(/api/posts/1)에 맞는 Controller 메서드를 찾아 호출
   ↓
PostController.find(1)       ← 개발자가 작성
   ↓
PostService.find(1)          ← 개발자가 작성 (필요하면 Spring이 트랜잭션 시작)
   ↓
PostRepository.findById(1)   ← 개발자가 작성 (또는 Spring Data JPA가 자동 생성)
   ↓
DB에서 id가 1인 게시글 조회
   ↓
조회 결과가 Repository → Service → Controller 순서로 반환됨
   ↓
Spring이 반환된 Post 객체를 JSON으로 변환
   ↓
브라우저가 {"id":1, "title":"첫 글", ...} 응답을 받음
```

이 흐름에서 개발자가 직접 호출하는 코드는 Controller → Service → Repository 사이의 메서드 호출뿐이다.
객체 생성, 연결, 요청 받기, Controller 호출, JSON 변환은 모두 Spring이 처리한다.
이렇게 실행 흐름을 Spring이 결정한다는 점 때문에 Spring을 라이브러리가 아니라 프레임워크라고 한다.

## 주요 모듈
| 모듈 | 역할 |
|---|---|
| Spring Core | IoC 컨테이너, DI |
| Spring MVC | 웹 요청 처리 (Controller, DispatcherServlet) |
| Spring Data JPA | Repository 인터페이스만으로 DB 접근 |
| Spring Security | 인증, 인가 |
| Spring AOP | 공통 관심사 분리 |

> **모듈이란?**
> Spring은 기능별로 여러 라이브러리로 나뉘어 있고, 그 각각을 모듈이라고 한다. 프로젝트에는 필요한 모듈만 추가해서 사용한다.

> **HTTP와 웹 요청이란?**
> HTTP는 브라우저(또는 앱)와 서버가 데이터를 주고받을 때 사용하는 통신 규칙이다.
> 브라우저가 서버에 보내는 것을 **요청(request)**, 서버가 돌려주는 것을 **응답(response)** 이라고 한다.
> 요청에는 URL(`/api/posts`)과 HTTP 메서드(조회는 `GET`, 생성은 `POST` 등), 필요하면 보낼 데이터가 들어 있다.

> **MVC란?**
> Model-View-Controller의 줄임말로, 웹 애플리케이션 코드를 세 역할로 나누는 구조다.
> Model은 화면에 전달할 데이터, View는 사용자에게 보여줄 화면, Controller는 요청을 받아 Model을 준비하고 View를 선택하는 역할이다.

> **서블릿과 DispatcherServlet이란?**
> 서블릿은 Java에서 HTTP 요청을 받아 처리하고 응답을 만드는 클래스의 표준 규격이다.
> DispatcherServlet은 Spring MVC가 제공하는 서블릿으로, 모든 HTTP 요청을 가장 먼저 받은 뒤 요청 URL에 맞는 Controller 메서드를 찾아 호출한다.

> **인증과 인가란?**
> - **인증(Authentication)**: 사용자가 누구인지 확인하는 것 (예: 아이디와 비밀번호로 로그인)
> - **인가(Authorization)**: 확인된 사용자가 특정 작업을 할 권한이 있는지 확인하는 것 (예: 관리자만 다른 사람의 게시글을 삭제할 수 있음)

IoC, DI, AOP 같은 Spring의 핵심 개념은 [03_spring_concepts](../03_spring_concepts/01_ioc.md)에서 하나씩 설명한다.
Spring만 사용하면 설정할 것이 많은데, 이 부담을 줄이기 위해 나온 것이 Spring Boot다. → [Spring Boot](../02_spring_boot/01_spring_boot.md)
