---
title: 스프링 부트 스타터
date: 2026-10-01
tags: [spring-boot, starter, gradle]
---

# 스프링 부트 스타터

> 이 글에 나오는 버전 호환성, 내장 서버 같은 용어는 [Spring Boot](../02_spring_boot/01_spring_boot.md) 폴더의 글에서 설명했다.

## 정의
스타터(Starter)는 특정 작업에 필요한 라이브러리를 묶어 두고, 서로 호환되는 버전까지 맞춰 둔 의존성 묶음이다.
스타터 하나를 추가하면 그 안에 들어 있는 여러 라이브러리가 함께 추가된다.

> **의존성(라이브러리 의존성)이란?**
> 프로젝트가 동작하려면 필요한 외부 라이브러리를 말한다.
> [DI](../03_spring_concepts/02_di.md)에서 말한 "클래스 사이의 의존성"과 같은 단어지만, 여기서는 "프로젝트가 사용하는 라이브러리"라는 뜻이다.

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'      // Spring MVC + 내장 Tomcat + Jackson
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa' // JPA + Hibernate + HikariCP
    implementation 'org.springframework.boot:spring-boot-starter-security'
    runtimeOnly 'com.mysql:mysql-connector-j'
}
```
각 라이브러리의 버전을 따로 적지 않아도 된다. 사용 중인 Spring Boot 버전이 호환되는 버전을 정해 준다.

> **Gradle이란?**
> Java 프로젝트의 빌드 도구다. 프로젝트에 필요한 라이브러리를 인터넷에서 내려받아 추가하고, 코드를 빌드한다.
> 설정은 `build.gradle` 파일에 작성하며, 위 코드의 `dependencies { ... }` 블록에 사용할 라이브러리를 적는다.
> - `implementation`: 코드를 컴파일할 때와 실행할 때 모두 필요한 라이브러리
> - `runtimeOnly`: 실행할 때만 필요한 라이브러리 (MySQL 드라이버는 코드에서 직접 사용하지 않으므로 여기에 둔다)
> - `testImplementation`: 테스트 코드를 컴파일하고 실행할 때만 필요한 라이브러리
> - `compileOnly`: 컴파일할 때만 필요하고 실행할 때는 필요 없는 라이브러리 (예: Lombok)

> **빌드와 컴파일이란?**
> 컴파일은 사람이 작성한 `.java` 소스 코드를 JVM(Java 실행 환경)이 실행할 수 있는 `.class` 파일로 변환하는 것이다.
> 빌드는 컴파일을 포함해 테스트 실행, 라이브러리와 함께 묶기 등을 거쳐 실행 가능한 결과물을 만드는 전체 과정이다.

> **Jackson이란?**
> Java 객체를 JSON으로, JSON을 Java 객체로 변환하는 라이브러리다.

> **MySQL과 드라이버란?**
> MySQL은 널리 쓰이는 DB 프로그램이다.
> 드라이버(JDBC 드라이버)는 JDBC 인터페이스를 특정 DB에 맞게 구현한 라이브러리다. Java 코드가 MySQL과 실제로 통신하려면 MySQL용 드라이버가 필요하다.

## 명명 규칙
스타터 이름은 `spring-boot-starter-{작업 유형}` 형식이다.
이름만 보고 어떤 작업에 쓰는 스타터인지 알 수 있다.

## 자주 사용하는 스타터
| 스타터 | 용도 | 들어 있는 주요 라이브러리 |
|---|---|---|
| `spring-boot-starter-web` | Spring MVC로 웹 애플리케이션과 REST API를 만들 때 | Spring MVC, 내장 Tomcat, Jackson |
| `spring-boot-starter-test` | 테스트 코드를 작성할 때 | JUnit, AssertJ, Mockito 등 ([스프링 부트 스타터 테스트](../08_test/02_spring_boot_starter_test.md)) |
| `spring-boot-starter-validation` | 입력값을 검증할 때 | Hibernate Validator |
| `spring-boot-starter-actuator` | 애플리케이션 상태를 확인할 때 | Micrometer 등 |
| `spring-boot-starter-data-jpa` | JPA로 DB를 사용할 때 | Spring Data JPA, Hibernate, HikariCP |

> **Spring Boot 4에서 바뀐 점**
> Spring Boot 4부터 스타터가 기능별로 더 잘게 나뉘었다.
> - 웹 스타터의 새 이름은 `spring-boot-starter-webmvc`다. 기존 `spring-boot-starter-web`도 아직 사용할 수 있지만 새 이름을 쓰는 것이 권장된다.
> - 테스트 스타터도 기능별로 나뉘었다. 예: `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`
> - H2 콘솔처럼 예전에는 자동으로 포함되던 기능이 `spring-boot-h2console` 같은 별도 모듈로 분리되었다.

### validation 예시
```java
public class AddPostRequest {
    @NotBlank   // 비어 있으면 안 됨
    @Size(max = 100)  // 100자 이하
    private String title;
}

@PostMapping("/api/posts")
public ResponseEntity<Post> addPost(@Valid @RequestBody AddPostRequest request) { ... }
```
`@Valid`가 붙은 매개변수의 값이 조건에 맞지 않으면, Controller 메서드가 실행되기 전에 400 Bad Request 응답이 나간다.

> **검증(validation)이란?**
> 사용자가 보낸 값이 정해진 조건(필수 입력, 길이, 형식 등)에 맞는지 확인하는 것이다.

### actuator 예시
`/actuator/health`, `/actuator/metrics` 같은 엔드포인트로 애플리케이션 상태를 확인할 수 있다.

> **엔드포인트와 메트릭이란?**
> 엔드포인트는 요청을 받을 수 있는 서버의 URL 주소다. 예를 들어 `/actuator/health`로 요청하면 `{"status":"UP"}`처럼 애플리케이션이 정상 동작 중인지 응답한다.
> 메트릭은 메모리 사용량, 요청 수, 응답 시간처럼 애플리케이션 상태를 나타내는 수치 데이터다.
