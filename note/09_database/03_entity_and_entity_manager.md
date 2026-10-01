---
title: 엔티티와 엔티티 매니저
date: 2026-10-01
tags: [database, jpa, entity]
---

# 엔티티와 엔티티 매니저

> 테이블, 컬럼, 기본 키, JPA 같은 용어는 [데이터베이스와 ORM](./01_database_and_orm.md), [JPA와 하이버네이트](./02_jpa_and_hibernate.md)에서 설명했다.

## 엔티티(Entity)
DB 테이블과 연결(매핑)된 Java 클래스다.
`@Entity`가 붙은 `Post` 클래스는 DB의 `post` 테이블에 대응하고, 필드 하나가 테이블의 컬럼 하나에 대응한다.
엔티티 객체 하나는 테이블의 행 하나에 대응한다.

### 엔티티 예시
```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Builder
    public Post(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
```

| 어노테이션 | 의미 |
|---|---|
| `@Entity` | 이 클래스가 엔티티임을 표시한다. 테이블 이름은 기본적으로 클래스 이름을 따른다 (`Post` → `post`) |
| `@Id` | 이 필드가 기본 키임을 표시한다 |
| `@GeneratedValue` | 기본 키 값을 개발자가 넣지 않고 자동으로 생성한다 |
| `@Column` | 필드와 연결할 컬럼의 속성을 정한다 |

`@GeneratedValue`의 `strategy` 값:
| 값 | 기본 키를 만드는 방법 |
|---|---|
| `IDENTITY` | DB의 자동 증가 기능(MySQL의 `AUTO_INCREMENT` 등)에 맡긴다 |
| `SEQUENCE` | DB의 시퀀스(번호를 차례로 만들어 주는 DB 객체)를 사용한다 |
| `TABLE` | 번호를 관리하는 별도 테이블을 사용한다 |
| `AUTO` | 사용하는 DB에 맞게 위 셋 중 하나를 자동으로 고른다 (기본값) |

`@Column`의 주요 속성:
| 속성 | 의미 |
|---|---|
| `name` | 컬럼 이름 |
| `nullable` | `false`면 빈 값(null)을 허용하지 않는다 |
| `updatable` | `false`면 처음 저장한 뒤 값을 바꾸지 않는다 |
| `unique` | `true`면 같은 값이 두 번 저장될 수 없다 |
| `length` | 문자열 컬럼의 최대 길이 (기본 255) |

> **null이란?**
> 값이 없음을 나타내는 Java(그리고 SQL)의 특별한 값이다.

### 엔티티 작성 규칙
- **기본 생성자가 필요하다.** JPA는 DB에서 조회한 데이터로 엔티티 객체를 만들 때 매개변수 없는 생성자를 사용한다. `public` 또는 `protected`여야 한다.
- 위 예시는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 기본 생성자를 `protected`로 만들었다. JPA는 사용할 수 있지만, 다른 코드에서 `new Post()`로 빈 객체를 만들지는 못한다.
- setter 대신 `update()`처럼 의미 있는 이름의 메서드로 값을 바꾼다. 그래야 값이 어디서 바뀌는지 찾기 쉽다.

Lombok 어노테이션은 [Lombok 어노테이션](../06_spring_boot_code/04_lombok.md)에서 설명했다.

## 엔티티 매니저(EntityManager)
엔티티를 저장, 조회, 수정, 삭제하는 JPA의 인터페이스다. 엔티티와 DB 사이에서 엔티티를 관리한다.

```java
// 저장
entityManager.persist(post);
// 조회
Post post = entityManager.find(Post.class, 1L);
// 삭제
entityManager.remove(post);
// 수정: 별도 메서드 없음. 조회한 엔티티의 값을 바꾸면 트랜잭션이 끝날 때 반영됨
post.update("새 제목", "새 내용");
```

## 엔티티 매니저 팩토리(EntityManagerFactory)
엔티티 매니저를 만드는 객체다.
- 엔티티 매니저 팩토리는 애플리케이션 전체에 **하나** 만들어진다. 만드는 데 비용이 크기 때문이다.
- 엔티티 매니저는 **요청(트랜잭션)마다** 따로 만들어진다. 여러 요청이 엔티티 매니저 하나를 같이 쓰면 서로의 데이터가 섞일 수 있기 때문이다.

```
EntityManagerFactory (1개)
   ├─ 요청 1 → EntityManager A
   ├─ 요청 2 → EntityManager B
   └─ 요청 3 → EntityManager C
```

## Spring Boot에서는
Spring Boot는 엔티티 매니저 팩토리를 자동으로 Bean으로 등록하고, 엔티티 매니저도 트랜잭션마다 알맞게 제공한다. ([자동 구성](../05_auto_configuration/01_auto_configuration.md))
또 보통은 엔티티 매니저를 직접 쓰지 않고, Spring Data JPA의 Repository를 사용한다. Repository가 내부에서 엔티티 매니저를 호출한다.
```java
postRepository.save(post);   // 내부에서 entityManager.persist(post) 호출
postRepository.findById(1L);    // 내부에서 entityManager.find(Post.class, 1L) 호출
```
→ [스프링 데이터 JPA](./05_spring_data_jpa.md)

엔티티 매니저가 엔티티를 관리하는 공간을 영속성 컨텍스트라고 한다. → [영속성 컨텍스트](./04_persistence_context.md)
