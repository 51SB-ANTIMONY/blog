---
title: IoC (Inversion of Control, 제어의 역전)
date: 2026-09-30
tags: [spring, ioc]
---

# IoC (Inversion of Control, 제어의 역전)

> 이 글에 나오는 프레임워크, 컨테이너 같은 용어는 [Spring Framework](../01_spring/01_spring_framework.md)에서 설명했다.

## 정의
객체를 생성하고, 생명주기를 관리하고, 객체끼리 연결하는 권한이 개발자 코드에서 프레임워크로 넘어간다는 개념이다.
원래 개발자 코드가 쥐고 있던 제어권이 프레임워크로 넘어가기 때문에 "제어의 역전"이라고 한다.
Spring에서는 Spring 컨테이너가 이 제어권을 가지며, 컨테이너가 관리하는 객체를 **Bean**이라고 한다.

> **생명주기란?**
> 객체가 생성되어 사용되다가 더 이상 쓰이지 않아 정리될 때까지의 과정이다.
> Spring에서는 컨테이너가 Bean을 언제 만들고 언제 정리할지 결정한다.

> **Bean이란?**
> Spring 컨테이너가 생성하고 관리하는 객체다. 자세한 내용은 [빈과 스프링 컨테이너](./03_bean_and_container.md)에서 설명한다.

## 코드로 비교하기

### 제어권이 개발자 코드에 있을 때
```java
public class PostService {
    // PostService가 직접 객체를 만든다
    private final PostRepository repository = new JpaPostRepository();
}

public class Main {
    public static void main(String[] args) {
        PostService service = new PostService();  // 개발자가 직접 생성
        service.find(1L);                          // 개발자가 직접 호출
    }
}
```
어떤 객체를 언제 만들고 무엇과 연결할지 모두 개발자 코드가 정한다.

### 제어권이 Spring에 있을 때
```java
@Service
public class PostService {
    private final PostRepository repository;

    public PostService(PostRepository repository) {
        this.repository = repository;
    }
}
```
코드 어디에도 `new PostService()`나 `new JpaPostRepository()`가 없다.
Spring 컨테이너가 애플리케이션 시작 시 두 객체를 만들고, `PostService`를 만들 때 `PostRepository` 객체를 생성자에 넣어 준다.
요청이 들어오면 Controller의 메서드를 호출하는 것도 Spring이다.

## IoC와 DI의 관계
IoC는 "제어권을 프레임워크로 넘긴다"는 개념이고, 그 개념을 구현하는 구체적인 방법이 **DI(의존성 주입)** 다.
→ [DI](./02_di.md)
