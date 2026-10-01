---
title: Lombok 어노테이션
date: 2026-10-01
tags: [lombok, annotation]
---

# Lombok 어노테이션

> 이 글에 나오는 어노테이션, 생성자, 생성자 주입 같은 용어는 [Spring Framework](../01_spring/01_spring_framework.md)와 [DI](../03_spring_concepts/02_di.md)에서 설명했다.

## Lombok이란?
getter, 생성자처럼 클래스마다 반복해서 작성하는 코드를 **컴파일할 때 자동으로 만들어 주는** 라이브러리다.
개발자는 어노테이션만 붙이고, 실제 코드는 Lombok이 `.class` 파일에 추가한다.

> **getter와 setter란?**
> - **getter**: `getTitle()`처럼 `private` 필드의 값을 외부에서 읽을 수 있게 해 주는 메서드
> - **setter**: `setTitle(String title)`처럼 `private` 필드의 값을 외부에서 바꿀 수 있게 해 주는 메서드

> **어노테이션 프로세서란?**
> 컴파일할 때 어노테이션을 읽고 코드를 추가로 만들어 내는 프로그램이다.
> Lombok은 어노테이션 프로세서로 동작하기 때문에 `build.gradle`에 두 줄을 추가한다.
> ```gradle
> compileOnly 'org.projectlombok:lombok'          // 코드에서 Lombok 어노테이션을 쓸 수 있게 함
> annotationProcessor 'org.projectlombok:lombok'  // 컴파일할 때 Lombok이 코드를 만들게 함
> ```
> 코드는 컴파일할 때 이미 만들어지므로 실행할 때는 Lombok이 필요 없다. 그래서 `implementation`이 아니라 `compileOnly`다.

## Lombok 없이 쓴 코드와 비교
```java
// Lombok 없이
public class UpdatePostRequest {
    private String title;
    private String content;

    public UpdatePostRequest() {}

    public UpdatePostRequest(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public String getTitle() { return title; }
    public String getContent() { return content; }
}
```
```java
// Lombok 사용
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UpdatePostRequest {
    private String title;
    private String content;
}
```
두 코드를 컴파일한 결과는 같다.

## 자주 쓰는 어노테이션

### 메서드를 만드는 어노테이션
| 어노테이션 | 만들어지는 코드 |
|---|---|
| `@Getter` | 모든 필드의 getter (`getTitle()` 등) |
| `@Setter` | 모든 필드의 setter (`setTitle()` 등) |
| `@ToString` | 필드 값을 문자열로 보여 주는 `toString()` |
| `@EqualsAndHashCode` | 필드 값으로 두 객체가 같은지 비교하는 `equals()`, `hashCode()` |

`@Getter`, `@Setter`는 클래스 대신 필드 하나에만 붙일 수도 있다.

### 생성자를 만드는 어노테이션
| 어노테이션 | 만들어지는 생성자 |
|---|---|
| `@NoArgsConstructor` | 매개변수가 없는 생성자 |
| `@AllArgsConstructor` | 모든 필드를 매개변수로 받는 생성자 |
| `@RequiredArgsConstructor` | `final` 필드(와 `@NonNull` 필드)만 매개변수로 받는 생성자 |

`access` 속성으로 생성자의 접근 범위를 정할 수 있다.
```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // protected Post() {}
```

> **접근 제어자(public, protected, private)란?**
> 클래스, 메서드, 필드를 어디서 사용할 수 있는지 정하는 Java 키워드다.
> - `public`: 어디서나 사용 가능
> - `protected`: 같은 패키지와, 이 클래스를 상속한 자식 클래스에서만 사용 가능
> - `private`: 그 클래스 안에서만 사용 가능

### 객체 생성을 돕는 어노테이션
| 어노테이션 | 만들어지는 코드 |
|---|---|
| `@Builder` | `Post.builder().title("제목").content("내용").build()` 형식으로 객체를 만드는 코드 |

> **빌더 패턴이란?**
> 생성자에 값을 순서대로 넣는 대신, 필드 이름이 적힌 메서드로 값을 하나씩 넣은 뒤 마지막에 `build()`로 객체를 만드는 방식이다.
> `new Post("제목", "내용")`은 어떤 값이 title이고 어떤 값이 content인지 생성자 정의를 봐야 알 수 있지만,
> `Post.builder().title("제목").content("내용").build()`는 코드만 보고 알 수 있다.

### 여러 개를 합친 어노테이션
| 어노테이션 | 포함하는 것 |
|---|---|
| `@Data` | `@Getter` + `@Setter` + `@RequiredArgsConstructor` + `@ToString` + `@EqualsAndHashCode` |

`@Data`는 편하지만 모든 필드에 setter를 만들기 때문에, 엔티티처럼 값이 아무 데서나 바뀌면 안 되는 클래스에는 쓰지 않는다.

## 클래스 역할별로 자주 쓰는 조합
| 클래스 | Lombok 어노테이션 | 이유 |
|---|---|---|
| 엔티티 | `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder` (생성자에) | JPA는 기본 생성자가 필요하지만, 외부에서 빈 객체를 함부로 만들지 못하게 `protected`로 막는다. setter 대신 `update()` 같은 메서드로만 값을 바꾼다 |
| 요청 DTO | `@NoArgsConstructor`, `@AllArgsConstructor`, `@Getter` | JSON을 객체로 바꿀 때 기본 생성자가 필요하고, 테스트에서는 모든 값을 받는 생성자를 쓴다 |
| 응답 DTO | `@Getter` | 객체를 JSON으로 바꿀 때 getter로 값을 읽는다 |
| Service, Controller | `@RequiredArgsConstructor` | `final` 필드를 받는 생성자를 만들어 생성자 주입에 사용한다 |

엔티티와 DTO는 [엔티티와 엔티티 매니저](../09_database/03_entity_and_entity_manager.md), [스프링 부트 계층 구조](../07_spring_boot_structure/01_layers.md)에서 설명한다.

> **IntelliJ에서 Lombok을 쓸 때**
> IntelliJ의 Settings → Build, Execution, Deployment → Compiler → Annotation Processors에서 **Enable annotation processing**이 켜져 있어야 Lombok이 만든 코드를 IDE가 인식한다.
