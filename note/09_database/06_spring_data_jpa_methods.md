---
title: 스프링 데이터 JPA에서 제공하는 메서드
date: 2026-10-01
tags: [database, spring-data, jpa]
---

# 스프링 데이터 JPA에서 제공하는 메서드

> `JpaRepository`, CRUD 같은 용어는 [스프링 데이터 JPA](./05_spring_data_jpa.md)에서 설명했다.

## 기본으로 제공하는 메서드
`JpaRepository`를 상속하면 다음 메서드를 바로 쓸 수 있다. (`T`는 엔티티 타입, `ID`는 기본 키 타입)

| 메서드 | 하는 일 |
|---|---|
| `save(T)` | 저장. 새 엔티티면 INSERT, 이미 있는 엔티티면 UPDATE |
| `saveAll(목록)` | 여러 개 저장 |
| `findById(ID)` | 기본 키로 하나 조회. 결과는 `Optional<T>` |
| `findAll()` | 전체 조회 |
| `findAll(Sort)` | 정렬해서 전체 조회 |
| `findAll(Pageable)` | 페이지 단위로 조회 |
| `existsById(ID)` | 그 기본 키의 데이터가 있는지 확인 |
| `count()` | 전체 개수 |
| `deleteById(ID)` | 기본 키로 삭제 |
| `delete(T)` | 엔티티로 삭제 |
| `deleteAll()` | 전체 삭제 |

> **Optional이란?**
> 값이 있을 수도, 없을 수도 있음을 나타내는 Java 클래스다. 조회 결과가 없을 때 `null` 대신 비어 있는 `Optional`을 돌려준다.
> ```java
> postRepository.findById(id)
>         .orElseThrow(() -> new IllegalArgumentException("not found: " + id));
> // 값이 있으면 꺼내고, 없으면 예외를 던진다
> ```
> | 메서드 | 동작 |
> |---|---|
> | `get()` | 값을 꺼낸다. 비어 있으면 `NoSuchElementException` |
> | `orElse(기본값)` | 값이 있으면 그 값, 없으면 기본값 |
> | `orElseThrow(...)` | 값이 있으면 그 값, 없으면 지정한 예외 |
> | `isPresent()` | 값이 있으면 `true` |

> **`deleteById`는 대상이 없어도 예외를 던지지 않는다**
> Spring Data 3부터 `deleteById`는 해당 id의 데이터가 없으면 아무것도 하지 않고 끝난다.
> 그래서 삭제 API에서 `deleteById`만 호출하면, `DELETE /api/posts/999`처럼 없는 글을 삭제해도 200 OK가 응답된다.
> 없는 글일 때 404를 응답하려면 삭제 전에 `existsById`나 `findById`로 먼저 확인해야 한다.

## 메서드 이름으로 쿼리 만들기 (쿼리 메서드)
정해진 규칙에 맞게 메서드 이름을 지으면, Spring Data JPA가 이름을 분석해서 쿼리를 만든다. 구현 코드는 필요 없다.

```java
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByTitle(String title);
    // SELECT * FROM post WHERE title = ?

    List<Post> findByTitleContaining(String keyword);
    // SELECT * FROM post WHERE title LIKE '%keyword%'

    List<Post> findByTitleAndContent(String title, String content);
    // SELECT * FROM post WHERE title = ? AND content = ?

    List<Post> findAllByOrderByIdDesc();
    // SELECT * FROM post ORDER BY id DESC

    boolean existsByTitle(String title);
    long countByTitleContaining(String keyword);
}
```

### 이름 규칙
`find...By` + `필드 이름` + `조건 키워드` 형식이다. `find` 대신 `exists`, `count`, `delete`를 쓸 수도 있다.

| 키워드 | 예시 | 만들어지는 조건 |
|---|---|---|
| (없음) | `findByTitle` | `title = ?` |
| `And` | `findByTitleAndContent` | `title = ? AND content = ?` |
| `Or` | `findByTitleOrContent` | `title = ? OR content = ?` |
| `Containing` | `findByTitleContaining` | `title LIKE '%?%'` |
| `StartingWith` | `findByTitleStartingWith` | `title LIKE '?%'` |
| `GreaterThan` | `findByIdGreaterThan` | `id > ?` |
| `LessThan` | `findByIdLessThan` | `id < ?` |
| `Between` | `findByIdBetween` | `id BETWEEN ? AND ?` |
| `IsNull` | `findByContentIsNull` | `content IS NULL` |
| `In` | `findByIdIn` | `id IN (...)` |
| `OrderBy...Asc/Desc` | `findByTitleOrderByIdDesc` | `ORDER BY id DESC` |

> **LIKE란?**
> 문자열이 특정 패턴과 일치하는지 검사하는 SQL 조건이다. `%`는 "아무 글자나 0개 이상"을 뜻한다. `'%스프링%'`은 "스프링"이 들어간 모든 문자열과 일치한다.

필드 이름이 엔티티에 없으면(예: `findByName`인데 `Post`에 `name` 필드가 없음) 애플리케이션이 시작될 때 에러가 난다.

## 직접 쿼리 작성하기: @Query
메서드 이름이 너무 길어지거나 복잡한 조회가 필요하면 `@Query`로 직접 쿼리를 적는다.
```java
@Query("select a from Post a where a.title like %:keyword% order by a.id desc")
List<Post> search(@Param("keyword") String keyword);
```

> **JPQL이란?**
> 위 `@Query`에 적힌 쿼리 언어다. SQL과 비슷하지만 테이블이 아니라 **엔티티와 필드**를 대상으로 쓴다. (`post` 테이블이 아니라 `Post` 엔티티)
> Hibernate가 JPQL을 사용 중인 DB에 맞는 SQL로 바꿔서 실행한다.
