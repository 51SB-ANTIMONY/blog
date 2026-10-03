---
title: JPA Auditing으로 작성/수정 시각 자동 기록
date: 2026-10-03
tags: [jpa, auditing, spring-data-jpa]
---

# JPA Auditing으로 작성/수정 시각 자동 기록

> 엔티티와 변경 감지는 [엔티티와 엔티티 매니저](../09_database/03_entity_and_entity_manager.md), [영속성 컨텍스트](../09_database/04_persistence_context.md)에서 설명했다.

## JPA Auditing이란?
엔티티가 저장되거나 수정될 때 "언제 만들어졌는지", "언제 마지막으로 바뀌었는지" 같은 값을 Spring Data JPA가 자동으로 채워 주는 기능이다.

> **Auditing이란?**
> 원래 "감사(검사)"라는 뜻이다. 여기서는 데이터가 언제, 누구에 의해 만들어지고 바뀌었는지 기록하는 것을 말한다.

이 기능이 없으면 글을 저장하거나 수정하는 코드마다 `createdAt = LocalDateTime.now()`를 직접 넣어야 한다.

## 설정 방법 (3단계)

### 1단계. Auditing 기능 켜기
```java
@EnableJpaAuditing
@SpringBootApplication
public class BlogApplication { ... }
```
`@EnableJpaAuditing`은 프로젝트 전체에서 JPA Auditing을 켜는 스위치다. 이 어노테이션이 없으면 아래 어노테이션들을 붙여도 값이 채워지지 않는다.

### 2단계. 엔티티에 리스너 붙이기
```java
@EntityListeners(AuditingEntityListener.class)
@Entity
public class Article { ... }
```
> **리스너(Listener)란?**
> 특정 사건이 일어날 때 자동으로 실행되도록 등록해 둔 코드다.

`AuditingEntityListener`는 Spring Data JPA가 제공하는 리스너로, 엔티티가 저장되기 직전과 수정되기 직전에 실행되어 시각 필드를 채운다.

### 3단계. 시각을 담을 필드 만들기
```java
@CreatedDate
@Column(name = "created_at")
private LocalDateTime createdAt;

@LastModifiedDate
@Column(name = "updated_at")
private LocalDateTime updatedAt;
```

| 어노테이션 | 값이 채워지는 때 |
|---|---|
| `@CreatedDate` | 엔티티가 처음 저장될 때 한 번 |
| `@LastModifiedDate` | 엔티티가 저장되거나 수정될 때마다 |

- `LocalDateTime`: 날짜와 시각을 함께 담는 Java 타입이다. 예: `2026-10-03T14:30:15`
- `@Column(name = "created_at")`: DB 테이블의 컬럼 이름을 `created_at`으로 정한다. Java는 `createdAt`처럼 단어 사이를 대문자로 잇고, DB는 `created_at`처럼 밑줄로 잇는 관례가 있다.

## 동작 흐름

### 글 생성
```
BlogService.save()
  → blogRepository.save(article)
  → 저장 직전 AuditingEntityListener 실행
  → createdAt, updatedAt에 현재 시각 입력
  → INSERT 쿼리 실행
```

### 글 수정
```java
@Transactional
public Article update(long id, UpdateArticleRequest request) {
    Article article = blogRepository.findById(id).orElseThrow(...);
    article.update(request.getTitle(), request.getContent());
    return article;
}
```
`update()`에는 시각을 바꾸는 코드가 없다. 그래도 `updatedAt`이 바뀐다.
```
article.update(...)로 제목/내용 변경
  → 트랜잭션이 끝날 때 변경 감지가 바뀐 것을 발견
  → UPDATE 직전 AuditingEntityListener 실행
  → updatedAt에 현재 시각 입력 (createdAt은 그대로)
  → UPDATE 쿼리 실행
```

## `import.sql` 수정
```sql
-- 이전
INSERT INTO article (title, content) VALUES ('제목 1', '내용1')

-- 이후
INSERT INTO article (title, content, created_at, updated_at) VALUES ('제목 1', '내용 1', NOW(), NOW())
```
> **`import.sql`이란?**
> `src/main/resources/`에 두면 애플리케이션이 시작될 때 Hibernate가 자동으로 실행하는 SQL 파일이다. 테스트용 초기 데이터를 넣을 때 쓴다.

`created_at`, `updated_at`에 `NOW()`(현재 시각을 돌려주는 SQL 함수)를 직접 넣었다.

직접 넣은 이유: **Auditing은 JPA를 통해 저장할 때만 동작한다.** `import.sql`은 JPA를 거치지 않고 DB에 SQL을 바로 실행하므로 리스너가 실행되지 않는다. 직접 넣지 않으면 두 컬럼이 비어 있게 되고, 상세 화면에서 작성 시각이 표시되지 않는다.

> **테이블 컬럼은 어떻게 추가되나?**
> 지금은 H2 메모리 DB를 쓰고 있어서, 애플리케이션이 시작될 때마다 Hibernate가 엔티티를 보고 테이블을 새로 만든다. 그래서 엔티티에 필드를 추가하면 `created_at`, `updated_at` 컬럼도 자동으로 생긴다.
