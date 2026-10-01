---
title: 스프링 데이터와 스프링 데이터 JPA
date: 2026-10-01
tags: [database, spring-data, jpa]
---

# 스프링 데이터와 스프링 데이터 JPA

> 엔티티 매니저, Bean, 인터페이스와 구현체 같은 용어는 [엔티티와 엔티티 매니저](./03_entity_and_entity_manager.md), [빈과 스프링 컨테이너](../03_spring_concepts/03_bean_and_container.md), [DI](../03_spring_concepts/02_di.md)에서 설명했다.

## 스프링 데이터(Spring Data)
여러 종류의 저장소(관계형 DB, MongoDB, Redis 등)를 **같은 방식으로** 사용할 수 있게 해 주는 Spring 프로젝트다.
저장, 조회, 삭제, 페이징, 정렬 같은 공통 기능을 인터페이스로 제공한다.

| 하위 프로젝트 | 대상 저장소 |
|---|---|
| Spring Data JPA | 관계형 DB (JPA 사용) |
| Spring Data MongoDB | MongoDB |
| Spring Data Redis | Redis |

> **MongoDB란?**
> 데이터를 테이블이 아니라 JSON과 비슷한 문서 형태로 저장하는 DB다.

> **페이징과 정렬이란?**
> - **페이징**: 데이터를 한 번에 모두 가져오지 않고, 정해진 개수씩 나눠서 가져오는 것 (예: 글 목록 2페이지, 10개씩)
> - **정렬**: 특정 컬럼 기준으로 순서를 정해서 가져오는 것 (예: 최신순)

## 스프링 데이터 JPA
Spring Data의 공통 기능에 JPA 전용 기능을 더한 것이다.
엔티티 매니저를 직접 쓰지 않고, **인터페이스를 선언하는 것만으로** Repository를 만들 수 있다.

```java
// 엔티티 매니저를 직접 쓰는 방식
@Repository
public class PostRepository {
    @PersistenceContext
    private EntityManager em;

    public void save(Post post) { em.persist(post); }
    public Post findById(Long id) { return em.find(Post.class, id); }
    public List<Post> findAll() {
        return em.createQuery("select a from Post a", Post.class).getResultList();
    }
}

// Spring Data JPA를 쓰는 방식
public interface PostRepository extends JpaRepository<Post, Long> {
}
```
구현 클래스를 작성하지 않아도, Spring Data JPA가 애플리케이션 시작 시 구현 객체를 자동으로 만들어 Bean으로 등록한다.
그래서 Service에서 생성자로 이 Repository를 주입받을 수 있다.

> **JpaRepository<Post, Long>이란?**
> Spring Data JPA가 제공하는 인터페이스로, 저장(`save`), 조회(`findById`, `findAll`), 삭제(`delete`) 같은 메서드가 이미 선언되어 있다.
> `<Post, Long>`은 이 Repository가 다루는 엔티티가 `Post`이고 그 기본 키 타입이 `Long`이라는 뜻이다.

> **extends란?**
> 인터페이스가 다른 인터페이스를 상속할 때 쓰는 Java 키워드다. 상속하면 부모 인터페이스에 선언된 메서드를 모두 물려받는다.

## 인터페이스 상속 구조
`JpaRepository`는 여러 인터페이스를 상속한다. (Spring Data 3 이상 기준)
```
JpaRepository                         ← flush, saveAndFlush, deleteAllInBatch 등 JPA 전용
 ├─ ListCrudRepository                ← findAll 등의 반환 타입을 List로
 │   └─ CrudRepository                ← save, findById, findAll, deleteById 등 기본 CRUD
 │       └─ Repository                ← 표시용, 메서드 없음
 ├─ ListPagingAndSortingRepository    ← 반환 타입을 List로
 │   └─ PagingAndSortingRepository    ← 페이징, 정렬
 │       └─ Repository
 └─ QueryByExampleExecutor            ← 예시 객체로 조회하는 기능
```
`JpaRepository` 하나만 상속하면 위 인터페이스의 메서드를 모두 사용할 수 있다.

> **CRUD란?**
> Create(생성), Read(조회), Update(수정), Delete(삭제)의 앞 글자를 딴 말로, 데이터를 다루는 기본 네 가지 작업이다.

자주 쓰는 메서드와 메서드 이름으로 쿼리를 만드는 방법은 [스프링 데이터 JPA에서 제공하는 메서드](./06_spring_data_jpa_methods.md)에서 정리한다.
