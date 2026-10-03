---
title: Thymeleaf와 템플릿 작성
date: 2026-10-03
tags: [spring-boot, thymeleaf, frontend, bootstrap]
---

# Thymeleaf와 템플릿 작성

## Thymeleaf란?
서버에서 HTML을 만들어 주는 **템플릿 엔진**이다.

> **템플릿 엔진이란?**
> 빈칸이 있는 HTML 틀(템플릿)과 데이터를 합쳐서 최종 HTML을 만드는 프로그램이다.

HTML 태그에 `th:`로 시작하는 속성을 적어 두면, 서버가 그 자리에 실제 데이터를 넣은 HTML을 브라우저로 보낸다.
```html
<!-- 템플릿 -->
<h1 th:text="${article.title}"></h1>

<!-- 브라우저가 받는 결과 -->
<h1>제목 1</h1>
```

## 의존성 추가
`build.gradle`에 한 줄을 추가했다.
```gradle
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
```
이 스타터를 추가하면 Spring Boot가 Thymeleaf 설정을 자동으로 해 준다([자동 구성](../05_auto_configuration/01_auto_configuration.md)).

## 파일 위치 규칙
| 폴더 | 들어가는 파일 | 브라우저에서 접근하는 주소 |
|---|---|---|
| `src/main/resources/templates/` | Thymeleaf 템플릿 (`.html`) | 직접 접근할 수 없다. 컨트롤러를 거쳐야 한다. |
| `src/main/resources/static/` | JS, CSS, 이미지 같은 정적 파일 | `static` 아래 경로 그대로. `static/js/article.js`는 `/js/article.js` |

> **정적 파일이란?**
> 서버가 가공하지 않고 그대로 브라우저에 보내는 파일이다. 요청할 때마다 내용이 같다.

컨트롤러가 `"articleList"`를 반환하면 Thymeleaf는 앞에 `templates/`, 뒤에 `.html`을 붙여 `templates/articleList.html`을 찾는다.

## 템플릿을 쓰기 위한 선언
```html
<html xmlns:th="http://www.thymeleaf.org">
```
이 HTML에서 `th:` 속성을 쓴다는 선언이다. 이 줄이 없어도 Thymeleaf는 동작하지만, IDE가 `th:` 속성을 인식해서 자동 완성과 오류 표시를 해 준다.

## 이번에 쓴 Thymeleaf 문법

### 표현식
| 표현식 | 의미 | 예시 |
|---|---|---|
| `${...}` | 변수 표현식. 컨트롤러가 Model에 담아 준 값을 꺼낸다. | `${article.title}` |
| `@{...}` | URL 표현식. 링크 주소를 만든다. | `@{/articles/{id}(id=${item.id})}` |
| `\|...\|` | 리터럴 대체. 문자열 안에 `${...}`를 섞어 쓸 수 있다. | `\|Posted on ${날짜}\|` |
| `#temporals` | 날짜/시각을 다루는 유틸리티 객체 | `${#temporals.format(article.createdAt, 'yyyy-MM-dd HH:mm')}` |

> **Model이란?**
> 컨트롤러가 템플릿에 넘겨줄 데이터를 담는 객체다. 컨트롤러에서 `model.addAttribute("article", ...)`로 담으면 템플릿에서 `${article}`로 꺼낸다.

`${article.title}`은 `article` 객체의 `getTitle()`을 호출한 결과다. 그래서 DTO에 Lombok `@Getter`를 붙였다.

URL 표현식에서 `{id}` 자리는 뒤의 괄호 `(id=${item.id})`의 값으로 채워진다. `item.id`가 3이면 `/articles/3`이 된다.

### 속성
| 속성 | 하는 일 |
|---|---|
| `th:text` | 태그 안의 글자를 값으로 바꾼다. |
| `th:value` | `<input>`의 `value`를 값으로 바꾼다. |
| `th:href` | `<a>`의 링크 주소를 바꾼다. |
| `th:onclick` | 클릭했을 때 실행할 JavaScript를 넣는다. |
| `th:each` | 목록의 원소 수만큼 태그를 반복해서 만든다. |
| `th:if` | 조건이 참일 때만 태그를 만든다. 거짓이면 태그 자체가 사라진다. |

> **`th:text`와 HTML 이스케이프**
> `th:text`는 값 안의 `<`, `>` 같은 문자를 `&lt;`, `&gt;`로 바꿔서 넣는다. 이것을 **이스케이프**라고 한다.
> 그래서 글 내용에 `<script>`가 들어 있어도 스크립트로 실행되지 않고 글자 그대로 보인다.

