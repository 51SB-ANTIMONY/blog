---
title: Spring Boot란? Spring과의 차이
date: 2026-09-30
tags: [spring, spring-boot]
---

# Spring Boot

> 이 글에 나오는 Bean, 컨테이너, DI, 트랜잭션, DataSource, WAS 같은 용어는 [01_spring_framework.md](./01_spring_framework.md)에서 설명했다.

## 정의
Spring Boot는 Spring Framework를 기반으로, 설정을 자동화하고 실행 환경을 내장해서
바로 실행할 수 있는 애플리케이션을 빠르게 만들도록 도와주는 도구다.
Spring을 대체하는 것이 아니라, 내부에서는 그대로 Spring을 사용한다.

> **설정이란?**
> 애플리케이션이 동작하는 데 필요한 정보를 지정하는 것이다.
> 예를 들어 어떤 DB에 연결할지, 서버를 몇 번 포트로 열지, 어떤 객체를 Bean으로 등록할지를 정하는 것이 모두 설정이다.

## Auto-Configuration (자동 설정)
클래스패스에 어떤 라이브러리가 있는지, 어떤 설정값이 있는지를 확인해서 필요한 Bean을 자동으로 등록한다.
- `spring-boot-starter-data-jpa`와 MySQL 드라이버가 있으면 → DataSource, EntityManagerFactory, TransactionManager가 자동 등록된다.
- 개발자가 같은 타입의 Bean을 직접 정의하면 자동 설정보다 개발자 설정이 우선한다 (`@ConditionalOnMissingBean`).

> **클래스패스란?**
> Java가 프로그램을 실행할 때 클래스 파일과 라이브러리를 찾는 경로 목록이다.
> Gradle에 라이브러리를 추가하면 그 라이브러리가 클래스패스에 포함된다.
> Spring Boot는 "클래스패스에 이 클래스가 있는가?"를 확인해서 어떤 자동 설정을 적용할지 결정한다.

> **MySQL과 드라이버란?**
> MySQL은 널리 쓰이는 DB 프로그램이다.
> 드라이버(JDBC 드라이버)는 JDBC 인터페이스를 특정 DB에 맞게 구현한 라이브러리다. Java 코드가 MySQL과 실제로 통신하려면 MySQL용 드라이버가 필요하다.

> **@ConditionalOnMissingBean이란?**
> "컨테이너에 이 타입의 Bean이 아직 없을 때만 이 Bean을 등록하라"는 조건을 나타내는 어노테이션이다.
> Spring Boot의 자동 설정 코드에 붙어 있어서, 개발자가 직접 `DataSource` Bean을 등록하면 자동 설정의 `DataSource`는 등록되지 않는다.

## Starter 의존성
관련 라이브러리를 묶어두고, 서로 호환되는 버전까지 맞춰 둔 의존성 묶음이다.
```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'      // Spring MVC + 내장 Tomcat + Jackson
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa' // JPA + Hibernate + HikariCP
    implementation 'org.springframework.boot:spring-boot-starter-security'
    runtimeOnly 'com.mysql:mysql-connector-j'
}
```
각 라이브러리의 버전을 따로 적지 않아도 된다.

> **Gradle이란?**
> Java 프로젝트의 빌드 도구다. 프로젝트에 필요한 라이브러리를 인터넷에서 내려받아 추가하고, 코드를 빌드한다.
> 설정은 `build.gradle` 파일에 작성하며, 위 코드의 `dependencies { ... }` 블록에 사용할 라이브러리를 적는다.
> - `implementation`: 코드를 컴파일할 때와 실행할 때 모두 필요한 라이브러리
> - `runtimeOnly`: 실행할 때만 필요한 라이브러리 (MySQL 드라이버는 코드에서 직접 사용하지 않으므로 여기에 둔다)

> **빌드와 컴파일이란?**
> 컴파일은 사람이 작성한 `.java` 소스 코드를 JVM(Java 실행 환경)이 실행할 수 있는 `.class` 파일로 변환하는 것이다.
> 빌드는 컴파일을 포함해 테스트 실행, 라이브러리와 함께 묶기 등을 거쳐 실행 가능한 결과물을 만드는 전체 과정이다.

