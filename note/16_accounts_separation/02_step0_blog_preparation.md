---
title: accounts 분리하기 - 0단계 blog 준비
date: 2026-10-03
tags: [refactoring, rest-api, accounts]
---

# accounts 분리하기 - 0단계 blog 준비

> 전체 단계는 [로드맵](./01_roadmap.md)에 있다.

## 1. 목적
1단계에서 blog의 로그인 코드를 accounts로 **그대로 복사**할 계획이었다. 복사하기 전에 blog 코드를 정리해 둬야, 복사한 뒤에 생기는 차이가 "accounts로 바꾸면서 생긴 변경"인지 "원래 있던 문제"인지 구별할 수 있다.

| 목적 | 내용 |
|---|---|
| 글 API를 먼저 안정시키기 | 로그인 방식을 바꾸는 동안 글 API의 응답 형식, 에러 처리가 함께 흔들리지 않게 |
| 회원 정보를 accounts의 모양에 맞추기 | accounts 회원은 아이디(`uid`), 닉네임(필수)을 가진다. blog `User`를 먼저 같은 모양으로 맞춰서, 복사한 코드가 바로 동작하게 |
| 패키지 이름 정리 | blog는 `kr.antimony.blog`, accounts는 `kr.antimony.accounts`로 나눠서 두 프로젝트가 같은 패키지를 쓰지 않게 |

## 2. 세부 단계와 수행 방안

### 0-1. 글 API 정리 (`45829cd`)
**왜:** 글 작성·수정은 엔티티를, 조회는 DTO를 응답하는 등 응답 형식이 제각각이었고, 없는 글은 500, 남의 글 수정도 500이었다.

**어떻게:**
| 할 일 | 방법 |
|---|---|
| 응답 형식 통일 | 작성, 목록, 상세, 수정 모두 `ArticleResponse`(id, title, content, author, createdAt, updatedAt). 삭제는 204 |
| 에러 형식 통일 | 상태 코드를 가진 `ApiException`과 `GlobalExceptionHandler`(`@RestControllerAdvice`)로 `{"message", "errors"}` 형식 |
| 상태 코드 | 없는 글 404(`ArticleNotFoundException`), 작성자가 아님 403(`ArticleForbiddenException`), 입력값 오류 400 |
| 입력값 검증 | Bean Validation(`@NotBlank`, `@Size`)으로 제목·내용 검사 |
| 테스트 | 404, 403, 400 응답과 데이터가 바뀌지 않는 것을 확인 |

> **`@RestControllerAdvice`란?**
> 여러 컨트롤러에서 던진 예외를 한곳에서 받아 응답으로 바꾸는 클래스에 붙이는 어노테이션이다. 컨트롤러마다 같은 에러 처리 코드를 쓰지 않아도 된다.

### 0-2. 회원 정보 맞추기 (`eade9bf`)
**왜:** accounts는 아이디·비밀번호 로그인도 지원할 계획이라 "아이디"가 필요하고, 화면에 보여 줄 닉네임은 항상 있어야 한다.

**어떻게:**
| 할 일 | 방법 |
|---|---|
| `uid` 추가 | 아이디·비밀번호 로그인에서 쓰는 아이디. 회원가입 규칙을 정하는 1-4 전까지는 제약(`NOT NULL`, `UNIQUE`)을 미뤘다 |
| `nickname` 필수 | `NOT NULL` |
| 소셜 가입 시 닉네임 | 처음 로그인할 때만 제공자의 이름으로 정하고, 이후 로그인에서는 바꾸지 않는다 |
| 로그인 조회 기준 | `UserDetailService`가 `uid`로 회원을 찾는다 |

### 0-3. 패키지 이름 변경 (`074debf`)
**왜:** blog 코드가 `kr.antimony` 바로 아래에 있었다. accounts를 `kr.antimony.accounts`로 만들면 blog만 한 단계 위에 있게 되어 구조가 어긋난다.

**어떻게:** blog의 모든 클래스를 `kr.antimony.blog`로 옮겼다(IDE의 패키지 이동). 코드 내용은 바꾸지 않았다.

## 3. 결과
- blog 글 API가 같은 형식으로 응답하고, 에러를 상태 코드로 구별한다.
- blog `User`가 accounts 회원과 같은 속성(`uid`, `nickname`)을 가진다.
- blog와 accounts가 서로 다른 패키지를 쓸 준비가 됐다.

이 정리 덕분에 1-2에서 blog 코드를 복사할 때 **패키지 이름만** 바꾸면 됐다.
