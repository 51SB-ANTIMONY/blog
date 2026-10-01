---
title: JUnit
date: 2026-10-01
tags: [test, junit]
---

# JUnit

> 테스트 코드와 given-when-then은 [테스트 코드](./01_test_code_and_given_when_then.md)에서 설명했다.

## 정의
Java에서 테스트 코드를 작성하고 실행하는 데 쓰는 테스트 프레임워크다. 현재 버전은 JUnit 5 이상(JUnit Jupiter)이다.

JUnit의 특징은 다음과 같다.
- 테스트 하나는 `@Test`가 붙은 메서드 하나다.
- 테스트 메서드마다 **테스트 클래스의 객체를 새로 만들어** 실행한다. 그래서 한 테스트의 필드 값이 다른 테스트에 영향을 주지 않는다.
- 테스트 결과는 성공(pass) 또는 실패(fail)로 나온다.

## 기본 어노테이션
| 어노테이션 | 의미 |
|---|---|
| `@Test` | 이 메서드가 테스트임을 표시한다 |
| `@DisplayName("...")` | 테스트 결과에 보일 이름을 정한다. 한글로 적을 수 있다 |

## 생명주기 어노테이션
테스트 전후에 실행할 메서드를 지정한다.

| 어노테이션 | 실행 시점 | 메서드 조건 | 주 용도 |
|---|---|---|---|
| `@BeforeAll` | 전체 테스트 시작 전 **한 번** | `static` | DB 연결처럼 한 번만 준비하면 되는 작업 |
| `@BeforeEach` | **각** 테스트 시작 전 | 일반 메서드 | 테스트마다 데이터를 초기화하는 작업 |
| `@AfterEach` | **각** 테스트 끝난 후 | 일반 메서드 | 테스트에서 만든 데이터를 정리하는 작업 |
| `@AfterAll` | 전체 테스트 끝난 후 **한 번** | `static` | `@BeforeAll`에서 준비한 자원을 정리하는 작업 |

> **static 메서드란?**
> 객체를 만들지 않고 클래스 이름으로 바로 호출할 수 있는 메서드다.
> JUnit은 테스트마다 객체를 새로 만들기 때문에, 전체에서 한 번만 실행되는 `@BeforeAll`, `@AfterAll`은 특정 객체에 속하지 않는 `static`이어야 한다.

### 실행 순서 확인
```java
class JUnitCycleTest {
    @BeforeAll
    static void beforeAll() { System.out.println("@BeforeAll"); }

    @BeforeEach
    void beforeEach() { System.out.println("@BeforeEach"); }

    @Test
    void test1() { System.out.println("test1"); }

    @Test
    void test2() { System.out.println("test2"); }

    @AfterEach
    void afterEach() { System.out.println("@AfterEach"); }

    @AfterAll
    static void afterAll() { System.out.println("@AfterAll"); }
}
```
출력 결과:
```
@BeforeAll
@BeforeEach
test1
@AfterEach
@BeforeEach
test2
@AfterEach
@AfterAll
```
(`test1`과 `test2`의 실행 순서는 보장되지 않는다. 테스트는 서로 순서에 의존하지 않게 작성해야 한다.)

## 예시: 테스트마다 DB 비우기
`@BeforeEach`로 각 테스트 전에 DB를 비울 수 있다.
```java
@BeforeEach
void cleanUp() {
    postRepository.deleteAll();  // 이전 테스트가 저장한 데이터를 지움
}
```
이렇게 하지 않으면 애플리케이션 시작 시 넣은 데이터나 이전 테스트가 저장한 글이 남아 있어서, "글이 1개 저장됐는지" 같은 검증이 실패할 수 있다.

## JUnit만으로 검증하기
JUnit에도 검증 메서드가 있다.
```java
assertEquals(3, sum);  // 기대값, 실제값 순서
```
하지만 기대값과 실제값의 순서가 헷갈리기 쉬워서, 보통은 더 읽기 쉬운 [AssertJ](./04_assertj.md)를 함께 사용한다.