> **JSON과 Jackson이란?**
> JSON은 `{"title": "첫 글", "content": "안녕하세요"}`처럼 이름과 값을 쌍으로 적는 텍스트 데이터 형식이다. 서버와 브라우저가 데이터를 주고받을 때 주로 사용한다.
> Jackson은 Java 객체를 JSON으로, JSON을 Java 객체로 변환하는 라이브러리다.

## 내장 서버
Tomcat(또는 Jetty, Undertow)이 애플리케이션 안에 포함되어 있다. 빌드한 JAR 하나를 `java -jar`로 실행하면 서버가 뜬다.
```bash
./gradlew build
java -jar build/libs/blog-0.0.1-SNAPSHOT.jar
```

> **내장 서버란?**
> WAS를 따로 설치하지 않고, WAS 라이브러리를 애플리케이션 안에 포함시킨 것이다.
> 애플리케이션이 시작될 때 코드 안에서 Tomcat을 직접 실행한다. Jetty, Undertow는 Tomcat과 같은 역할을 하는 다른 WAS다.

> **JAR란?**
> Java Archive의 줄임말로, 클래스 파일과 리소스 파일을 하나로 묶은 압축 파일이다.
> Spring Boot는 내장 서버와 모든 라이브러리를 포함한 **실행 가능한 JAR**를 만들어서, `java -jar 파일명.jar` 명령 하나로 실행할 수 있다.

> **`./gradlew`란?**
> Gradle Wrapper라는 스크립트다. 프로젝트에 포함되어 있어서, 컴퓨터에 Gradle을 설치하지 않아도 프로젝트에 지정된 버전의 Gradle로 빌드할 수 있다.

## 외부 설정 파일
XML Bean 정의 대신 `application.yml`(또는 `.properties`)에 값만 적는다.
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
> `update`는 엔티티에 새로 추가된 필드를 테이블에 반영한다. 실제 서비스(운영) 환경에서는 데이터가 의도치 않게 바뀔 수 있어서 `validate`나 `none`을 사용한다.
> (DDL은 테이블 생성, 수정, 삭제처럼 DB 구조를 정의하는 SQL 명령이다.)

## Actuator
`/actuator/health`, `/actuator/metrics` 같은 엔드포인트로 애플리케이션 상태를 확인할 수 있다.

> **엔드포인트와 메트릭이란?**
> 엔드포인트는 요청을 받을 수 있는 서버의 URL 주소다. 예를 들어 `/actuator/health`로 요청하면 `{"status":"UP"}`처럼 애플리케이션이 정상 동작 중인지 응답한다.
> 메트릭은 메모리 사용량, 요청 수, 응답 시간처럼 애플리케이션 상태를 나타내는 수치 데이터다.

## 예시: 최소한의 블로그 API
```java
@SpringBootApplication  // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class BlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(BlogApplication.class, args);
    }
}
```

> **main 메서드와 SpringApplication.run이란?**
> `main` 메서드는 Java 프로그램을 실행하면 가장 먼저 호출되는 메서드다.
> `SpringApplication.run()`은 Spring 컨테이너를 만들고, 자동 설정을 적용하고, Bean을 등록한 뒤 내장 Tomcat을 시작한다.

> **@SpringBootApplication이란?**
> 세 어노테이션을 합친 것이다.
> - `@Configuration`: 이 클래스가 Bean 설정을 담은 클래스임을 표시한다.
> - `@EnableAutoConfiguration`: Spring Boot의 자동 설정을 켠다.
> - `@ComponentScan`: 이 클래스가 있는 패키지와 그 하위 패키지에서 `@Component`, `@Service`, `@Repository`, `@Controller` 등이 붙은 클래스를 찾아 Bean으로 등록한다.
>
> 패키지는 Java 클래스를 묶는 폴더 단위다. 예를 들어 `BlogApplication`이 `kr.antimony` 패키지에 있으면 `kr.antimony.post` 같은 하위 패키지의 클래스도 검색 대상이 된다.

```java
@Entity
public class Post {
    @Id @GeneratedValue
    private Long id;
    private String title;
    private String content;
    // getter, 생성자 생략
}
```

