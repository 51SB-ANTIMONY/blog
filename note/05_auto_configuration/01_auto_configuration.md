---
title: 스프링 부트 자동 구성 (Auto-Configuration)
date: 2026-10-01
tags: [spring-boot, auto-configuration]
---

# 자동 구성 (Auto-Configuration)

> 이 글에 나오는 Bean, 스타터, DataSource 같은 용어는 [빈과 스프링 컨테이너](../03_spring_concepts/03_bean_and_container.md), [스프링 부트 스타터](../04_spring_boot_starter/01_starter.md), [차이점 1](../02_spring_boot/02_difference_configuration.md)에서 설명했다.

## 정의
Spring Boot는 애플리케이션이 최소한의 설정만으로도 실행되도록 여러 부분을 자동으로 구성한다.
서버를 시작할 때 미리 준비된 구성 파일을 읽어서, 필요한 Bean을 알아서 등록한다. 이것을 **자동 구성(자동 설정)** 이라고 한다.

자동 구성은 다음 두 가지를 확인해서 어떤 Bean을 등록할지 정한다.
1. 클래스패스에 어떤 라이브러리가 있는가
2. `application.yml`에 어떤 설정값이 있는가

> **클래스패스란?**
> Java가 프로그램을 실행할 때 클래스 파일과 라이브러리를 찾는 경로 목록이다.
> Gradle에 라이브러리를 추가하면 그 라이브러리가 클래스패스에 포함된다.
> Spring Boot는 "클래스패스에 이 클래스가 있는가?"를 확인해서 어떤 자동 구성을 적용할지 결정한다.

## 예시
- `spring-boot-starter-data-jpa`와 MySQL 드라이버가 있으면 → DataSource, EntityManagerFactory, TransactionManager가 자동 등록된다.
- `spring-boot-starter-web`이 있으면 → DispatcherServlet, 내장 Tomcat, Jackson의 `ObjectMapper`가 자동 등록된다.
- H2가 있고 DB 주소를 따로 적지 않았으면 → 인메모리 H2에 연결하는 DataSource가 자동 등록된다.

## 자동 구성 목록은 어디에 있나
자동 구성 클래스의 목록은 Spring Boot 라이브러리 JAR 안의 `META-INF` 폴더에 파일로 들어 있다.

| Spring Boot 버전 | 목록 파일 |
|---|---|
| 2.6 이하 | `META-INF/spring.factories` |
| 2.7 이상 (3.x, 4.x 포함) | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

예전에는 `spring.factories`에 자동 구성 목록을 적었다. Spring Boot 3.0부터는 자동 구성 목록을 `spring.factories`에서 읽지 않고, `AutoConfiguration.imports` 파일만 사용한다.

> **META-INF란?**
> JAR 파일 안에 있는 특별한 폴더로, 그 JAR에 대한 정보(설정 파일, 목록 파일 등)를 담는다.

`AutoConfiguration.imports` 파일 안에는 자동 구성 클래스 이름이 한 줄에 하나씩 적혀 있다. (아래는 Spring Boot 3 기준 예시)
```
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
...
```
Spring Boot 4에서는 자동 구성이 기능별 모듈(JAR)로 나뉘어서, 각 모듈 JAR가 자기 `AutoConfiguration.imports` 파일을 가지고 있다. 그래서 클래스의 패키지 이름도 3과 다르다.

## 자동 구성이 동작하는 순서
```
① @SpringBootApplication 안의 @EnableAutoConfiguration이 자동 구성을 켬
② AutoConfiguration.imports 파일에서 자동 구성 클래스 목록을 읽음
③ 각 클래스에 붙은 조건(@Conditional...)을 확인
④ 조건을 만족하는 클래스의 Bean만 등록
```

목록에 있는 클래스가 모두 적용되는 것은 아니다. 각 자동 구성 클래스에는 조건이 붙어 있다.

| 조건 어노테이션 | 의미 |
|---|---|
| `@ConditionalOnClass` | 클래스패스에 특정 클래스가 있을 때만 적용 |
| `@ConditionalOnMissingBean` | 같은 타입의 Bean이 아직 없을 때만 적용 |
| `@ConditionalOnProperty` | `application.yml`에 특정 설정값이 있을 때만 적용 |

> **@ConditionalOnMissingBean이란?**
> "컨테이너에 이 타입의 Bean이 아직 없을 때만 이 Bean을 등록하라"는 조건을 나타내는 어노테이션이다.
> Spring Boot의 자동 구성 코드에 붙어 있어서, 개발자가 직접 `DataSource` Bean을 등록하면 자동 구성의 `DataSource`는 등록되지 않는다.
> 즉 **개발자가 정의한 Bean이 자동 구성보다 우선한다.**

## 어떤 자동 구성이 적용됐는지 확인하는 방법
`application.yml`에 다음을 추가하고 실행하면, 콘솔에 적용된 자동 구성(Positive matches)과 적용되지 않은 자동 구성(Negative matches)이 출력된다.
```yaml
debug: true
```