## 화면 3개

세 파일 모두 같은 "My Blog" 헤더를 두고, Bootstrap CSS를 불러오고, 마지막에 `/js/article.js`를 불러온다.

> **Bootstrap이란?**
> `btn`, `card`, `container` 같은 클래스 이름만 붙이면 미리 만들어진 스타일이 적용되는 CSS 라이브러리다.

> **CDN이란?**
> Content Delivery Network. 라이브러리 파일을 인터넷 서버에 올려 두고 주소로 불러 쓰는 방식이다. 프로젝트 안에 파일을 직접 넣지 않아도 된다.
> ```html
> <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/css/bootstrap.min.css">
> ```

### 1. `articleList.html` - 글 목록
```html
<button type="button" id="create-btn"
        th:onclick="|location.href='@{/new-article}'|"
        class="btn btn-secondary btn-sm mb-3">글 등록</button>
<div class="row-6" th:each="item : ${articles}">
    <div class="card">
        <div class="card-header" th:text="${item.id}"></div>
        <div class="card-body">
            <h5 class="card-title" th:text="${item.title}"></h5>
            <p class="card-text" th:text="${item.content}"></p>
            <a th:href="@{/articles/{id}(id=${item.id})}" class="btn btn-primary">보러가기</a>
        </div>
    </div>
</div>
```
- `th:each="item : ${articles}"`: `articles` 목록에서 글을 하나씩 꺼내 `item`이라는 이름으로 쓴다. 글이 3개면 카드가 3개 만들어진다.
- "글 등록" 버튼은 `/new-article`로 이동한다.
  > **`location.href`란?**
  > 브라우저의 현재 주소를 나타내는 JavaScript 값이다. 여기에 새 주소를 넣으면 그 주소로 이동한다.
- "보러가기" 링크는 `/articles/{id}`로 이동한다.

### 2. `article.html` - 글 상세
```html
<input type="hidden" id="article-id" th:value="${article.id}">
<h1 class="fw-bolder mb-1" th:text="${article.title}"></h1>
<div class="text-muted fst-italic mb-2"
     th:text="|Posted on ${#temporals.format(article.createdAt, 'yyyy-MM-dd HH:mm')}|"></div>
<p class="fs-5 mb-4" th:text="${article.content}"></p>
<button type="button" id="modify-btn"
        th:onclick="|location.href='@{/new-article?id={articleId}(articleId=${article.id})}'|"
        class="btn btn-primary btn-sm">수정</button>
<button type="button" id="delete-btn" class="btn btn-secondary btn-sm">삭제</button>
```
- `<input type="hidden">`: 화면에 보이지 않는 입력칸이다. 글 id를 넣어 두면 JavaScript가 삭제 요청을 보낼 때 읽어 간다.
- `#temporals.format(...)`: `LocalDateTime` 값을 `2026-10-03 14:30` 같은 형식의 문자열로 바꾼다.
- 수정 버튼은 `/new-article?id=1`처럼 id를 붙여 작성 화면으로 이동한다.
- 삭제 버튼에는 `th:onclick`이 없다. `article.js`가 처리한다.

### 3. `newArticle.html` - 글 작성/수정 겸용
```html
<input type="hidden" id="article-id" th:value="${article.id}">
<input type="text" class="form-control" placeholder="제목" id="title" th:value="${article.title}">
<textarea class="form-control h-25" rows="10" placeholder="내용" id="content" th:text="${article.content}"></textarea>
<button th:if="${article.id} != null" type="button" id="modify-btn" class="btn btn-primary btn-sm">수정</button>
<button th:if="${article.id} == null" type="button" id="create-btn" class="btn btn-primary btn-sm">등록</button>
```
화면 하나로 작성과 수정을 함께 처리한다. 구분 기준은 `article.id`가 `null`인지다.

| 상황 | 주소 | `article.id` | 입력칸 | 보이는 버튼 |
|---|---|---|---|---|
| 새 글 작성 | `/new-article` | `null` | 비어 있음 | 등록 (`create-btn`) |
| 기존 글 수정 | `/new-article?id=1` | `1` | 기존 제목/내용이 채워짐 | 수정 (`modify-btn`) |

- `<input>`은 값을 `value` 속성에 넣으므로 `th:value`를 쓴다.
- `<textarea>`는 값을 태그 사이에 넣으므로 `th:text`를 쓴다.
- `placeholder`: 입력칸이 비어 있을 때 흐리게 보이는 안내 문구다.
