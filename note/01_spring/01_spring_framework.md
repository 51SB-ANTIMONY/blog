---
title: Spring Framework의 핵심 개념 (IoC, DI, AOP, PSA)
date: 2026-09-30
tags: [spring, ioc, di, aop]
---

# Spring Framework

## 정의
Spring은 Java 애플리케이션을 만들 때 쓰는 오픈소스 프레임워크다.
객체를 누가 만들고 서로 어떻게 연결할지를 개발자 코드가 아니라 **Spring 컨테이너(ApplicationContext)** 가 관리한다.
이 구조 덕분에 객체 사이의 결합도가 낮아지고, 코드를 테스트하고 교체하기가 쉬워진다.

> **프레임워크란?**
> 애플리케이션의 기본 구조와 실행 흐름을 미리 만들어 둔 코드 모음이다.
> 개발자는 프레임워크가 정한 규칙에 맞춰 필요한 부분의 코드만 작성하고, 그 코드를 **언제 호출할지는 프레임워크가 결정**한다.
>
> 비슷한 말로 **라이브러리**가 있다. 라이브러리는 특정 기능을 하는 코드를 다른 프로젝트에서 가져다 쓸 수 있게 묶어 둔 것으로, **개발자 코드가 필요할 때 직접 호출**한다.
> 둘의 차이는 코드의 실행 흐름을 누가 결정하느냐에 있다.

### Spring의 기본 구조
Spring 애플리케이션은 **Spring이 제공하는 부분**과 **개발자가 작성하는 부분**으로 나뉜다.

| 구분 | 구성 요소 | 하는 일 |
|---|---|---|
| Spring이 제공 | 컨테이너 (`ApplicationContext`) | 개발자가 작성한 클래스의 객체를 만들고, 서로 연결하고, 보관한다 |
| Spring이 제공 | `DispatcherServlet` | 들어온 HTTP 요청을 받아 알맞은 Controller 메서드를 호출한다 |
| Spring이 제공 | 트랜잭션 처리, JSON 변환 등 | 여러 클래스에 공통으로 필요한 기능을 처리한다 |
| 개발자가 작성 | Controller | 요청 URL별로 무엇을 할지 정한다 |
| 개발자가 작성 | Service | 애플리케이션의 규칙(예: 제목이 비면 저장하지 않음)을 처리한다 |
| 개발자가 작성 | Repository | DB에 데이터를 저장하고 조회한다 |

개발자는 Controller, Service, Repository 클래스를 작성하고 `@Controller`, `@Service`, `@Repository` 같은 표시(어노테이션)를 붙인다.
이 클래스들의 객체를 언제 만들고, 언제 어떤 메서드를 호출할지는 Spring이 결정한다.
(컨테이너, DispatcherServlet, 어노테이션 등 각 용어는 아래에서 하나씩 설명한다.)

### Spring의 실행 흐름
실행 흐름은 **애플리케이션이 시작될 때**와 **요청이 들어올 때** 두 단계로 나뉜다.

**1) 애플리케이션이 시작될 때 (한 번 실행)**
```
① 애플리케이션 실행 (Spring Boot에서는 main 메서드의 SpringApplication.run())
② Spring 컨테이너 생성
③ 컴포넌트 스캔: @Controller, @Service, @Repository 등이 붙은 클래스를 찾음
④ 찾은 클래스의 객체(Bean)를 생성
⑤ 의존성 주입: 각 객체가 필요로 하는 다른 객체를 생성자 등에 넣어 연결
   (예: PostService 객체를 만들 때 PostRepository 객체를 넣어줌)
⑥ 서버(Tomcat)를 시작하고 요청을 기다림
```

