---
title: Controller에서 쓰는 어노테이션
date: 2026-10-01
tags: [spring-boot, annotation, controller]
---

# Controller에서 쓰는 어노테이션

> 이 글에 나오는 HTTP, JSON, `@RestController` 같은 용어는 [Spring Framework](../01_spring/01_spring_framework.md)와 [@Component](./02_component.md)에서 설명했다.

## Controller 예시
```java
@RequiredArgsConstructor
@RestController
public class PostController {
    private final PostService postService;

    @PostMapping("/api/posts")
    public ResponseEntity<Post> addPost(@RequestBody AddPostRequest request) {
        Post savedPost = postService.save(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedPost);
    }

    @GetMapping("/api/posts/{id}")
    public ResponseEntity<PostResponse> findPost(@PathVariable long id) {
        Post post = postService.findById(id);
        return ResponseEntity.ok()
                .body(new PostResponse(post));
    }
    // 나머지 메서드 생략
}
```

## 클래스에 붙이는 어노테이션
| 어노테이션 | 의미 |
|---|---|
| `@RestController` | 메서드가 반환한 객체를 JSON으로 변환해서 HTTP 응답 본문에 담아 보내는 Controller임을 표시한다 |
| `@RequestMapping("/api/posts")` | 이 클래스의 메서드들이 `/api/posts`로 시작하는 URL 요청을 처리한다. 공통 URL 앞부분을 한 번만 적을 때 쓴다 |

## 메서드에 붙이는 어노테이션
요청의 HTTP 메서드에 따라 어노테이션이 나뉜다.

| 어노테이션 | 처리하는 요청 | 예시 메서드 |
|---|---|---|
| `@GetMapping` | `GET` (조회) | `findAllPosts()`, `findPost()` |
| `@PostMapping` | `POST` (생성) | `addPost()` |
| `@PutMapping` | `PUT` (수정) | `updatePost()` |
| `@DeleteMapping` | `DELETE` (삭제) | `deletePost()` |

HTTP 메서드는 [REST API 사용 규칙](../10_rest_api/03_rest_api_rules.md)에서 자세히 설명한다.

## 매개변수에 붙이는 어노테이션
| 어노테이션 | 값을 가져오는 곳 | 예시 |
|---|---|---|
| `@PathVariable` | URL 경로의 `{}` 부분 | `/api/posts/3` → `id = 3` |
| `@RequestBody` | 요청 본문의 JSON | `{"title":"제목","content":"내용"}` → `AddPostRequest` 객체 |
| `@RequestParam` | URL의 `?` 뒤 쿼리 문자열 | `/api/posts?page=2` → `page = 2` |

> **요청 본문이란?**
> HTTP 요청에서 URL과 별도로 담아 보내는 데이터다. 글 생성처럼 보낼 데이터가 많을 때 JSON 형식으로 본문에 담는다.

> **쿼리 문자열이란?**
> URL 뒤에 `?이름=값&이름=값` 형식으로 붙이는 값이다. 검색어나 페이지 번호처럼 조회 조건을 보낼 때 주로 쓴다.

## ResponseEntity
HTTP 응답 전체(상태 코드, 헤더, 본문)를 직접 정할 때 사용하는 클래스다.
```java
ResponseEntity.status(HttpStatus.CREATED).body(savedPost);  // 201 Created + 본문
ResponseEntity.ok().body(posts);                              // 200 OK + 본문
ResponseEntity.ok().build();                                     // 200 OK, 본문 없음
```

> **상태 코드란?**
> 서버가 요청을 어떻게 처리했는지 알려 주는 세 자리 숫자다. 200은 성공, 201은 생성 성공, 404는 대상을 찾을 수 없음, 500은 서버 내부 오류다.
> 전체 목록은 [REST API 사용 규칙](../10_rest_api/03_rest_api_rules.md)에 있다.

> **제네릭(`<Post>`)이란?**
> `ResponseEntity<Post>`처럼 `< >` 안에 타입을 적어서, 그 클래스가 다루는 데이터의 타입을 지정하는 Java 문법이다.
> `ResponseEntity<Post>`는 "본문에 `Post`를 담은 응답", `List<PostResponse>`는 "`PostResponse`를 여러 개 담은 목록"이라는 뜻이다.
