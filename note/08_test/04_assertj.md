---
title: AssertJ
date: 2026-10-01
tags: [test, assertj]
---

# AssertJ

> 검증(assertion)은 [스프링 부트 스타터 테스트](./02_spring_boot_starter_test.md)에서 설명했다.

## 정의
테스트 결과를 검증하는 메서드를 제공하는 라이브러리다.
`assertThat(실제값).검증메서드(기대값)` 형식으로, 코드를 왼쪽부터 읽으면 문장처럼 읽힌다.

```java
import static org.assertj.core.api.Assertions.assertThat;

assertThat(sum).isEqualTo(3);  // "sum은 3과 같아야 한다"
```

> **import static이란?**
> 다른 클래스의 `static` 메서드를 클래스 이름 없이 바로 쓸 수 있게 가져오는 Java 문법이다.
> `import static ...Assertions.assertThat;`을 쓰면 `Assertions.assertThat(...)` 대신 `assertThat(...)`이라고 쓸 수 있다.

## JUnit의 검증 메서드와 비교
```java
// JUnit
assertEquals(3, sum);           // 3이 기대값인지 실제값인지 메서드 정의를 봐야 앎

// AssertJ
assertThat(sum).isEqualTo(3);   // sum이 실제값, 3이 기대값임이 코드에 드러남
```

## 자주 쓰는 검증 메서드
### 값 비교
| 메서드 | 검증 내용 |
|---|---|
| `isEqualTo(A)` | A와 같다 |
| `isNotEqualTo(A)` | A와 다르다 |
| `isNull()` / `isNotNull()` | null이다 / null이 아니다 |

### 문자열
| 메서드 | 검증 내용 |
|---|---|
| `contains(A)` | A를 포함한다 |
| `doesNotContain(A)` | A를 포함하지 않는다 |
| `startsWith(A)` | A로 시작한다 |
| `endsWith(A)` | A로 끝난다 |
| `isEmpty()` | 비어 있다 |
| `isNotEmpty()` | 비어 있지 않다 |

### 숫자
| 메서드 | 검증 내용 |
|---|---|
| `isPositive()` | 양수다 |
| `isNegative()` | 음수다 |
| `isGreaterThan(1)` | 1보다 크다 |
| `isLessThan(1)` | 1보다 작다 |

### 컬렉션(List 등)
| 메서드 | 검증 내용 |
|---|---|
| `hasSize(3)` | 요소가 3개다 |
| `isEmpty()` | 요소가 없다 |
| `contains(A)` | A를 포함한다 |

> **컬렉션이란?**
> `List`, `Set`, `Map`처럼 여러 객체를 한꺼번에 담는 Java의 자료 구조다.

### 예외
```java
assertThatThrownBy(() -> postService.findById(999L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("not found: 999");
```
괄호 안의 코드를 실행했을 때 `IllegalArgumentException`이 `"not found: 999"` 메시지로 발생해야 테스트가 성공한다.

> **`() -> ...` (람다식)이란?**
> 메서드를 이름 없이 짧게 적는 Java 문법이다. 위 코드에서는 "나중에 실행할 코드"를 `assertThatThrownBy`에 넘기려고 사용한다.

## 이어서 쓰기
검증 메서드는 이어서 쓸 수 있다. 앞의 조건을 모두 만족해야 성공이다.
```java
assertThat("Spring Boot")
        .isNotEmpty()
        .startsWith("Spring")
        .endsWith("Boot");
```

## 예시
```java
List<Post> posts = postRepository.findAll();
assertThat(posts.size()).isEqualTo(1);
assertThat(posts.get(0).getTitle()).isEqualTo(title);
```
`assertThat(posts.size()).isEqualTo(1)`은 `assertThat(posts).hasSize(1)`로도 쓸 수 있다.
`hasSize`를 쓰면 실패했을 때 목록의 실제 내용까지 메시지에 나와서 원인을 찾기 쉽다.