**2) HTTP 요청이 들어올 때 (요청마다 반복)**
```
브라우저: GET /api/posts/1 요청
   ↓
Tomcat(서버)이 요청을 받음
   ↓
DispatcherServlet이 URL(/api/posts/1)에 맞는 Controller 메서드를 찾아 호출
   ↓
PostController.find(1)       ← 개발자가 작성
   ↓
PostService.find(1)          ← 개발자가 작성 (필요하면 Spring이 트랜잭션 시작)
   ↓
PostRepository.findById(1)   ← 개발자가 작성 (또는 Spring Data JPA가 자동 생성)
   ↓
DB에서 id가 1인 게시글 조회
   ↓
조회 결과가 Repository → Service → Controller 순서로 반환됨
   ↓
Spring이 반환된 Post 객체를 JSON으로 변환
   ↓
브라우저가 {"id":1, "title":"첫 글", ...} 응답을 받음
```

이 흐름에서 개발자가 직접 호출하는 코드는 Controller → Service → Repository 사이의 메서드 호출뿐이다.
객체 생성, 연결, 요청 받기, Controller 호출, JSON 변환은 모두 Spring이 처리한다.
이렇게 실행 흐름을 Spring이 결정한다는 점 때문에 Spring을 라이브러리가 아니라 프레임워크라고 한다.

> **컨테이너란?**
> Spring에서 컨테이너는 애플리케이션이 실행될 때 객체를 생성하고, 필요한 곳에 연결하고, 애플리케이션이 종료될 때 정리하는 Spring의 구성 요소다.
> Java 코드에서는 `ApplicationContext` 인터페이스와 그 구현 클래스가 이 역할을 한다.
> 애플리케이션이 시작되면 Spring은 `@Service`, `@Repository` 같은 표시가 붙은 클래스를 찾아 객체를 만들고 컨테이너 안에 보관한다.

## IoC (Inversion of Control, 제어의 역전)
객체를 생성하고, 생명주기를 관리하고, 객체끼리 연결하는 권한이 개발자 코드에서 프레임워크로 넘어간다는 개념이다.
Spring 컨테이너가 관리하는 객체를 **Bean**이라고 한다.

> **Bean이란?**
> Spring 컨테이너가 생성하고 관리하는 객체다. 코드 자체는 일반 Java 객체와 같고, 개발자가 `new`로 만들었는지 컨테이너가 만들었는지가 다르다.
> 다음 중 하나에 해당하면 Bean으로 등록된다.
> - `@Component`, `@Service`, `@Repository`, `@Controller`가 붙은 클래스
> - `@Configuration` 클래스 안에서 `@Bean`이 붙은 메서드가 반환한 객체

## DI (Dependency Injection, 의존성 주입)
IoC를 구현하는 방식이다. 객체가 필요로 하는 다른 객체(의존성)를 스스로 `new`로 만들지 않고, 외부(컨테이너)에서 받는다.

> **의존성이란?**
> 클래스 A가 동작하려면 클래스 B를 사용해야 할 때, "A가 B에 의존한다"고 하고 B를 A의 **의존성**이라고 한다.
> 예를 들어 `PostService`가 게시글을 조회하려면 `PostRepository`가 필요하므로, `PostRepository`는 `PostService`의 의존성이다.

> **Controller, Service, Repository란?**
> 웹 애플리케이션은 보통 역할에 따라 클래스를 세 계층으로 나눈다.
> - **Controller**: 사용자의 요청을 받고 응답을 돌려준다.
> - **Service**: 비즈니스 로직(아래 AOP 부분에서 설명)을 처리한다.
> - **Repository**: DB에 데이터를 저장하거나 DB에서 조회한다.
>
> 요청은 Controller → Service → Repository 순서로 전달된다.

> **인터페이스와 구현체란?**
> 인터페이스는 메서드의 이름, 매개변수, 반환 타입만 선언하고 실제 동작 코드는 없는 Java 타입이다.
> 구현체는 `implements`로 인터페이스를 구현해 메서드의 실제 동작을 작성한 클래스다. 하나의 인터페이스에 여러 구현체가 있을 수 있다.
> ```java
> public interface PostRepository {
>     Post findById(Long id);  // 선언만 있음
> }
>
> public class JpaPostRepository implements PostRepository {
>     public Post findById(Long id) {
>         // 실제로 DB에서 조회하는 코드
>     }
> }
> ```

