---
title: JPA와 하이버네이트
date: 2026-10-01
tags: [database, jpa, hibernate]
---

# JPA와 하이버네이트

> ORM, SQL 같은 용어는 [데이터베이스와 ORM](./01_database_and_orm.md)에서 설명했다.

## JDBC, JPA, Hibernate
- **JDBC**: Java에서 DB에 연결하고 SQL을 실행하기 위한 Java 표준 API다. 개발자가 SQL을 직접 작성한다.
- **JPA**(Jakarta Persistence API): Java 객체와 DB 테이블을 연결(매핑)해서, SQL을 직접 작성하지 않고 객체를 저장하고 조회할 수 있게 해주는 Java 표준 **명세**다.
- **Hibernate**: JPA 명세를 실제로 구현한 라이브러리다. Spring Boot에서 JPA를 사용하면 기본으로 Hibernate가 사용된다.

> **명세란?**
> "이런 인터페이스와 동작을 제공해야 한다"는 규칙이다. 명세에는 인터페이스와 어노테이션만 정의되어 있고, 실제 동작 코드는 들어 있지 않다.
> JPA의 `EntityManager`는 인터페이스이고, 그 실제 동작은 Hibernate의 구현 클래스가 처리한다.

> **Jakarta와 javax**
> JPA의 원래 이름은 Java Persistence API이고 패키지 이름이 `javax.persistence`였다.
> 관리 주체가 Eclipse 재단으로 옮겨지면서 이름이 Jakarta Persistence로 바뀌었고, Spring Boot 3부터는 `jakarta.persistence` 패키지를 사용한다.
> 그래서 엔티티 클래스에서는 `import jakarta.persistence.*;`를 사용한다.

## 세 기술의 관계
```
개발자 코드 (Repository)
   ↓ 사용
JPA (인터페이스, 어노테이션: @Entity, EntityManager ...)
   ↓ 구현
Hibernate (SQL 생성, 영속성 컨텍스트 관리)
   ↓ 사용
JDBC (실제 SQL 실행)
   ↓
DB
```
JPA를 쓴다고 JDBC가 사라지는 것이 아니다. Hibernate가 SQL을 만들어서 내부에서 JDBC로 실행한다.

## Hibernate가 하는 일
- 엔티티 클래스를 보고 테이블을 만든다 (`ddl-auto`, [차이점 2](../02_spring_boot/03_difference_embedded_was.md))
- `save()`, `findById()` 같은 호출을 SQL로 바꿔서 실행한다
- 영속성 컨텍스트로 엔티티를 관리한다 ([영속성 컨텍스트](./04_persistence_context.md))
- 사용하는 DB의 문법에 맞는 SQL을 만든다

## Hibernate가 만든 SQL 보기
`application.yml`에 다음 설정을 추가하면 Hibernate가 실행하는 SQL을 볼 수 있다.
```yaml
spring:
  jpa:
    show-sql: true          # 실행되는 SQL을 콘솔에 출력
    properties:
      hibernate:
        format_sql: true    # SQL을 줄바꿈해서 보기 좋게 출력
```
실행하면 콘솔에서 다음과 같은 SQL을 볼 수 있다.
```sql
select
    a1_0.id,
    a1_0.content,
    a1_0.title
from
    post a1_0
where
    a1_0.id=?
```

JPA의 핵심 개념인 엔티티와 엔티티 매니저는 [엔티티와 엔티티 매니저](./03_entity_and_entity_manager.md)에서 설명한다.
