---
title: AOP (Aspect-Oriented Programming, 관점 지향 프로그래밍)
date: 2026-09-30
tags: [spring, aop, transaction]
---

# AOP (Aspect-Oriented Programming, 관점 지향 프로그래밍)

> 이 글에 나오는 Bean, 어노테이션, Service 같은 용어는 [빈과 스프링 컨테이너](./03_bean_and_container.md)와 [Spring Framework](../01_spring/01_spring_framework.md)에서 설명했다.

## 정의
트랜잭션, 로깅, 권한 검사처럼 여러 클래스에 반복해서 들어가는 공통 기능(**공통 관심사**)을 비즈니스 로직과 분리하는 기법이다.
Spring은 프록시 객체를 만들어 메서드 호출 앞뒤에 공통 로직을 끼워 넣는다.

> **트랜잭션이란?**
> DB에서 여러 작업을 하나의 단위로 묶어, 모두 성공하거나 모두 실패하도록 만드는 것이다.
> 예를 들어 게시글 저장과 태그 저장을 하나의 트랜잭션으로 묶으면, 태그 저장이 실패했을 때 먼저 실행된 게시글 저장도 취소된다.
> 이렇게 하면 게시글은 저장됐는데 태그는 없는 어중간한 상태가 DB에 남지 않는다.

> **커밋과 롤백이란?**
> - **커밋(commit)**: 트랜잭션 안에서 실행한 변경 내용을 DB에 확정해서 반영하는 것
> - **롤백(rollback)**: 트랜잭션 안에서 실행한 변경 내용을 모두 취소하고, 트랜잭션 시작 전 상태로 되돌리는 것

> **로깅이란?**
> 프로그램이 실행되는 동안 일어난 일(요청이 들어온 시각, 발생한 에러 내용 등)을 기록으로 남기는 것이다.

> **비즈니스 로직이란?**
> 애플리케이션이 해결하려는 문제에 해당하는 규칙과 처리다.
> 예: "제목이 비어 있으면 게시글을 저장하지 않는다", "작성자만 자신의 게시글을 수정할 수 있다"
> 트랜잭션 처리나 로깅은 비즈니스 로직이 아니라, 여러 비즈니스 로직에 공통으로 필요한 부가 기능이다.

## AOP를 쓰지 않으면
트랜잭션 코드를 모든 메서드에 직접 작성해야 한다.
```java
public void write(PostRequest req) {
    TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
    try {
        postRepository.save(req.toEntity());   // 비즈니스 로직
        tagRepository.saveAll(req.getTags());  // 비즈니스 로직
        transactionManager.commit(tx);
    } catch (RuntimeException e) {
        transactionManager.rollback(tx);
        throw e;
    }
}
```
비즈니스 로직은 두 줄인데 트랜잭션 코드가 더 길다. 그리고 같은 코드가 모든 메서드에 반복된다.

## AOP를 사용하면
```java
@Service
public class PostService {
    @Transactional  // 메서드 시작 시 트랜잭션 시작, 정상 종료 시 커밋, 예외 발생 시 롤백
    public void write(PostRequest req) {
        postRepository.save(req.toEntity());
        tagRepository.saveAll(req.getTags());
    }
}
```
개발자가 `begin()`, `commit()`, `rollback()`을 직접 호출하지 않아도 된다.
(기본 설정에서는 `RuntimeException` 계열 예외가 발생했을 때만 롤백한다.)

> **예외와 RuntimeException이란?**
> 예외는 프로그램 실행 중에 발생한 오류를 나타내는 Java 객체다. 예외가 발생하면(`throw`) 실행 중이던 메서드가 중단된다.
> `RuntimeException`은 컴파일러가 처리 여부를 검사하지 않는 예외 종류다. `IllegalArgumentException`, `NullPointerException` 등이 여기에 속한다.

## 프록시
> **프록시란?**
> 원래 객체와 같은 타입이면서, 원래 객체의 메서드를 대신 받아서 호출해 주는 객체다.
> Spring은 `@Transactional`이 붙은 클래스에 대해 프록시 객체를 만들고, 원래 객체 대신 이 프록시를 Bean으로 등록한다.
> 그래서 다른 클래스가 `postService.write()`를 호출하면 실제로는 프록시의 `write()`가 먼저 실행된다.
> 프록시가 하는 일을 단순하게 코드로 나타내면 다음과 같다. (Spring이 실행 중에 자동으로 만드는 것이므로 개발자가 작성하지 않는다.)
> ```java
> public class PostServiceProxy extends PostService {
>     private final PostService target;  // 원래 PostService 객체
>
>     public void write(PostRequest req) {
>         // 1. 트랜잭션 시작
>         try {
>             target.write(req);  // 2. 원래 객체의 메서드 호출
>             // 3. 커밋
>         } catch (RuntimeException e) {
>             // 3. 롤백
>             throw e;
>         }
>     }
> }
> ```

JPA를 사용할 때 `@Transactional` 안에서 엔티티의 값을 바꾸면 `save()`를 호출하지 않아도 DB에 반영된다. 트랜잭션이 커밋될 때 JPA의 **변경 감지**가 동작하기 때문이다.
→ [영속성 컨텍스트](../09_database/04_persistence_context.md)