### DI를 쓰지 않은 코드
```java
public class PostService {
    // 구현체를 직접 생성하므로 PostService가 JpaPostRepository에 강하게 묶임
    private final PostRepository repository = new JpaPostRepository();

    public Post find(Long id) {
        return repository.findById(id);
    }
}
```
저장소 구현을 바꾸거나 테스트용 가짜 객체로 교체하려면 `PostService` 코드를 직접 고쳐야 한다.

> **테스트와 가짜 객체란?**
> 여기서 테스트는 작성한 코드가 의도대로 동작하는지 확인하는 코드를 따로 작성해 실행하는 것을 말한다.
> 가짜 객체(Fake)는 테스트에서 실제 구현체 대신 쓰려고 만든 구현체다. 예를 들어 `FakePostRepository`는 데이터를 DB가 아니라 Java의 `Map`에 저장한다.
> 가짜 객체를 쓰면 DB 없이도 `PostService`의 로직만 빠르게 확인할 수 있다.

### DI를 사용한 코드
```java
@Repository
public class JpaPostRepository implements PostRepository { ... }

@Service
public class PostService {
    private final PostRepository repository;

    // 생성자 주입: Spring이 PostRepository 타입의 Bean을 찾아 넣어줌
    public PostService(PostRepository repository) {
        this.repository = repository;
    }
}
```
`PostService`는 인터페이스에만 의존한다. 어떤 구현체가 들어올지는 컨테이너가 결정한다.
테스트할 때는 `new PostService(new FakePostRepository())`처럼 원하는 객체를 직접 넣으면 된다.

> **어노테이션이란?**
> `@Service`처럼 `@`로 시작하며 클래스, 메서드, 필드 등에 붙여 추가 정보를 표시하는 Java 문법이다.
> 어노테이션은 그 자체로 동작하지 않는다. Spring 같은 프레임워크가 어노테이션을 읽고 그에 맞는 처리를 한다.
> 예를 들어 `@Service`를 읽은 Spring은 그 클래스의 객체를 Bean으로 등록한다.

> **생성자와 생성자 주입이란?**
> 생성자는 `new`로 객체를 만들 때 실행되는 메서드다. 이름이 클래스 이름과 같고 반환 타입이 없다.
> 생성자 주입은 의존성을 생성자의 매개변수로 받는 방식이다.
> Spring은 `PostService` 객체를 만들 때 생성자 매개변수의 타입(`PostRepository`)을 확인하고, 컨테이너에서 그 타입의 Bean(`JpaPostRepository` 객체)을 찾아 전달한다.
> 생성자가 하나뿐이면 `@Autowired`를 생략해도 Spring이 자동으로 주입한다.

## AOP (Aspect-Oriented Programming, 관점 지향 프로그래밍)
트랜잭션, 로깅, 권한 검사처럼 여러 클래스에 반복해서 들어가는 공통 기능(**공통 관심사**)을 비즈니스 로직과 분리하는 기법이다.
Spring은 프록시 객체를 만들어 메서드 호출 앞뒤에 공통 로직을 끼워 넣는다.

> **트랜잭션이란?**
> DB에서 여러 작업을 하나의 단위로 묶어, 모두 성공하거나 모두 실패하도록 만드는 것이다.
> 예를 들어 게시글 저장과 태그 저장을 하나의 트랜잭션으로 묶으면, 태그 저장이 실패했을 때 먼저 실행된 게시글 저장도 취소된다.
> 이렇게 하면 게시글은 저장됐는데 태그는 없는 어중간한 상태가 DB에 남지 않는다.

> **커밋과 롤백이란?**
> - **커밋(commit)**: 트랜잭션 안에서 실행한 변경 내용을 DB에 확정해서 반영하는 것
> - **롤백(rollback)**: 트랜잭션 안에서 실행한 변경 내용을 모두 취소하고, 트랜잭션 시작 전 상태로 되돌리는 것

> **로깅이란?**
> 프로그램이 실행되는 동안 일어난 일(요청이 들어온 시각, 발생한 에러 내용 등)을 기록으로 남기는 것이다.

