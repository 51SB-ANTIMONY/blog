---
title: 빈(Bean)과 스프링 컨테이너
date: 2026-10-01
tags: [spring, bean, container]
---

# 빈(Bean)과 스프링 컨테이너

> 이 글에 나오는 IoC, DI, 어노테이션 같은 용어는 [IoC](./01_ioc.md), [DI](./02_di.md), [Spring Framework](../01_spring/01_spring_framework.md)에서 설명했다.

## 스프링 컨테이너
애플리케이션이 실행될 때 Bean을 생성하고, 필요한 곳에 연결(DI)하고, 애플리케이션이 종료될 때 정리하는 Spring의 구성 요소다.
Java 코드에서는 `ApplicationContext` 인터페이스와 그 구현 클래스가 이 역할을 한다.
애플리케이션이 시작되면 Spring은 `@Service`, `@Repository` 같은 표시가 붙은 클래스를 찾아 객체를 만들고 컨테이너 안에 보관한다.

컨테이너가 하는 일을 순서대로 정리하면 다음과 같다.
1. Bean으로 등록할 클래스를 찾는다.
2. 각 클래스의 객체를 만든다.
3. 객체가 필요로 하는 다른 Bean을 넣어 준다(DI).
4. 애플리케이션이 실행되는 동안 Bean을 보관하고, 필요한 곳에 제공한다.
5. 애플리케이션이 종료되면 Bean을 정리한다.

## Bean
Spring 컨테이너가 생성하고 관리하는 객체다. 코드 자체는 일반 Java 객체와 같고, 개발자가 `new`로 만들었는지 컨테이너가 만들었는지가 다르다.

### Bean으로 등록하는 방법
**1) 클래스에 어노테이션 붙이기 (컴포넌트 스캔)**
```java
@Service
public class PostService { ... }
```
`@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`, `@Configuration`이 붙은 클래스는 컴포넌트 스캔으로 찾아서 Bean으로 등록된다.

> **컴포넌트 스캔이란?**
> 지정한 패키지와 그 하위 패키지의 클래스를 모두 살펴보고, Bean 등록용 어노테이션이 붙은 클래스를 찾아 Bean으로 등록하는 기능이다.
> Spring Boot에서는 `@SpringBootApplication`이 붙은 클래스의 패키지부터 검색한다. ([@SpringBootApplication](../06_spring_boot_code/01_spring_boot_application.md))

**2) `@Configuration` 클래스에 `@Bean` 메서드 작성하기**
```java
@Configuration
public class AppConfig {
    @Bean
    public PostRepository postRepository() {
        return new JpaPostRepository();  // 이 메서드가 반환한 객체가 Bean이 됨
    }
}
```
직접 만든 클래스가 아니라서 어노테이션을 붙일 수 없는 객체(예: 외부 라이브러리의 클래스)를 Bean으로 등록할 때 사용한다.

### Bean의 이름
Bean은 컨테이너 안에서 이름으로 구분된다.
- 컴포넌트 스캔으로 등록하면 클래스 이름의 첫 글자를 소문자로 바꾼 것이 이름이 된다. (`PostService` → `postService`)
- `@Bean`으로 등록하면 메서드 이름이 Bean 이름이 된다. (`postRepository()` → `postRepository`)

### Bean은 기본적으로 하나만 만들어진다 (싱글톤)
Spring은 기본적으로 클래스 하나당 Bean 객체를 **하나만** 만들고, 그 객체를 필요한 모든 곳에 같은 객체로 넣어 준다.
예를 들어 Controller 10개가 `PostService`를 주입받아도, 모두 같은 `PostService` 객체를 사용한다.

> **싱글톤이란?**
> 애플리케이션 전체에서 어떤 클래스의 객체가 딱 하나만 존재하도록 하는 방식이다.
> 요청이 올 때마다 객체를 새로 만들지 않으므로 메모리를 아끼고 객체 생성 시간을 줄일 수 있다.
> 대신 여러 요청이 같은 객체를 동시에 사용하므로, Bean 안에 요청마다 달라지는 값을 필드로 저장하면 안 된다.
> ```java
> @Service
> public class PostService {
>     private String currentTitle;  // 잘못된 예: 여러 요청이 이 값을 덮어씀
> }
> ```

## 정리
- **컨테이너**: Bean을 만들고, 연결하고, 보관하고, 정리하는 Spring의 구성 요소 (`ApplicationContext`)
- **Bean**: 컨테이너가 관리하는 객체. 어노테이션(컴포넌트 스캔) 또는 `@Bean` 메서드로 등록하고, 기본적으로 하나만 만들어진다.
