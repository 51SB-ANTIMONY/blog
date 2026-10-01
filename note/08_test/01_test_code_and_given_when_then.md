---
title: 테스트 코드와 given-when-then 패턴
date: 2026-10-01
tags: [test]
---

# 테스트 코드와 given-when-then 패턴

## 테스트 코드란?
작성한 코드가 의도대로 동작하는지 확인하는 코드다.
애플리케이션 코드와 따로 `src/test/java`에 작성하고, 실행하면 성공 또는 실패 결과가 나온다.

테스트 코드를 작성하면 다음과 같은 장점이 있다.
- 서버를 띄우고 브라우저로 직접 요청을 보내지 않아도, 명령 한 번으로 기능을 확인할 수 있다.
- 코드를 고친 뒤 테스트를 다시 실행하면, 기존 기능이 망가졌는지 바로 알 수 있다.
- 테스트 코드를 읽으면 그 기능이 어떻게 동작해야 하는지 알 수 있다.

```bash
./gradlew test   # 모든 테스트 실행
```

## given-when-then 패턴
테스트 코드 하나를 세 부분으로 나눠서 작성하는 방식이다.

| 단계 | 의미 | 하는 일 |
|---|---|---|
| given | 준비 | 테스트에 필요한 데이터와 상태를 만든다 |
| when | 실행 | 테스트하려는 코드를 실행한다 |
| then | 검증 | 실행 결과가 기대한 값과 같은지 확인한다 |

나누는 이유는, 어디까지가 준비이고 무엇을 실행해서 무엇을 확인하는지 읽는 사람이 바로 알 수 있게 하기 위해서다.

## 예시 1: 간단한 계산
```java
@Test
void addTwoNumbers() {
    // given
    int a = 1;
    int b = 2;

    // when
    int sum = a + b;

    // then
    assertThat(sum).isEqualTo(3);
}
```

## 예시 2: 글 수정 API 테스트
```java
@DisplayName("updatePost: 글 수정에 성공한다.")
@Test
public void updatePost() throws Exception {
    // given: DB에 글 하나를 저장하고, 수정 요청 데이터를 준비한다
    final String url = "/api/posts/{id}";
    Post savedPost = postRepository.save(Post.builder()
            .title("title")
            .content("content")
            .build());
    UpdatePostRequest request = new UpdatePostRequest("new title", "new content");

    // when: 수정 API에 PUT 요청을 보낸다
    ResultActions result = mockMvc.perform(put(url, savedPost.getId())
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(objectMapper.writeValueAsString(request)));

    // then: 응답이 200이고, DB의 글이 바뀌었는지 확인한다
    result.andExpect(status().isOk());
    Post post = postRepository.findById(savedPost.getId()).get();
    assertThat(post.getTitle()).isEqualTo("new title");
    assertThat(post.getContent()).isEqualTo("new content");
}
```
이 코드에 나온 `@Test`, `assertThat`, `mockMvc` 등은 다음 글에서 설명한다.
- [스프링 부트 스타터 테스트](./02_spring_boot_starter_test.md)
- [JUnit](./03_junit.md)
- [AssertJ](./04_assertj.md)
- [@SpringBootTest와 MockMvc](./05_spring_boot_test_and_mockmvc.md)
