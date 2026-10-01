---
title: 영속성 컨텍스트
date: 2026-10-01
tags: [database, jpa, persistence-context]
---

# 영속성 컨텍스트

> 엔티티, 엔티티 매니저, 트랜잭션, 커밋 같은 용어는 [엔티티와 엔티티 매니저](./03_entity_and_entity_manager.md)와 [AOP](../03_spring_concepts/04_aop.md)에서 설명했다.

## 정의
엔티티 매니저가 엔티티를 보관하고 관리하는 공간이다.
엔티티를 DB에 바로 저장하거나 DB에서 매번 조회하지 않고, 이 공간에 먼저 보관한 뒤 필요할 때 DB와 맞춘다.
Spring에서는 기본적으로 **트랜잭션 하나마다 영속성 컨텍스트 하나**가 만들어지고, 트랜잭션이 끝나면 사라진다.

> **영속성이란?**
> 데이터가 프로그램이 끝난 뒤에도 사라지지 않고 남아 있는 성질이다.

영속성 컨텍스트에는 네 가지 특징이 있다. **1차 캐시, 쓰기 지연, 변경 감지, 지연 로딩**이다.

## 엔티티의 상태
특징을 보기 전에, 엔티티가 영속성 컨텍스트와 어떤 관계인지 나타내는 네 가지 상태를 알아 둔다.

| 상태 | 의미 | 만드는 코드 |
|---|---|---|
| 비영속 (new) | 영속성 컨텍스트와 관계없는 새 객체 | `new Post(...)` |
| 영속 (managed) | 영속성 컨텍스트가 관리하는 상태 | `persist()`, `find()` |
| 준영속 (detached) | 관리되다가 분리된 상태 | `detach()`, 트랜잭션 종료 |
| 삭제 (removed) | 삭제하기로 표시된 상태 | `remove()` |

```java
Post post = new Post("제목", "내용");  // 비영속
entityManager.persist(post);                // 영속
entityManager.detach(post);                 // 준영속
entityManager.remove(post);                 // 삭제 (영속 상태인 엔티티에만 사용 가능)
```

아래 네 가지 특징은 모두 **영속 상태**인 엔티티에만 적용된다.

## 1. 1차 캐시
영속성 컨텍스트 안에 있는 저장 공간이다. 엔티티를 **기본 키를 키로 하여** 보관한다.

```java
Post a1 = entityManager.find(Post.class, 1L);  // 1차 캐시에 없음 → DB 조회 SQL 실행 → 1차 캐시에 저장
Post a2 = entityManager.find(Post.class, 1L);  // 1차 캐시에 있음 → SQL 실행 없이 캐시에서 꺼냄
System.out.println(a1 == a2);  // true (같은 객체)
```
같은 트랜잭션 안에서 같은 엔티티를 여러 번 조회해도 DB에는 한 번만 요청한다.

> **캐시란?**
> 한 번 조회한 결과를 저장해 두고, 같은 요청이 오면 원래 저장소(DB)를 다시 조회하지 않고 저장해 둔 결과를 돌려주는 방식이다.

> **1차 캐시의 범위**
> 1차 캐시는 트랜잭션이 끝나면 영속성 컨텍스트와 함께 사라진다. 그래서 서로 다른 요청끼리는 1차 캐시를 공유하지 않는다.

## 2. 쓰기 지연
`persist()`를 호출해도 INSERT SQL을 바로 실행하지 않는다.
SQL을 **쓰기 지연 저장소**에 모아 두었다가, 트랜잭션이 커밋될 때 한꺼번에 DB로 보낸다.

```java
transaction.begin();
entityManager.persist(postA);  // INSERT SQL을 쓰기 지연 저장소에 모아 둠
entityManager.persist(postB);  // INSERT SQL을 쓰기 지연 저장소에 모아 둠
transaction.commit();             // 이때 INSERT 2개가 DB로 전송됨
```
SQL을 모아서 보내면 DB와 통신하는 횟수를 줄일 수 있다.

> **플러시(flush)란?**
> 영속성 컨텍스트의 변경 내용(쓰기 지연 저장소의 SQL)을 DB에 보내는 것이다. 트랜잭션이 커밋될 때 자동으로 일어난다.
> 플러시는 SQL을 보내는 것이고, 커밋은 그 결과를 DB에 확정하는 것이다.

