---
title: 임시 프론트엔드 - 전체 구조와 변경 사항
date: 2026-10-03
tags: [spring-boot, thymeleaf, frontend]
---

# 임시 프론트엔드 - 전체 구조와 변경 사항

> 이미 만든 글 CRUD REST API([REST API](../10_rest_api/02_rest_api.md)) 위에 **Thymeleaf로 화면을 붙인 작업**을 정리한다.
> 나중에 별도 프론트엔드를 만들 때까지 쓰는 임시 화면이다.

## 한 줄 요약
- 글 목록, 글 상세, 글 작성/수정 **화면 3개**를 만들었다.
- 글에 **작성 시각과 수정 시각을 자동으로 기록**하는 기능(JPA Auditing)을 넣었다.

## 바뀐 파일
| 구분 | 파일 | 내용 | 정리한 노트 |
|---|---|---|---|
| 수정 | `build.gradle` | Thymeleaf 의존성 추가 | [Thymeleaf](./02_thymeleaf.md) |
| 수정 | `BlogApplication.java` | JPA Auditing 켜기 | [JPA Auditing](./04_jpa_auditing.md) |
| 수정 | `domain/Article.java` | `createdAt`, `updatedAt` 필드 추가 | [JPA Auditing](./04_jpa_auditing.md) |
| 수정 | `resources/import.sql` | 초기 데이터에 시각 컬럼 추가 | [JPA Auditing](./04_jpa_auditing.md) |
| 신규 | `controller/BlogViewController.java` | HTML 화면을 돌려주는 컨트롤러 | [뷰 컨트롤러와 DTO](./03_view_controller_and_dto.md) |
| 신규 | `dto/ArticleListViewResponse.java` | 목록 화면용 DTO | [뷰 컨트롤러와 DTO](./03_view_controller_and_dto.md) |
| 신규 | `dto/ArticleViewResponse.java` | 상세/작성 화면용 DTO | [뷰 컨트롤러와 DTO](./03_view_controller_and_dto.md) |
| 신규 | `templates/articleList.html` | 글 목록 페이지 | [Thymeleaf](./02_thymeleaf.md) |
| 신규 | `templates/article.html` | 글 상세 페이지 | [Thymeleaf](./02_thymeleaf.md) |
| 신규 | `templates/newArticle.html` | 글 작성/수정 페이지 | [Thymeleaf](./02_thymeleaf.md) |
| 신규 | `static/js/article.js` | 버튼을 누르면 API를 부르는 스크립트 | [JavaScript와 fetch](./05_javascript_fetch.md) |

## 화면과 데이터의 역할 분담
이번 작업에서 요청은 두 종류로 나뉜다.

| 하는 일 | 담당 | URL | 응답 |
|---|---|---|---|
| 페이지 열기 (목록, 상세, 작성 화면) | `BlogViewController` (새로 만듦) | `/articles`, `/articles/{id}`, `/new-article` | HTML |
| 데이터 바꾸기 (생성, 수정, 삭제) | `BlogApiController` (기존) | `/api/articles`, `/api/articles/{id}` | JSON |

화면용 URL은 `/articles`, 데이터 API는 `/api/articles`로 앞부분을 다르게 해서 구분했다.

데이터 변경은 화면의 JavaScript가 기존 API를 호출하는 방식이다. 그래서 **API 쪽 코드는 수정하지 않았다.**

## 전체 흐름
```
[글 목록 보기]
브라우저 ─ GET /articles ─▶ BlogViewController ─▶ BlogService.findAll()
         ◀── 완성된 HTML (articleList.html) ──

[글 상세 보기]
브라우저 ─ GET /articles/1 ─▶ BlogViewController ─▶ BlogService.findById(1)
         ◀── 완성된 HTML (article.html) ──

[새 글 작성]
브라우저 ─ GET /new-article ─▶ BlogViewController ─▶ 빈 DTO
         ◀── 빈 입력칸이 있는 HTML (newArticle.html) ──
"등록" 클릭 ─▶ article.js ─ POST /api/articles (JSON) ─▶ BlogApiController

[글 수정]
브라우저 ─ GET /new-article?id=1 ─▶ BlogViewController ─▶ 1번 글을 담은 DTO
         ◀── 기존 내용이 채워진 HTML (newArticle.html) ──
"수정" 클릭 ─▶ article.js ─ PUT /api/articles/1 (JSON) ─▶ BlogApiController

[글 삭제]
상세 화면에서 "삭제" 클릭 ─▶ article.js ─ DELETE /api/articles/1 ─▶ BlogApiController
```

> **서버 사이드 렌더링(SSR)이란?**
> 서버가 데이터를 채운 완성된 HTML을 만들어 브라우저에 보내는 방식이다. 이번 화면이 이 방식이다.
> 반대로 서버는 JSON만 주고 브라우저의 JavaScript가 HTML을 만드는 방식은 **클라이언트 사이드 렌더링(CSR)**이라고 한다. React 같은 프론트엔드 프레임워크가 주로 이 방식을 쓴다.
