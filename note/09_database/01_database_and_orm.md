---
title: 데이터베이스와 ORM
date: 2026-10-01
tags: [database, orm]
---

# 데이터베이스와 ORM

## 데이터베이스(DB)란?
데이터를 체계적으로 저장하고, 여러 사용자나 프로그램이 동시에 조회하고 수정할 수 있게 관리하는 시스템이다.
정확히는 데이터 묶음 자체를 데이터베이스, 그 데이터를 관리하는 프로그램을 **DBMS**(Database Management System)라고 한다. MySQL, H2, PostgreSQL, Oracle이 DBMS다.

## 관계형 데이터베이스(RDB)
데이터를 **테이블** 형태로 저장하는 데이터베이스다. 가장 널리 쓰인다.

`post` 테이블 예시:
| id | title | content |
|---|---|---|
| 1 | 첫 글 | 안녕하세요 |
| 2 | 두 번째 글 | 반갑습니다 |
| 3 | 세 번째 글 | 스프링 공부 |

| 용어 | 의미 | 위 표에서 |
|---|---|---|
| 테이블 | 같은 종류의 데이터를 모은 표 | `post` |
| 행(row, 레코드) | 데이터 한 건 | id가 1인 줄 |
| 열(column, 컬럼) | 데이터의 항목 | `title` |
| 기본 키(PK, Primary Key) | 각 행을 구분하는 고유한 값. 중복되거나 비어 있을 수 없다 | `id` |

## SQL
DB에 명령을 내리는 언어다.
```sql
-- 조회
SELECT * FROM post WHERE id = 1;
-- 추가
INSERT INTO post (title, content) VALUES ('첫 글', '안녕하세요');
-- 수정
UPDATE post SET title = '새 제목' WHERE id = 1;
-- 삭제
DELETE FROM post WHERE id = 1;
```

> **쿼리란?**
> DB에 보내는 요청(SQL 문장 하나)을 말한다.

## ORM이란?
Object-Relational Mapping의 줄임말로, **Java 객체와 관계형 DB의 테이블을 연결(매핑)** 해 주는 기술이다.
개발자는 SQL 대신 객체의 메서드를 호출하고, ORM이 그에 맞는 SQL을 만들어 실행한다.

```java
// SQL을 직접 쓰는 방식 (JDBC)
String sql = "SELECT id, title, content FROM post WHERE id = ?";
PreparedStatement ps = connection.prepareStatement(sql);
ps.setLong(1, 1L);
ResultSet rs = ps.executeQuery();
rs.next();
Post post = new Post(rs.getString("title"), rs.getString("content"));

// ORM을 쓰는 방식 (Spring Data JPA)
Post post = postRepository.findById(1L).get();
```
두 코드는 같은 SQL을 실행한다. ORM을 쓰면 SQL 작성과, 조회 결과를 객체로 옮기는 코드가 사라진다.

> **매핑이란?**
> 두 대상의 항목을 서로 짝지어 연결하는 것이다. ORM에서는 클래스 ↔ 테이블, 필드 ↔ 컬럼, 객체 하나 ↔ 행 하나를 짝짓는다.

### ORM의 장점
- SQL을 직접 작성하지 않고 객체 중심으로 코드를 작성할 수 있다.
- DB 종류(MySQL, H2 등)가 바뀌어도 코드를 거의 고치지 않는다. ORM이 DB에 맞는 SQL을 만들기 때문이다.
- 조회 결과를 객체로 옮기는 반복 코드가 줄어든다.

### ORM의 단점
- 복잡한 조회(여러 테이블을 조인하고 통계를 내는 등)는 ORM만으로 표현하기 어려워서 SQL을 직접 써야 할 때가 있다.
- ORM이 내부에서 어떤 SQL을 만드는지 모르면, 의도하지 않게 SQL이 많이 실행되는 성능 문제가 생길 수 있다.
- ORM의 동작 방식(영속성 컨텍스트 등)을 따로 배워야 한다.

> **조인이란?**
> 두 개 이상의 테이블을 공통 컬럼으로 연결해서 함께 조회하는 SQL 기능이다.

Java의 대표적인 ORM 표준이 JPA다. → [JPA와 하이버네이트](./02_jpa_and_hibernate.md)