> **비즈니스 로직이란?**
> 애플리케이션이 해결하려는 문제에 해당하는 규칙과 처리다.
> 예: "제목이 비어 있으면 게시글을 저장하지 않는다", "작성자만 자신의 게시글을 수정할 수 있다"
> 트랜잭션 처리나 로깅은 비즈니스 로직이 아니라, 여러 비즈니스 로직에 공통으로 필요한 부가 기능이다.

```java
@Service
public class PostService {
    @Transactional  // 메서드 시작 시 트랜잭션 시작, 정상 종료 시 커밋, 예외 발생 시 롤백
    public void write(PostRequest req) {
        postRepository.save(req.toEntity());
        tagRepository.saveAll(req.getTags());
    }
}
```
개발자가 `begin()`, `commit()`, `rollback()`을 직접 호출하지 않아도 된다.
(기본 설정에서는 `RuntimeException` 계열 예외가 발생했을 때만 롤백한다.)

> **프록시란?**
> 원래 객체와 같은 타입이면서, 원래 객체의 메서드를 대신 받아서 호출해 주는 객체다.
> Spring은 `@Transactional`이 붙은 클래스에 대해 프록시 객체를 만들고, 원래 객체 대신 이 프록시를 Bean으로 등록한다.
> 그래서 다른 클래스가 `postService.write()`를 호출하면 실제로는 프록시의 `write()`가 먼저 실행된다.
> 프록시가 하는 일을 단순하게 코드로 나타내면 다음과 같다. (Spring이 실행 중에 자동으로 만드는 것이므로 개발자가 작성하지 않는다.)
> ```java
> public class PostServiceProxy extends PostService {
>     private final PostService target;  // 원래 PostService 객체
>
>     public void write(PostRequest req) {
>         // 1. 트랜잭션 시작
>         try {
>             target.write(req);  // 2. 원래 객체의 메서드 호출
>             // 3. 커밋
>         } catch (RuntimeException e) {
>             // 3. 롤백
>             throw e;
>         }
>     }
> }
> ```

## PSA (Portable Service Abstraction)
특정 기술에 종속되지 않도록 추상화된 인터페이스를 제공한다.
`@Transactional`은 JDBC, JPA, Hibernate 중 무엇을 쓰든 같은 방식으로 사용할 수 있다.
내부에서는 `PlatformTransactionManager` 인터페이스의 구현체만 달라진다.

> **추상화란?**
> 여러 구현의 공통된 동작만 뽑아서 하나의 인터페이스로 정의하고, 사용하는 쪽은 그 인터페이스만 사용하게 하는 것이다.
> 이렇게 하면 구현이 바뀌어도 인터페이스를 사용하는 코드는 수정할 필요가 없다.
> 예를 들어 `PlatformTransactionManager`는 트랜잭션 시작, 커밋, 롤백 메서드를 정의한 인터페이스이고,
> JDBC용 구현체는 `DataSourceTransactionManager`, JPA용 구현체는 `JpaTransactionManager`다.

> **JDBC, JPA, Hibernate란?**
> - **JDBC**: Java에서 DB에 연결하고 SQL을 실행하기 위한 Java 표준 API다. 개발자가 SQL을 직접 작성한다.
> - **JPA**: Java 객체와 DB 테이블을 연결(매핑)해서, SQL을 직접 작성하지 않고 객체를 저장하고 조회할 수 있게 해주는 Java 표준 명세다. 명세는 "이런 인터페이스와 동작을 제공해야 한다"는 규칙이며, 실제 동작 코드는 들어 있지 않다.
> - **Hibernate**: JPA 명세를 실제로 구현한 라이브러리다. Spring Boot에서 JPA를 사용하면 기본으로 Hibernate가 사용된다.

> **API란?**
> 어떤 프로그램이나 라이브러리가 다른 코드에서 자기 기능을 사용할 수 있도록 공개한 메서드, 클래스, 규칙의 모음이다.
> 웹에서는 특정 URL로 요청을 보내면 데이터를 돌려주는 서버의 기능을 가리킬 때도 API라고 한다.

