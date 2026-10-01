---
title: 스프링 부트 프로젝트 디렉터리 구성
date: 2026-10-01
tags: [spring-boot, gradle]
---

# 스프링 부트 프로젝트 디렉터리 구성

> 이 글에 나오는 Gradle, 빌드, 패키지 같은 용어는 [스프링 부트 스타터](../04_spring_boot_starter/01_starter.md)와 [@SpringBootApplication](../06_spring_boot_code/01_spring_boot_application.md)에서 설명했다.

## 기본 구성
Spring Boot 프로젝트(Gradle 사용)는 보통 다음과 같이 구성된다.
```
demo/
├── build.gradle            ← 빌드 설정, 의존성 목록
├── settings.gradle         ← 프로젝트 이름 등 Gradle 전체 설정
├── gradlew, gradlew.bat    ← Gradle Wrapper 실행 스크립트 (리눅스·맥 / 윈도우)
├── gradle/wrapper/         ← Gradle Wrapper가 사용할 Gradle 버전 정보
└── src/
    ├── main/               ← 실제 애플리케이션 코드
    │   ├── java/
    │   │   └── com/example/demo/
    │   │       ├── DemoApplication.java
    │   │       ├── controller/
    │   │       ├── domain/
    │   │       ├── dto/
    │   │       ├── repository/
    │   │       └── service/
    │   └── resources/
    │       ├── application.yml
    │       ├── static/
    │       └── templates/
    └── test/               ← 테스트 코드
        └── java/
            └── com/example/demo/
                └── controller/
                    └── PostControllerTest.java
```

> **디렉터리란?**
> 파일을 담는 폴더를 말한다.

## main
애플리케이션을 실행하는 데 쓰이는 코드와 파일이 들어 있다. 빌드하면 이 안의 내용이 JAR에 포함된다.

### main/java
Java 소스 코드를 둔다. 폴더 구조가 곧 패키지 이름이다. (`main/java/com/example/demo/service/` → `com.example.demo.service` 패키지)

### main/resources
Java 코드가 아닌 파일(설정, SQL, HTML, 이미지 등)을 둔다.
| 파일/폴더 | 용도 |
|---|---|
| `application.yml` (또는 `application.properties`) | 애플리케이션 설정 ([차이점 2](../02_spring_boot/03_difference_embedded_was.md)) |
| `static/` | 가공 없이 그대로 보내는 파일(HTML, CSS, JavaScript, 이미지). 예를 들어 `static/index.html`은 `http://localhost:8080/`로 접속하면 보인다 |
| `templates/` | 서버가 데이터를 채워 HTML을 만드는 템플릿 파일 (Thymeleaf 등) |
| `data.sql`, `schema.sql`, `import.sql` | 애플리케이션이 시작될 때 실행할 SQL |

## test
테스트 코드를 둔다. 빌드할 때 테스트는 실행되지만, 결과물인 JAR에는 포함되지 않는다.
보통 `main/java`와 같은 패키지 구조를 따라 만든다. (`controller/PostController` → `controller/PostControllerTest`)
→ [테스트 코드](../08_test/01_test_code_and_given_when_then.md)

## build.gradle
프로젝트를 빌드하는 방법과 사용할 라이브러리를 적는 파일이다.
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies { ... }
```
| 블록 | 의미 |
|---|---|
| `plugins` | Gradle에 추가할 기능. Spring Boot 플러그인은 실행 가능한 JAR를 만들고, dependency-management는 라이브러리 버전을 관리한다 |
| `group`, `version` | 프로젝트의 그룹 이름과 버전 |
| `java.toolchain` | 프로젝트를 빌드할 Java 버전 |
| `repositories` | 라이브러리를 내려받을 저장소 (Maven Central) |
| `dependencies` | 사용할 라이브러리 목록 ([스프링 부트 스타터](../04_spring_boot_starter/01_starter.md)) |

> **플러그인이란?**
> 프로그램에 기능을 추가하는 확장 모듈이다. Gradle 플러그인을 추가하면 새로운 빌드 작업(예: `bootRun`, `bootJar`)을 쓸 수 있다.

> **Maven Central이란?**
> Java 라이브러리가 공개되어 있는 가장 큰 온라인 저장소다. Gradle은 `dependencies`에 적힌 라이브러리를 여기서 내려받는다.

## settings.gradle
Gradle 프로젝트 전체에 대한 설정을 적는다. 모듈이 하나인 프로젝트에는 보통 프로젝트 이름만 들어 있다.
```gradle
rootProject.name = 'demo'
```
빌드 결과물 JAR의 이름(`demo-0.0.1-SNAPSHOT.jar`)은 이 이름과 `version`으로 정해진다.
여러 모듈로 이루어진 프로젝트에서는 여기에 하위 모듈 목록(`include 'module-a'`)도 적는다.