> **엔티티란?**
> DB 테이블과 연결(매핑)된 Java 클래스다. `@Entity`가 붙은 `Post` 클래스는 DB의 `post` 테이블에 대응하고, 필드 하나가 테이블의 컬럼 하나에 대응한다.
> - `@Id`: 이 필드가 **기본 키**임을 표시한다. 기본 키는 테이블에서 각 행을 구분하는 고유한 값이다.
> - `@GeneratedValue`: 기본 키 값을 개발자가 넣지 않고 DB가 자동으로 생성하게 한다.
> - getter: `getTitle()`처럼 `private` 필드의 값을 외부에서 읽을 수 있게 해주는 메서드다.

```java
public interface PostRepository extends JpaRepository<Post, Long> {}
// 구현 클래스 없이 save, findById, findAll 등을 바로 사용 가능 (Spring Data JPA)
```

> **JpaRepository<Post, Long>이란?**
> Spring Data JPA가 제공하는 인터페이스로, 저장(`save`), 조회(`findById`, `findAll`), 삭제(`delete`) 같은 메서드가 이미 선언되어 있다.
> `<Post, Long>`은 제네릭 문법으로, 이 Repository가 다루는 엔티티가 `Post`이고 그 기본 키 타입이 `Long`이라는 뜻이다.
> 개발자가 구현 클래스를 작성하지 않아도, Spring Data JPA가 실행 중에 구현 객체를 자동으로 만들어 Bean으로 등록한다.

```java
@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostRepository postRepository;

    public PostController(PostRepository postRepository) {  // DI
        this.postRepository = postRepository;
    }

    @GetMapping
    public List<Post> list() {
        return postRepository.findAll();
    }

    @PostMapping
    public Post create(@RequestBody Post post) {
        return postRepository.save(post);
    }
}
```
`main`을 실행하면 내장 Tomcat이 8080 포트로 뜨고, `GET /api/posts`로 게시글 목록을 JSON으로 받을 수 있다.
DataSource, Jackson, DispatcherServlet 등록 같은 설정은 자동 설정이 처리한다.

> **Controller에 쓰인 어노테이션**
> - `@RestController`: 메서드가 반환한 객체를 JSON으로 변환해서 HTTP 응답 본문에 담아 보내는 Controller임을 표시한다.
> - `@RequestMapping("/api/posts")`: 이 클래스의 메서드들이 `/api/posts`로 시작하는 URL 요청을 처리한다.
> - `@GetMapping`: HTTP `GET` 요청(조회)을 처리하는 메서드임을 표시한다. `GET /api/posts` 요청이 오면 `list()`가 실행된다.
> - `@PostMapping`: HTTP `POST` 요청(생성)을 처리하는 메서드임을 표시한다. `POST /api/posts` 요청이 오면 `create()`가 실행된다.
> - `@RequestBody`: 요청 본문에 담겨 온 JSON을 Java 객체(`Post`)로 변환해서 매개변수에 넣는다.

> **List<Post>란?**
> 여러 개의 `Post` 객체를 순서대로 담는 Java 컬렉션이다. JSON으로 변환되면 `[{"id":1, ...}, {"id":2, ...}]` 같은 배열이 된다.

## Spring vs Spring Boot
| 항목 | Spring Framework | Spring Boot |
|---|---|---|
| 역할 | IoC/DI, AOP, MVC 등 핵심 기능 제공 | Spring을 쉽게 쓰도록 설정과 실행 환경을 자동화 |
| 설정 | XML 또는 Java Config로 직접 작성 | 자동 설정 + `application.yml` |
| 의존성 관리 | 라이브러리와 버전을 개별 지정 | Starter로 묶음 관리, 버전은 Spring Boot가 관리 |
| 서버 | 외부 WAS에 WAR로 배포 | 내장 서버, 실행 가능한 JAR |
| 관계 | 기반 기술 | Spring 위에서 동작 |

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

## 정리
객체 관리(DI), 트랜잭션, 웹 요청 처리 같은 실제 기능은 Spring이 제공한다.
Spring Boot는 그 기능을 쓰는 데 필요한 설정, 의존성 관리, 서버 실행을 자동으로 처리한다.