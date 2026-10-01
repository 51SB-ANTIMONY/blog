---
title: "@Component와 Bean 등록 어노테이션"
date: 2026-10-01
tags: [spring-boot, annotation, bean]
---

# @Component와 Bean 등록 어노테이션

> 이 글에 나오는 Bean, 컴포넌트 스캔 같은 용어는 [빈과 스프링 컨테이너](../03_spring_concepts/03_bean_and_container.md)와 [@SpringBootApplication](./01_spring_boot_application.md)에서 설명했다.

## @Component
클래스를 Bean으로 등록하라고 Spring에 알리는 가장 기본 어노테이션이다.
컴포넌트 스캔은 `@Component`가 붙은 클래스를 찾아서 Bean으로 등록한다.

```java
@Component
public class SlugGenerator {
    public String generate(String title) {
        return title.toLowerCase().replace(" ", "-");
    }
}
```
이제 다른 Bean에서 생성자로 `SlugGenerator`를 주입받을 수 있다.

## @Component가 들어 있는 어노테이션들
`@Service`, `@Repository`, `@Controller`, `@RestController`, `@Configuration`은 모두 내부에 `@Component`가 들어 있다.
그래서 이 어노테이션을 붙여도 컴포넌트 스캔 대상이 된다.

```java
@Component  // @Service 정의에 @Component가 붙어 있다
public @interface Service { ... }
```

| 어노테이션 | 붙이는 곳 | `@Component` 외에 추가되는 것 |
|---|---|---|
| `@Controller` | 프레젠테이션 계층 (요청 처리) | 요청 URL과 메서드를 연결하는 대상이 된다 |
| `@RestController` | REST API용 Controller | `@Controller` + `@ResponseBody` (반환값을 JSON으로 응답) |
| `@Service` | 비즈니스 계층 | 기능은 `@Component`와 같고, 역할을 표시한다 |
| `@Repository` | 퍼시스턴스 계층 (DB 접근) | DB 예외를 Spring 예외(`DataAccessException`)로 바꿔 준다 |
| `@Configuration` | 설정 클래스 | 안에 있는 `@Bean` 메서드로 Bean을 등록한다 |

계층에 대한 설명은 [스프링 부트 계층 구조](../07_spring_boot_structure/01_layers.md)에 있다.

## 왜 @Component 하나로 쓰지 않을까
기능만 보면 `@Service` 자리에 `@Component`를 써도 동작한다.
하지만 역할별 어노테이션을 쓰면 다음과 같은 장점이 있다.
- 클래스 이름 위의 어노테이션만 보고 어느 계층의 클래스인지 알 수 있다.
- `@Repository`처럼 역할에 맞는 추가 기능이 적용된다.
- AOP를 적용할 때 "모든 `@Service` 클래스"처럼 대상을 골라낼 수 있다. ([AOP](../03_spring_concepts/04_aop.md))

> **Spring Data JPA의 Repository**
> `JpaRepository`를 상속한 Repository 인터페이스에는 `@Repository`를 붙이지 않아도 된다. Spring Data JPA가 구현 객체를 만들어 Bean으로 등록한다. ([스프링 데이터 JPA](../09_database/05_spring_data_jpa.md))
