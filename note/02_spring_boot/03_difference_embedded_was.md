---
title: Spring과 Spring Boot의 차이 2 - 내장 WAS의 유무와 그 밖의 차이
date: 2026-10-01
tags: [spring, spring-boot, was, h2]
---

# 차이점 2. 내장 WAS의 유무와 그 밖의 차이

> 이 글에 나오는 Bean, 서블릿 같은 용어는 [Spring Framework](../01_spring/01_spring_framework.md)에서, XML은 [차이점 1](./02_difference_configuration.md)에서 설명했다.

| 항목 | Spring | Spring Boot |
|---|---|---|
| 목적 | 엔터프라이즈 애플리케이션 개발을 더 쉽게 만들기 | Spring 개발을 더 빠르고 쉽게 만들기 |
| 설정 파일 | 개발자가 직접 작성 | 자동 구성, 필요한 값만 `application.yml`에 작성 |
| XML | 사용함 | 사용하지 않음 |
| 인메모리 DB 지원 | 기본 지원 없음 | H2 같은 인메모리 DB를 자동으로 설정 |
| 서버 | 외부 WAS를 설치하고 WAR로 배포 | 내장 WAS, 실행 가능한 JAR |

아래에서 항목을 하나씩 설명한다.

## 1. 목적
- **Spring**: 엔터프라이즈 애플리케이션을 개발할 때 필요한 기능(DI, 트랜잭션, 웹 요청 처리 등)을 제공한다.
- **Spring Boot**: 그 Spring을 설정 없이 바로 쓸 수 있게 해서, 개발을 시작하고 실행하는 데 드는 시간을 줄인다.

> **엔터프라이즈 애플리케이션이란?**
> 회사나 기관이 업무에 사용하는 대규모 애플리케이션이다. 많은 사용자의 요청을 동시에 처리하고, DB 트랜잭션과 보안이 중요하다.

## 2. 설정 파일
Spring Boot에서는 XML Bean 정의 대신 `application.yml`(또는 `application.properties`)에 값만 적는다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/blog
    username: root
    password: 1234
  jpa:
    hibernate:
      ddl-auto: update
server:
  port: 8080
```

> **YAML과 properties란?**
> 둘 다 설정 값을 적는 파일 형식이다.
> YAML(`.yml`)은 들여쓰기로 계층을 표현하고, properties(`.properties`)는 한 줄에 `키=값` 형식으로 적는다. 아래 두 설정은 같은 의미다.
> ```yaml
> server:
>   port: 8080
> ```
> ```properties
> server.port=8080
> ```

> **포트와 localhost란?**
> 포트는 한 컴퓨터에서 여러 프로그램이 네트워크 통신을 할 때 각 프로그램을 구분하는 번호다.
> localhost는 지금 사용 중인 내 컴퓨터를 가리키는 주소다.
> `server.port: 8080`으로 실행하면 브라우저에서 `http://localhost:8080`으로 이 애플리케이션에 접속할 수 있다.

> **ddl-auto란?**
> Hibernate가 애플리케이션 시작 시 엔티티 클래스를 보고 DB 테이블을 자동으로 만들거나 수정할지 정하는 설정이다.
> | 값 | 동작 |
> |---|---|
> | `create` | 시작할 때 테이블을 지우고 새로 만든다 |
> | `create-drop` | `create`와 같고, 종료할 때 테이블을 지운다 (H2 같은 내장 DB의 기본값) |
> | `update` | 엔티티에 새로 추가된 필드를 테이블에 반영한다 |
> | `validate` | 엔티티와 테이블이 일치하는지 확인만 한다 |
> | `none` | 아무것도 하지 않는다 |
>
> 실제 서비스(운영) 환경에서는 데이터가 의도치 않게 바뀔 수 있어서 `validate`나 `none`을 사용한다.
> (DDL은 테이블 생성, 수정, 삭제처럼 DB 구조를 정의하는 SQL 명령이다.)

