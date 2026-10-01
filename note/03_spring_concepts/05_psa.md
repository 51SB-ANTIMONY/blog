---
title: PSA (Portable Service Abstraction, 이식 가능한 서비스 추상화)
date: 2026-09-30
tags: [spring, psa]
---

# PSA (Portable Service Abstraction, 이식 가능한 서비스 추상화)

> 이 글에 나오는 트랜잭션, `@Transactional`, 인터페이스와 구현체 같은 용어는 [AOP](./04_aop.md), [DI](./02_di.md)에서 설명했다.

## 정의
특정 기술에 종속되지 않도록 추상화된 인터페이스를 제공하는 것이다.
"이식 가능하다"는 말은, 사용하는 기술을 바꿔도 그 기능을 사용하는 코드는 그대로 옮겨 쓸 수 있다는 뜻이다.

> **추상화란?**
> 여러 구현의 공통된 동작만 뽑아서 하나의 인터페이스로 정의하고, 사용하는 쪽은 그 인터페이스만 사용하게 하는 것이다.
> 이렇게 하면 구현이 바뀌어도 인터페이스를 사용하는 코드는 수정할 필요가 없다.

## 예시 1: 트랜잭션
`@Transactional`은 JDBC, JPA, Hibernate 중 무엇을 쓰든 같은 방식으로 사용할 수 있다.
내부에서는 `PlatformTransactionManager` 인터페이스의 구현체만 달라진다.

- `PlatformTransactionManager`: 트랜잭션 시작, 커밋, 롤백 메서드를 정의한 인터페이스
- JDBC용 구현체: `DataSourceTransactionManager`
- JPA용 구현체: `JpaTransactionManager`

DB 접근 기술을 JDBC에서 JPA로 바꿔도 `@Transactional`을 붙인 Service 코드는 고치지 않는다.

> **JDBC, JPA, Hibernate란?**
> - **JDBC**: Java에서 DB에 연결하고 SQL을 실행하기 위한 Java 표준 API다. 개발자가 SQL을 직접 작성한다.
> - **JPA**: Java 객체와 DB 테이블을 연결(매핑)해서, SQL을 직접 작성하지 않고 객체를 저장하고 조회할 수 있게 해주는 Java 표준 명세다.
> - **Hibernate**: JPA 명세를 실제로 구현한 라이브러리다.
>
> 자세한 내용은 [JPA와 하이버네이트](../09_database/02_jpa_and_hibernate.md)에서 설명한다.

> **API란?**
> 어떤 프로그램이나 라이브러리가 다른 코드에서 자기 기능을 사용할 수 있도록 공개한 메서드, 클래스, 규칙의 모음이다. → [API](../10_rest_api/01_api.md)

## 예시 2: 웹 요청 처리
`@GetMapping`, `@RequestBody` 같은 Spring MVC 어노테이션은 내장 서버가 Tomcat이든 Jetty든 똑같이 동작한다.
서버를 바꿔도 Controller 코드는 그대로다.

## 예시 3: 캐시
`@Cacheable`은 캐시 저장소가 메모리든 Redis든 같은 어노테이션으로 사용한다. 내부에서는 `CacheManager` 인터페이스의 구현체만 바뀐다.

> **캐시와 Redis란?**
> 캐시는 한 번 조회한 결과를 저장해 두고, 같은 요청이 오면 DB를 다시 조회하지 않고 저장해 둔 결과를 돌려주는 방식이다.
> Redis는 데이터를 메모리에 저장하는 별도의 저장소 프로그램으로, 캐시 저장소로 자주 쓰인다.

## 정리
Spring의 핵심 개념 네 가지를 정리하면 다음과 같다.
| 개념 | 한 줄 정리 |
|---|---|
| IoC | 객체 생성과 연결의 제어권을 Spring 컨테이너가 가진다 |
| DI | 필요한 객체를 컨테이너가 넣어 준다 (IoC를 구현하는 방법) |
| AOP | 트랜잭션, 로깅 같은 공통 기능을 비즈니스 로직과 분리한다 |
| PSA | 기술이 바뀌어도 같은 방식으로 사용할 수 있는 인터페이스를 제공한다 |
