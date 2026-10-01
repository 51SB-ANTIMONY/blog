---
title: 스프링 부트 스타터 테스트
date: 2026-10-01
tags: [test, spring-boot, starter]
---

# 스프링 부트 스타터 테스트

> 스타터는 [스프링 부트 스타터](../04_spring_boot_starter/01_starter.md)에서 설명했다.

## 정의
`spring-boot-starter-test`는 테스트에 필요한 라이브러리를 묶은 스타터다.
```gradle
testImplementation 'org.springframework.boot:spring-boot-starter-test'
```

## 들어 있는 라이브러리
| 라이브러리 | 용도 |
|---|---|
| **JUnit** | Java 테스트를 작성하고 실행하는 기본 프레임워크 |
| Spring Test & Spring Boot Test | Spring 컨테이너를 띄운 상태로 테스트하는 기능 (`@SpringBootTest`, `MockMvc` 등) |
| **AssertJ** | 결과를 검증하는 메서드(`assertThat(...).isEqualTo(...)`)를 제공 |
| Hamcrest | 결과를 검증하는 다른 방식의 라이브러리 (`is()`, `equalTo()` 같은 매처 사용) |
| Mockito | 테스트용 가짜 객체(목 객체)를 만드는 라이브러리 |
| JSONassert | JSON 문자열이 기대한 내용과 같은지 비교 |
| JsonPath | JSON 안의 특정 값을 경로(`$.title`)로 꺼냄 |

이 중 **JUnit과 AssertJ를 가장 많이 사용한다.**

> **검증(assertion)이란?**
> 테스트에서 "이 값은 이래야 한다"고 확인하는 것이다. 확인한 값이 기대와 다르면 테스트가 실패한다.

> **매처(matcher)란?**
> Hamcrest에서 값이 어떤 조건을 만족하는지 표현하는 객체다. `assertThat(sum, is(3))`의 `is(3)`이 매처다.

> **목 객체(mock)란?**
> 실제 객체 대신 테스트에서 사용하는 가짜 객체다. 어떤 메서드가 호출되면 무엇을 반환할지 테스트 코드에서 정할 수 있다.
> ```java
> PostRepository repository = Mockito.mock(PostRepository.class);
> when(repository.findById(1L)).thenReturn(Optional.of(post));  // findById(1)이 호출되면 post를 반환
> ```

> **JsonPath 경로 문법**
> - `$`: JSON 전체
> - `$.title`: 최상위 객체의 `title` 값
> - `$[0].title`: 배열의 첫 번째 요소의 `title` 값

## Spring Boot 4의 테스트 스타터
Spring Boot 4부터는 기능별 테스트 스타터가 따로 나뉘었다. 예를 들면 다음과 같다.
| 스타터 | 제공하는 것 |
|---|---|
| `spring-boot-starter-webmvc-test` | `@AutoConfigureMockMvc`, `@WebMvcTest` 등 웹 계층 테스트 기능 |
| `spring-boot-starter-data-jpa-test` | `@DataJpaTest` 등 JPA 테스트 기능 |

그래서 Spring Boot 4에서 `@AutoConfigureMockMvc`는 `org.springframework.boot.webmvc.test.autoconfigure` 패키지에서 가져온다.