## 3. XML
Spring은 Bean 정의와 서블릿 등록을 XML로 작성할 수 있다 ([차이점 1](./02_difference_configuration.md)의 `applicationContext.xml`, `web.xml`).
Spring Boot는 XML을 쓰지 않는다. Bean은 자동 구성과 어노테이션으로 등록하고, 설정 값은 `application.yml`에 적는다.

## 4. 인메모리 데이터베이스 지원
Spring Boot는 클래스패스에 H2 같은 인메모리 DB 라이브러리가 있으면, 접속 정보를 적지 않아도 DataSource를 자동으로 만든다.

> **인메모리 데이터베이스란?**
> 데이터를 하드디스크 파일이 아니라 컴퓨터 메모리(RAM)에 저장하는 DB다.
> 별도 설치 없이 애플리케이션과 함께 실행되고, 애플리케이션을 종료하면 데이터가 모두 사라진다.
> 그래서 학습, 개발, 테스트할 때 주로 사용한다.

> **H2란?**
> Java로 만들어진 가벼운 DB다. 인메모리 모드로 실행할 수 있어서 Spring Boot 학습과 테스트에 많이 쓴다.
> H2 콘솔을 켜면 브라우저에서 `http://localhost:8080/h2-console`로 접속해 테이블과 데이터를 직접 확인할 수 있다.

H2를 사용하는 설정 예시는 다음과 같다.

```gradle
// build.gradle
runtimeOnly 'com.h2database:h2'                               // H2 DB
implementation 'org.springframework.boot:spring-boot-h2console' // H2 콘솔 (Spring Boot 4에서 분리된 모듈)
```

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb   # mem = 메모리에 testdb라는 이름의 DB를 만든다
    username: sa
  h2:
    console:
      enabled: true           # /h2-console 화면을 켠다
```

## 5. 서버 (내장 WAS)
Spring만 사용할 때는 Tomcat 같은 WAS를 따로 설치하고, 애플리케이션을 WAR 파일로 만들어 그 WAS에 올려야 했다.
Spring Boot는 Tomcat(또는 Jetty, Undertow)이 애플리케이션 안에 포함되어 있다. 빌드한 JAR 하나를 `java -jar`로 실행하면 서버가 뜬다.

```bash
./gradlew build
java -jar build/libs/blog-0.0.1-SNAPSHOT.jar
```

> **WAS, Tomcat, WAR, 배포란?**
> - **WAS(Web Application Server)**: HTTP 요청을 받아 Java 웹 애플리케이션 코드(서블릿)를 실행하고 응답을 돌려주는 서버 프로그램
> - **Tomcat**: 가장 널리 쓰이는 Java WAS(정확히는 서블릿 컨테이너)
> - **WAR(Web Application Archive)**: Java 웹 애플리케이션을 WAS에 올리기 위해 클래스 파일과 설정 파일을 하나로 묶은 파일
> - **배포**: 작성한 애플리케이션을 서버에 올려서 사용자가 접속할 수 있는 상태로 만드는 것

> **내장 서버란?**
> WAS를 따로 설치하지 않고, WAS 라이브러리를 애플리케이션 안에 포함시킨 것이다.
> 애플리케이션이 시작될 때 코드 안에서 Tomcat을 직접 실행한다. Jetty, Undertow는 Tomcat과 같은 역할을 하는 다른 WAS다.

> **JAR란?**
> Java Archive의 줄임말로, 클래스 파일과 리소스 파일을 하나로 묶은 압축 파일이다.
> Spring Boot는 내장 서버와 모든 라이브러리를 포함한 **실행 가능한 JAR**를 만들어서, `java -jar 파일명.jar` 명령 하나로 실행할 수 있다.

> **`./gradlew`란?**
> Gradle Wrapper라는 스크립트다. 프로젝트에 포함되어 있어서, 컴퓨터에 Gradle을 설치하지 않아도 프로젝트에 지정된 버전의 Gradle로 빌드할 수 있다.
> Gradle은 [스프링 부트 스타터](../04_spring_boot_starter/01_starter.md)에서 설명한다.
