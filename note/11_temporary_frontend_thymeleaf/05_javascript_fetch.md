---
title: JavaScript fetch로 API 호출하기
date: 2026-10-03
tags: [javascript, fetch, frontend, rest-api]
---

# JavaScript fetch로 API 호출하기

> HTTP 메서드와 REST API 규칙은 [REST API](../10_rest_api/02_rest_api.md), [REST API 사용 규칙](../10_rest_api/03_rest_api_rules.md)에서 설명했다.

## 왜 JavaScript가 필요한가
HTML만으로는 `GET`(링크 이동)과 `POST`(폼 제출) 요청만 보낼 수 있다. 이 프로젝트의 API는 수정에 `PUT`, 삭제에 `DELETE`를 쓰고, 데이터를 JSON으로 받는다. 그래서 JavaScript로 직접 요청을 만들어 보낸다.

이 코드는 `src/main/resources/static/js/article.js`에 있고, 세 템플릿이 모두 아래 줄로 불러온다.
```html
<script src="/js/article.js"></script>
```

## 기본 개념

> **DOM이란?**
> Document Object Model. 브라우저가 HTML을 읽어서 JavaScript로 다룰 수 있게 만든 객체 구조다. JavaScript는 DOM을 통해 HTML 요소를 찾고, 값을 읽고, 바꾼다.

> **`document.getElementById('아이디')`란?**
> HTML에서 `id="아이디"`인 요소를 찾아 돌려준다. 없으면 `null`을 돌려준다.

> **`addEventListener('click', 함수)`란?**
> 요소를 클릭했을 때 실행할 함수를 등록한다.

> **`fetch`란?**
> 브라우저 JavaScript에서 서버로 HTTP 요청을 보내는 함수다. 페이지를 새로 고치지 않고 요청을 보낼 수 있다.

> **`.then(함수)`란?**
> `fetch`는 응답이 올 때까지 기다리지 않고 바로 다음 줄로 넘어간다. 응답이 도착한 뒤 실행할 코드를 `.then()` 안에 넣는다.

> **`JSON.stringify(객체)`란?**
> JavaScript 객체를 JSON 문자열로 바꾼다. `{title: '제목'}`은 `'{"title":"제목"}'`이 된다.

## 세 가지 기능
`article.js`는 버튼 세 개에 각각 기능을 붙인다.

| 버튼 id | 있는 화면 | HTTP 요청 | 성공 후 이동 |
|---|---|---|---|
| `delete-btn` | 상세 | `DELETE /api/articles/{id}` | `/articles` (목록) |
| `modify-btn` | 작성/수정 (수정 모드) | `PUT /api/articles/{id}` | `/articles/{id}` (상세) |
| `create-btn` | 작성/수정 (작성 모드) | `POST /api/articles` | `/articles` (목록) |

세 블록 모두 `if (버튼)`으로 감싸져 있다. 세 화면이 같은 JS 파일을 불러오므로, 버튼이 없는 화면에서는 `getElementById`가 `null`을 돌려주고 그 블록은 건너뛴다.

### 1. 삭제
```javascript
const deleteButton = document.getElementById('delete-btn');

if (deleteButton) {
    deleteButton.addEventListener('click', event => {
        let id = document.getElementById('article-id').value;
        fetch(`/api/articles/${id}`, {
            method: 'DELETE'
        })
            .then(() => {
                alert('삭제가 완료되었습니다.');
                location.replace('/articles');
            });
    });
}
```
- 글 id는 상세 화면의 숨김 입력칸(`<input type="hidden" id="article-id">`)에서 읽는다.
- `` `/api/articles/${id}` ``: 백틱(`` ` ``)으로 감싼 문자열 안에 `${변수}`를 쓰면 변수 값이 들어간다. 이것을 **템플릿 리터럴**이라고 한다.
  > Thymeleaf의 `${...}`와 모양이 같지만 다르다. Thymeleaf의 `${...}`는 서버에서 처리되고, JavaScript의 `${...}`는 브라우저에서 처리된다.
- `alert(...)`: 브라우저 알림창을 띄운다.
- `location.replace(주소)`: 주소로 이동한다. `location.href`와 달리 현재 페이지를 방문 기록에 남기지 않아서, 뒤로 가기를 눌러도 삭제된 글 화면으로 돌아가지 않는다.

### 2. 수정
```javascript
const modifyButton = document.getElementById('modify-btn');

if (modifyButton) {
    modifyButton.addEventListener('click', event => {
        let params = new URLSearchParams(location.search);
        let id = params.get('id');

        fetch(`/api/articles/${id}`, {
            method: 'PUT',
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                title: document.getElementById('title').value,
                content: document.getElementById('content').value
            })
        })
            .then(() => {
                alert('수정이 완료되었습니다.');
                location.replace(`/articles/${id}`);
            });
    });
}
```
- 글 id는 현재 주소의 쿼리 파라미터에서 읽는다.
  - `location.search`: 현재 주소의 `?` 뒤 부분이다. `/new-article?id=1`이면 `?id=1`
  - `new URLSearchParams(...)`: 쿼리 문자열을 다루기 쉬운 객체로 바꾼다.
  - `params.get('id')`: `id`의 값 `"1"`을 꺼낸다.
- `headers`의 `"Content-Type": "application/json"`: 보내는 데이터가 JSON이라고 서버에 알린다. 서버의 `@RequestBody`가 이 헤더를 보고 JSON을 Java 객체(`UpdateArticleRequest`)로 바꾼다.
- `body`: 입력칸의 제목과 내용을 JSON으로 만들어 보낸다.

### 3. 생성
```javascript
const createButton = document.getElementById('create-btn');

if (createButton) {
    createButton.addEventListener('click', event => {
        fetch('/api/articles', {
            method: 'POST',
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                title: document.getElementById('title').value,
                content: document.getElementById('content').value
            })
        })
            .then(() => {
                alert('등록 완료되었습니다.');
                location.replace('/articles');
            });
    });
}
```
수정과 거의 같다. 차이는 다음과 같다.
- 아직 글이 없으므로 id가 필요 없다. 주소는 `/api/articles`다.
- 메서드가 `POST`다.
- 서버의 `AddArticleRequest`가 JSON을 받는다.

## 요청 하나의 전체 흐름 (수정 예시)
```
1. 사용자가 /new-article?id=1 화면에서 제목/내용을 고치고 "수정" 클릭
2. article.js가 주소에서 id=1을 읽고, 입력칸 값을 JSON으로 만듦
3. fetch가 PUT /api/articles/1 요청을 보냄
   본문: {"title":"새 제목","content":"새 내용"}
4. BlogApiController.updateArticle() → BlogService.update() 실행
5. 응답이 오면 .then() 안의 코드 실행 → 알림창 → /articles/1로 이동
6. BlogViewController.getArticle()이 수정된 글로 상세 화면을 만들어 보냄
```
