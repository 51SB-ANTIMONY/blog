---
title: "@SpringBootApplication과 main 메서드"
date: 2026-10-01
tags: [spring-boot, annotation]
---

# @SpringBootApplication과 main 메서드

> 이 글에 나오는 Bean, 컴포넌트 스캔, 자동 구성 같은 용어는 [빈과 스프링 컨테이너](../03_spring_concepts/03_bean_and_container.md), [자동 구성](../05_auto_configuration/01_auto_configuration.md)에서 설명했다.

## 시작 클래스
Spring Boot 프로젝트를 만들면 다음과 같은 시작 클래스가 하나 생긴다.
```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

> **main 메서드와 SpringApplication.run이란?**
> `main` 메서드는 Java 프로그램을 실행하면 가장 먼저 호출되는 메서드다.
> `SpringApplication.run()`은 Spring 컨테이너를 만들고, 자동 구성을 적용하고, Bean을 등록한 뒤 내장 Tomcat을 시작한다.
> 첫 번째 인자(`DemoApplication.class`)는 시작 클래스이고, 두 번째 인자(`args`)는 실행할 때 넘긴 명령줄 인자다.

## @SpringBootApplication
세 어노테이션을 합친 것이다.

| 어노테이션 | 역할 |
|---|---|
| `@SpringBootConfiguration` | 이 클래스가 Spring Boot의 설정 클래스임을 표시한다. 내부에 `@Configuration`이 들어 있어서, 이 클래스 안에 `@Bean` 메서드를 쓸 수 있다 |
| `@EnableAutoConfiguration` | Spring Boot의 자동 구성을 켠다 |
| `@ComponentScan` | 이 클래스가 있는 패키지와 그 하위 패키지에서 `@Component`가 붙은 클래스를 찾아 Bean으로 등록한다 |

> **패키지란?**
> Java 클래스를 묶는 폴더 단위다. 파일 맨 위의 `package com.example.demo;`가 이 클래스의 패키지를 나타낸다.
> `DemoApplication`이 `com.example.demo` 패키지에 있으므로, `com.example.demo.controller`, `com.example.demo.service` 같은 하위 패키지의 클래스도 검색 대상이 된다.
> 반대로 `com.example.other` 같은 다른 패키지에 있는 클래스는 찾지 못한다. 그래서 시작 클래스는 보통 가장 위쪽 패키지에 둔다.

## 실제 코드 확인
`@SpringBootApplication`의 소스 코드를 열어 보면 다음과 같이 세 어노테이션이 붙어 있다. (일부 생략)
```java
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(excludeFilters = { ... })
public @interface SpringBootApplication { ... }
```

> **@interface란?**
> 어노테이션을 정의할 때 쓰는 Java 문법이다. `public @interface SpringBootApplication`은 `@SpringBootApplication`이라는 어노테이션을 만든다는 뜻이다.
> 어노테이션 정의 위에 다른 어노테이션을 붙이면, 그 어노테이션을 쓸 때 위에 붙인 어노테이션들도 함께 적용된다.
