---
title: 스프링 부트 계층 구조
date: 2026-10-01
tags: [spring-boot, architecture]
---

# 스프링 부트 계층 구조

> 이 글에 나오는 Bean, `@Service`, `@RestController` 같은 용어는 [빈과 스프링 컨테이너](../03_spring_concepts/03_bean_and_container.md)와 [@Component](../06_spring_boot_code/02_component.md)에서 설명했다.

## 계층이란?
애플리케이션의 코드를 역할에 따라 나눈 단위다.
각 계층은 자기 역할만 처리하고, 바로 아래 계층에만 일을 요청한다.
이렇게 나누면 한 계층의 코드를 고쳐도 다른 계층에 주는 영향이 줄어들고, 계층별로 따로 테스트할 수 있다.

```
클라이언트 (브라우저, 앱)
   ↓ HTTP 요청
┌──────────────────────────────┐
│ 프레젠테이션 계층 - Controller │
├──────────────────────────────┤
│ 비즈니스 계층 - Service        │
├──────────────────────────────┤
│ 퍼시스턴스 계층 - Repository   │
└──────────────────────────────┘
   ↓ SQL
데이터베이스
```

> **클라이언트란?**
> 서버에 요청을 보내는 쪽이다. 브라우저, 모바일 앱, 다른 서버 모두 클라이언트가 될 수 있다.

## 프레젠테이션 계층 - Controller
HTTP 요청을 받고, 그 요청을 비즈니스 계층에 전달하고, 결과를 HTTP 응답으로 돌려준다.
- 요청 URL과 HTTP 메서드에 맞는 메서드를 실행한다.
- 요청 데이터(JSON, URL 경로 값)를 Java 객체로 받는다.
- 비즈니스 규칙은 직접 처리하지 않고 Service에 넘긴다.

```java
@RestController
public class PostController {
    private final PostService postService;

    @GetMapping("/api/posts/{id}")
    public ResponseEntity<PostResponse> findPost(@PathVariable long id) {
        Post post = postService.findById(id);           // Service에 요청
        return ResponseEntity.ok().body(new PostResponse(post));  // 응답으로 변환
    }
}
```

## 비즈니스 계층 - Service
비즈니스 로직을 처리한다. 트랜잭션도 주로 이 계층에서 건다.
- "없는 글이면 예외를 던진다", "작성자만 수정할 수 있다" 같은 규칙을 처리한다.
- 데이터가 필요하면 Repository에 요청한다.

```java
@Service
public class PostService {
    private final PostRepository postRepository;

    public Post findById(long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("not found: " + id));  // 비즈니스 규칙
    }
}
```

> **비즈니스 로직이란?**
> 애플리케이션이 해결하려는 문제에 해당하는 규칙과 처리다. → [AOP](../03_spring_concepts/04_aop.md)

## 퍼시스턴스 계층 - Repository
DB에 데이터를 저장하고, 조회하고, 수정하고, 삭제한다.
```java
public interface PostRepository extends JpaRepository<Post, Long> {
}
```

> **퍼시스턴스(persistence)란?**
> "영속성", 즉 데이터가 프로그램이 끝난 뒤에도 사라지지 않고 남아 있는 성질이다.
> 퍼시스턴스 계층은 데이터를 DB처럼 영구히 저장되는 곳에 저장하는 일을 맡는다.

> **DAO란?**
> Data Access Object의 줄임말로, DB에 접근하는 코드를 모아 둔 객체다.
> Repository와 역할이 같아서 퍼시스턴스 계층에 Repository 대신 DAO를 둘 수도 있다.
> 보통 SQL을 직접 작성하는 클래스를 DAO, Spring Data JPA처럼 엔티티 단위로 저장하고 조회하는 인터페이스를 Repository라고 부른다.

## 데이터베이스
실제로 데이터가 저장되는 곳이다.
→ [데이터베이스와 ORM](../09_database/01_database_and_orm.md)

## 계층 사이에서 데이터를 옮기는 객체 - DTO
계층 사이에서 데이터를 주고받을 때는 엔티티 대신 DTO를 쓰는 경우가 많다.

> **DTO란?**
> Data Transfer Object의 줄임말로, 계층 사이에서 데이터를 옮기는 데만 쓰는 클래스다. 비즈니스 로직은 넣지 않는다.

| DTO 예시 | 용도 |
|---|---|
| `AddPostRequest` | 글 생성 요청 JSON을 받음 |
| `UpdatePostRequest` | 글 수정 요청 JSON을 받음 |
| `PostResponse` | 조회 결과를 응답 JSON으로 보냄 |

엔티티를 그대로 응답하면 DB 테이블 구조가 밖에 드러나고, 엔티티를 고칠 때 API 응답 형식까지 바뀐다. DTO를 두면 이 둘을 따로 관리할 수 있다.

## 계층별 패키지 구성 예시
| 계층 | 패키지 | 클래스 |
|---|---|---|
| 프레젠테이션 | `com.example.demo.controller` | `PostController` |
| 비즈니스 | `com.example.demo.service` | `PostService` |
| 퍼시스턴스 | `com.example.demo.repository` | `PostRepository` |
| (엔티티) | `com.example.demo.domain` | `Post` |
| (DTO) | `com.example.demo.dto` | `AddPostRequest`, `UpdatePostRequest`, `PostResponse` |
