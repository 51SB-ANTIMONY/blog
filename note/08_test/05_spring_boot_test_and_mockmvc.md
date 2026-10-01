---
title: "@SpringBootTest와 MockMvc"
date: 2026-10-01
tags: [test, spring-boot, mockmvc]
---

# @SpringBootTest와 MockMvc

> 이 글에 나오는 Bean, 컨테이너, JSON, 상태 코드, JsonPath 같은 용어는 [빈과 스프링 컨테이너](../03_spring_concepts/03_bean_and_container.md), [Controller 어노테이션](../06_spring_boot_code/03_controller_annotations.md), [스프링 부트 스타터 테스트](./02_spring_boot_starter_test.md)에서 설명했다.

## @SpringBootTest
테스트를 실행할 때 **애플리케이션 전체의 Spring 컨테이너**를 띄우는 어노테이션이다.
`@SpringBootApplication`이 붙은 클래스를 찾아서, 실제 애플리케이션과 같은 방식으로 Bean을 모두 등록한다.
그래서 테스트 클래스에서 `@Autowired`로 Controller, Service, Repository 등 원하는 Bean을 주입받을 수 있다.

```java
@SpringBootTest
class PostControllerTest {
    @Autowired
    PostRepository postRepository;  // 컨테이너에 등록된 실제 Bean을 주입받음
}
```

> **테스트 클래스에서 `@Autowired`를 쓰는 이유**
> JUnit이 테스트 클래스 객체를 만들기 때문에, 생성자 주입 대신 필드에 `@Autowired`를 붙여 주입받는 방식을 주로 쓴다. ([DI](../03_spring_concepts/02_di.md))

전체 컨테이너를 띄우므로 실제 동작과 가장 비슷하게 테스트할 수 있지만, Bean을 모두 만들기 때문에 시작하는 데 시간이 걸린다.

| 어노테이션 | 띄우는 범위 |
|---|---|
| `@SpringBootTest` | 애플리케이션 전체 |
| `@WebMvcTest` | Controller 등 웹 계층만 (Service, Repository는 등록하지 않음) |
| `@DataJpaTest` | JPA 관련 Bean(Repository 등)만 |

## MockMvc
서버를 실제로 띄우지 않고, HTTP 요청을 흉내 내서 Controller를 테스트하는 도구다.
DispatcherServlet부터 Controller까지 실제와 같은 과정을 거치지만, 네트워크를 쓰지 않아서 빠르다.

> **@AutoConfigureMockMvc란?**
> `MockMvc` 객체를 자동으로 만들어서 Bean으로 등록해 주는 어노테이션이다. `@SpringBootTest`와 함께 쓰면 `@Autowired`로 `MockMvc`를 주입받을 수 있다.

### 요청 보내기: perform
```java
ResultActions result = mockMvc.perform(post("/api/posts")
        .contentType(MediaType.APPLICATION_JSON_VALUE)  // 본문이 JSON이라고 알림
        .content(requestBody));                         // 요청 본문
```
| 메서드 | 의미 |
|---|---|
| `get(url)`, `post(url)`, `put(url)`, `delete(url)` | HTTP 메서드와 URL을 지정한 요청을 만든다. `put("/api/posts/{id}", 1)`처럼 `{}` 자리에 값을 넣을 수 있다 |
| `contentType(...)` | 요청 본문의 형식 |
| `accept(...)` | 응답으로 받고 싶은 형식 |
| `content(...)` | 요청 본문 |

### 응답 검증하기: andExpect
```java
result
        .andExpect(status().isOk())                         // 상태 코드 200
        .andExpect(jsonPath("$[0].title").value("title"));  // 응답 JSON 배열의 첫 번째 요소의 title
```
| 메서드 | 검증 내용 |
|---|---|
| `status().isOk()` | 200 OK |
| `status().isCreated()` | 201 Created |
| `status().isNotFound()` | 404 Not Found |
| `jsonPath("$.title").value("제목")` | 응답 JSON의 `title`이 `"제목"`이다 |

## ObjectMapper
Java 객체를 JSON 문자열로 바꾸거나(직렬화), JSON 문자열을 Java 객체로 바꾸는(역직렬화) Jackson의 클래스다.
테스트에서는 요청 본문 JSON을 만들 때 쓴다.
```java
AddPostRequest userRequest = new AddPostRequest("title", "content");
String requestBody = objectMapper.writeValueAsString(userRequest);
// {"title":"title","content":"content"}
```

> **직렬화와 역직렬화란?**
> - **직렬화**: Java 객체를 JSON 같은 문자열이나 바이트로 바꾸는 것
> - **역직렬화**: 문자열이나 바이트를 다시 Java 객체로 바꾸는 것

> **Spring Boot 4의 ObjectMapper**
> Spring Boot 4는 Jackson 3를 사용한다. Jackson 3의 패키지 이름은 `tools.jackson`이라서, `tools.jackson.databind.ObjectMapper`를 import한다. (Jackson 2는 `com.fasterxml.jackson.databind.ObjectMapper`)

## 테스트 전체 예시
```java
@SpringBootTest          // 전체 컨테이너를 띄움
@AutoConfigureMockMvc    // MockMvc를 Bean으로 등록
class PostControllerTest {
    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired PostRepository postRepository;

    @BeforeEach
    public void cleanUp() {
        postRepository.deleteAll();
    }

    @DisplayName("addPost: 글 추가에 성공한다.")
    @Test
    public void addPost() throws Exception {
        // given
        final String url = "/api/posts";
        final AddPostRequest userRequest = new AddPostRequest("title", "content");
        final String requestBody = objectMapper.writeValueAsString(userRequest);

        // when
        ResultActions result = mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(requestBody));

        // then
        result.andExpect(status().isCreated());
        List<Post> posts = postRepository.findAll();
        assertThat(posts.size()).isEqualTo(1);
        assertThat(posts.get(0).getTitle()).isEqualTo("title");
    }
}
```