> **예외: `IDENTITY` 전략**
> `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 쓰면, 기본 키를 DB가 만들기 때문에 INSERT를 실행해야 id를 알 수 있다.
> 그런데 1차 캐시는 기본 키로 엔티티를 보관하므로, 이 경우 Hibernate는 `persist()` 시점에 INSERT를 바로 실행한다. 즉 INSERT에는 쓰기 지연이 적용되지 않는다.

## 3. 변경 감지 (Dirty Checking)
영속 상태인 엔티티의 값을 바꾸면, 트랜잭션이 커밋될 때 **바뀐 것을 자동으로 찾아서** UPDATE SQL을 실행한다.

동작 과정:
1. 엔티티를 처음 영속성 컨텍스트에 보관할 때, 그때의 값을 복사해 둔다(**스냅샷**).
2. 트랜잭션이 커밋될 때, 현재 엔티티와 스냅샷을 비교한다.
3. 달라진 엔티티가 있으면 UPDATE SQL을 만들어 실행한다.

다음 Service 코드가 이 기능을 사용한다.
```java
@Transactional
public Post update(long id, UpdatePostRequest request) {
    Post post = postRepository.findById(id)   // 영속 상태로 조회, 스냅샷 저장
            .orElseThrow(() -> new IllegalArgumentException("not found: " + id));
    post.update(request.getTitle(), request.getContent());  // 값만 바꿈
    return post;
}   // 메서드가 끝나면 트랜잭션 커밋 → 스냅샷과 비교 → UPDATE SQL 실행
```
`save()`를 다시 호출하지 않아도 수정 내용이 DB에 반영된다.
`@Transactional`이 없으면 조회가 끝나는 순간 트랜잭션도 끝나서 엔티티가 준영속 상태가 되고, 변경 감지가 일어나지 않는다.

> **dirty란?**
> "더러워진", 즉 처음 상태에서 값이 바뀐 상태를 가리키는 말이다.

## 4. 지연 로딩 (Lazy Loading)
연관된 엔티티를 처음부터 함께 조회하지 않고, **실제로 사용하는 시점에** 조회하는 기능이다.

예를 들어 글(`Post`)이 작성자(`Member`)와 연관되어 있다고 하자.
```java
@Entity
public class Post {
    @ManyToOne(fetch = FetchType.LAZY)  // 지연 로딩
    private Member author;
}
```
```java
Post post = postRepository.findById(1L).get();  // post만 조회 (SELECT 1번)
// post.getAuthor()는 아직 실제 Member가 아니라 프록시 객체

String name = post.getAuthor().getName();  // 이때 member를 조회 (SELECT 1번 더)
```
작성자 정보가 필요 없는 화면에서는 member를 조회하지 않으므로 불필요한 SQL을 줄일 수 있다.

반대로 처음부터 함께 조회하는 방식을 **즉시 로딩**(`FetchType.EAGER`)이라고 한다.

> **연관 관계와 @ManyToOne이란?**
> 한 엔티티가 다른 엔티티를 필드로 가지는 관계다. `@ManyToOne`은 "여러 글(Many)이 한 작성자(One)에 속한다"는 관계를 나타낸다.

> **프록시란?**
> 원래 객체와 같은 타입이면서, 원래 객체 대신 메서드 호출을 받아 주는 객체다. ([AOP](../03_spring_concepts/04_aop.md))
> 지연 로딩에서는 Hibernate가 `Member` 대신 프록시를 넣어 두었다가, 프록시의 메서드가 처음 호출될 때 DB에서 실제 데이터를 조회한다.

## 정리
| 특징 | 하는 일 | 효과 |
|---|---|---|
| 1차 캐시 | 조회한 엔티티를 기본 키로 보관 | 같은 엔티티를 다시 조회할 때 SQL 생략 |
| 쓰기 지연 | SQL을 모았다가 커밋 때 한꺼번에 전송 | DB 통신 횟수 감소 |
| 변경 감지 | 커밋 때 스냅샷과 비교해 UPDATE 자동 실행 | `save()` 없이 수정 반영 |
| 지연 로딩 | 연관 엔티티를 사용할 때 조회 | 필요 없는 SQL 생략 |