## 주요 모듈
| 모듈 | 역할 |
|---|---|
| Spring Core | IoC 컨테이너, DI |
| Spring MVC | 웹 요청 처리 (Controller, DispatcherServlet) |
| Spring Data JPA | Repository 인터페이스만으로 DB 접근 |
| Spring Security | 인증, 인가 |
| Spring AOP | 공통 관심사 분리 |

> **모듈이란?**
> Spring은 기능별로 여러 라이브러리로 나뉘어 있고, 그 각각을 모듈이라고 한다. 프로젝트에는 필요한 모듈만 추가해서 사용한다.

> **HTTP와 웹 요청이란?**
> HTTP는 브라우저(또는 앱)와 서버가 데이터를 주고받을 때 사용하는 통신 규칙이다.
> 브라우저가 서버에 보내는 것을 **요청(request)**, 서버가 돌려주는 것을 **응답(response)** 이라고 한다.
> 요청에는 URL(`/api/posts`)과 HTTP 메서드(조회는 `GET`, 생성은 `POST` 등), 필요하면 보낼 데이터가 들어 있다.

> **MVC란?**
> Model-View-Controller의 줄임말로, 웹 애플리케이션 코드를 세 역할로 나누는 구조다.
> Model은 화면에 전달할 데이터, View는 사용자에게 보여줄 화면, Controller는 요청을 받아 Model을 준비하고 View를 선택하는 역할이다.

> **서블릿과 DispatcherServlet이란?**
> 서블릿은 Java에서 HTTP 요청을 받아 처리하고 응답을 만드는 클래스의 표준 규격이다.
> DispatcherServlet은 Spring MVC가 제공하는 서블릿으로, 모든 HTTP 요청을 가장 먼저 받은 뒤 요청 URL에 맞는 Controller 메서드를 찾아 호출한다.

> **인증과 인가란?**
> - **인증(Authentication)**: 사용자가 누구인지 확인하는 것 (예: 아이디와 비밀번호로 로그인)
> - **인가(Authorization)**: 확인된 사용자가 특정 작업을 할 권한이 있는지 확인하는 것 (예: 관리자만 다른 사람의 게시글을 삭제할 수 있음)

## Spring만 사용할 때의 문제
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
> - **EntityManagerFactory**: JPA에서 객체를 저장하고 조회하는 `EntityManager`를 만들어 주는 객체

> **커넥션 풀과 HikariCP란?**
> DB 연결(커넥션)을 새로 만드는 데는 시간이 걸린다. 그래서 커넥션을 미리 여러 개 만들어 두고, 요청이 올 때마다 빌려 쓰고 반납해서 재사용하는 방식을 커넥션 풀이라고 한다.
> HikariCP는 커넥션 풀 라이브러리이고, Spring Boot가 기본으로 사용한다.
> 위 XML의 `jdbc:mysql://localhost:3306/blog`는 "내 컴퓨터(localhost)의 3306번 포트에서 실행 중인 MySQL의 blog DB"에 연결하라는 뜻이다.

> **버전 호환성이란?**
> 라이브러리들은 서로의 클래스와 메서드를 사용하기 때문에, 특정 버전 조합에서만 정상적으로 동작한다.
> 버전이 맞지 않으면 실행 중에 필요한 클래스나 메서드를 찾지 못해 에러가 난다(예: `NoSuchMethodError`).

> **WAS, Tomcat, WAR, 배포란?**
> - **WAS(Web Application Server)**: HTTP 요청을 받아 Java 웹 애플리케이션 코드(서블릿)를 실행하고 응답을 돌려주는 서버 프로그램
> - **Tomcat**: 가장 널리 쓰이는 Java WAS(정확히는 서블릿 컨테이너)
> - **WAR(Web Application Archive)**: Java 웹 애플리케이션을 WAS에 올리기 위해 클래스 파일과 설정 파일을 하나로 묶은 파일
> - **배포**: 작성한 애플리케이션을 서버에 올려서 사용자가 접속할 수 있는 상태로 만드는 것

이 설정 부담을 줄이기 위해 나온 것이 Spring Boot다. → [02_spring_boot.md](./02_spring_boot.md)