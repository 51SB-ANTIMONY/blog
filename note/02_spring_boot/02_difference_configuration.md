---
title: Spring과 Spring Boot의 차이 1 - 구성의 차이
date: 2026-09-30
tags: [spring, spring-boot]
---

# 차이점 1. 구성의 차이

> 이 글에 나오는 Bean, 트랜잭션 같은 용어는 [Spring Framework](../01_spring/01_spring_framework.md)와 [03_spring_concepts](../03_spring_concepts/01_ioc.md)에서 설명했다.

**구성(configuration)** 은 애플리케이션에 어떤 Bean을 등록하고, 각 Bean에 어떤 값을 넣을지 정하는 일이다.
Spring은 이 구성을 개발자가 하나하나 작성해야 하고, Spring Boot는 대부분을 자동으로 처리한다.

## Spring만 사용할 때 직접 해야 하는 일
- `web.xml`에 `DispatcherServlet` 등록
- DataSource, TransactionManager, EntityManagerFactory 같은 Bean을 직접 정의
- 라이브러리 버전 호환성을 직접 맞추기
- Tomcat을 따로 설치한 뒤 WAR 파일로 배포

```xml
<!-- applicationContext.xml 일부 -->
<bean id="dataSource" class="com.zaxxer.hikari.HikariDataSource">
    <property name="jdbcUrl" value="jdbc:mysql://localhost:3306/blog"/>
    <property name="username" value="root"/>
    <property name="password" value="1234"/>
</bean>
<bean id="transactionManager"
      class="org.springframework.orm.jpa.JpaTransactionManager">
    <property name="entityManagerFactory" ref="entityManagerFactory"/>
</bean>
```

> **XML과 web.xml이란?**
> XML은 `<태그>값</태그>` 형식으로 데이터를 계층 구조로 적는 텍스트 형식이다. 예전 Spring에서는 Bean 정의를 XML 파일에 작성했다.
> 위 코드의 `<bean>` 태그 하나가 Bean 하나를 정의하고, `<property>`는 그 객체에 넣을 값을 지정한다.
> `web.xml`은 서블릿 기반 웹 애플리케이션의 설정 파일로, 어떤 서블릿이 어떤 URL 요청을 처리할지 등록한다.

> **DataSource, TransactionManager, EntityManagerFactory란?**
> - **DataSource**: DB 주소, 계정 정보를 가지고 DB 연결(커넥션)을 만들어 제공하는 객체
> - **TransactionManager**: 트랜잭션의 시작, 커밋, 롤백을 실제로 처리하는 객체
> - **EntityManagerFactory**: JPA에서 객체를 저장하고 조회하는 `EntityManager`를 만들어 주는 객체 ([엔티티와 엔티티 매니저](../09_database/03_entity_and_entity_manager.md))

> **커넥션 풀과 HikariCP란?**
> DB 연결(커넥션)을 새로 만드는 데는 시간이 걸린다. 그래서 커넥션을 미리 여러 개 만들어 두고, 요청이 올 때마다 빌려 쓰고 반납해서 재사용하는 방식을 커넥션 풀이라고 한다.
> HikariCP는 커넥션 풀 라이브러리이고, Spring Boot가 기본으로 사용한다.
> 위 XML의 `jdbc:mysql://localhost:3306/blog`는 "내 컴퓨터(localhost)의 3306번 포트에서 실행 중인 MySQL의 blog DB"에 연결하라는 뜻이다.

> **버전 호환성이란?**
> 라이브러리들은 서로의 클래스와 메서드를 사용하기 때문에, 특정 버전 조합에서만 정상적으로 동작한다.
> 버전이 맞지 않으면 실행 중에 필요한 클래스나 메서드를 찾지 못해 에러가 난다(예: `NoSuchMethodError`).

> **Java Config란?**
> XML 대신 Java 클래스로 Bean을 정의하는 방식이다. `@Configuration` 클래스 안에 `@Bean` 메서드를 작성하면, 그 메서드가 반환한 객체가 Bean으로 등록된다.
> ```java
> @Configuration
> public class AppConfig {
>     @Bean
>     public PostRepository postRepository() {
>         return new JpaPostRepository();
>     }
> }
> ```
> XML이든 Java Config든, Spring만 사용할 때는 필요한 Bean을 개발자가 모두 직접 정의해야 한다.

## Spring Boot에서는
같은 DB 연결을 Spring Boot에서는 다음 두 가지만 하면 된다.

1. `build.gradle`에 스타터와 DB 드라이버를 추가한다.
```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'com.mysql:mysql-connector-j'
```

2. `application.yml`에 접속 정보만 적는다.
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog
    username: root
    password: 1234
```

DataSource, TransactionManager, EntityManagerFactory Bean은 Spring Boot의 자동 구성이 만들어 준다.
라이브러리 버전도 Spring Boot가 서로 호환되는 버전으로 정해 준다.

- 스타터 → [스프링 부트 스타터](../04_spring_boot_starter/01_starter.md)
- 자동 구성 → [자동 구성](../05_auto_configuration/01_auto_configuration.md)
- `application.yml` → [내장 WAS와 그 밖의 차이](./03_difference_embedded_was.md)

## 정리
| 항목 | Spring | Spring Boot |
|---|---|---|
| Bean 정의 | XML 또는 Java Config로 개발자가 모두 작성 | 자동 구성이 대부분 등록, 필요한 것만 개발자가 추가 |
| 라이브러리 버전 | 하나하나 직접 지정 | 스타터와 Spring Boot가 관리 |
| 설정 값 | XML 안에 Bean 정의와 함께 작성 | `application.yml`에 값만 작성 |
