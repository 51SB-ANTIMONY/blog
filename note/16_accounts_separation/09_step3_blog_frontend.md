---
title: accounts 분리하기 - 3단계 blog 화면을 Next.js로 (구현, 확인, 트러블슈팅)
date: 2026-10-03
tags: [nextjs, ssr, bff, csrf, frontend]
---

# accounts 분리하기 - 3단계 blog 화면을 Next.js로 (구현, 확인, 트러블슈팅)

> 목적과 계획은 [3단계 계획](./08_step3_blog_frontend_plan.md)에, 전체 단계는 [로드맵](./01_roadmap.md)에 있다.
> 이 노트는 실제로 만든 코드와 이유, 확인 결과, 겪은 문제를 정리한다.

## 1. 커밋
| 단계 | 커밋 | 내용 |
|---|---|---|
| 3-0 | (디자인 아티팩트) | 글 상세 추가, 없는 기능 비활성, 모바일 로그인 상태 |
| 계획 | `3b30feb` | 정한 것과 프로토타입 변경을 계획 노트에 기록 |
| 3-1 | `8d3636a` | 카테고리·요약, 읽기 공개, `/api/me`, `editable` |
| 3-2 | `dafb90a` (accounts), `4ca15c0` (blog) | blog 주소를 3000으로. accounts에 등록한 redirect_uri와 로그아웃 후 주소 변경 |
| 3-3, 3-4 | `22c9b09` | Next.js 화면 (`blog/frontend`) |
| 3-5 | `22ff712` | Thymeleaf 임시 화면 삭제 |
| 3-7 | `c617521` | README |

## 2. 3-1: API 보완

### 카테고리와 요약
프로토타입의 홈은 카테고리 탭(전체, 일상, 학습, 시냇가에 심은 나무)과 카테고리 카드로 글을 나눠 보여 주고, 목록에는 글마다 두세 줄 요약이 있다. 그래서 글에 두 값을 더했다.

| 값 | 저장 | 규칙 |
|---|---|---|
| `category` | `enum Category { DAILY, STUDY, TREE }`, DB에는 이름 문자열(`@Enumerated(EnumType.STRING)`) | 필수 |
| `summary` | `VARCHAR(300)` | 선택. 비어 있으면 `null`로 저장 |

> **`@Enumerated(EnumType.STRING)`이란?**
> enum 값을 DB에 이름(`"DAILY"`)으로 저장하게 하는 설정이다. 기본값(`ORDINAL`)은 순서 번호(`0`)로 저장하는데, 나중에 enum 중간에 값을 끼워 넣으면 이미 저장된 번호의 뜻이 바뀌어 버린다.

목록에 보일 요약은 `Article.excerpt()`가 만든다. 요약을 썼으면 그것을, 아니면 본문 앞 120자를 쓴다. 줄바꿈과 연속 공백은 한 칸으로 바꾼다. 응답에는 저장한 `summary`(수정 화면에 그대로 보여 줄 값)와 `excerpt`(목록에 보일 값)를 함께 넣었다.

### 읽기 공개
블로그는 누구나 글을 읽을 수 있어야 하므로, 인가 규칙에서 글 조회만 열었다.
```java
.requestMatchers("/api/csrf").permitAll()
.requestMatchers(HttpMethod.GET, "/api/articles", "/api/articles/*").permitAll()
.requestMatchers("/api/**").authenticated()
```
규칙은 위에서부터 처음 맞는 것이 적용된다. 그래서 공개할 주소를 `/api/**`보다 먼저 적었다. 나중에 다시 막으려면 두 번째 줄만 지우면 된다.

### `editable`과 `/api/me`
- `editable`: 로그인한 사용자가 이 글의 작성자인가. 화면이 수정·삭제 버튼을 보여 줄지 정한다. 로그인하지 않았으면 `false`. 버튼을 숨기는 것은 화면의 편의일 뿐이고, 실제 수정·삭제 권한은 지금처럼 서버(`authorizeArticleAuthor`)가 다시 검사한다.
- `GET /api/me`: 로그인했으면 `{"nickname": "..."}`, 아니면 401. 헤더의 "[닉네임]님"과 로그인·로그아웃 버튼을 고르는 데 쓴다.

> **왜 서버가 다시 검사하나**
> 화면의 버튼은 사용자가 고칠 수 있다. 개발자 도구나 `curl`로 버튼 없이도 `PUT /api/articles/1`을 보낼 수 있으므로, 권한은 요청을 받는 서버가 판단해야 한다.

## 3. 3-2: blog backend를 Next.js 뒤에 두기
브라우저가 3000(Next.js)만 보게 되면, Spring이 만드는 주소도 3000이어야 한다. 1-7에서 accounts에 쓴 방법과 같다.

| 바꾼 것 | 내용 |
|---|---|
| `server.forward-headers-strategy: framework` | Next.js가 붙이는 `X-Forwarded-Host: localhost:3000`을 보고, redirect_uri를 `http://localhost:3000/login/oauth2/code/accounts`로, 로그아웃 후 주소를 `http://localhost:3000/`으로 만든다 |
| `defaultSuccessUrl("/")` | 로그인 후 화면의 첫 페이지로 |
| accounts 설정 (accounts 커밋) | blog 클라이언트의 `redirect-uri`, `post-logout-redirect-uri`를 3000으로 |

accounts는 등록된 주소와 정확히 같은 redirect_uri만 받는다. 그래서 blog 쪽만 바꾸면 accounts가 "등록되지 않은 redirect_uri"로 거절한다. 두 저장소가 나뉘어도 지킬 수 있도록, accounts 설정 변경은 accounts 안의 파일만 담은 커밋으로 따로 했다.

## 4. 3-3, 3-4: Next.js 화면

### 구성
```
blog/frontend/
├── next.config.ts            rewrites: /api/**, /oauth2/**, /login/oauth2/**, /logout → Spring(8080)
├── app/
│   ├── layout.tsx            글꼴(Noto Serif KR, IBM Plex Sans KR)
│   ├── globals.css           프로토타입의 색·크기, 라이트·다크, PC·모바일
│   ├── page.tsx              홈 (카테고리 탭, 소개, 말씀 카드, 카테고리 카드, 최근 글)
│   ├── articles/[id]/        글 상세, edit/ 글 수정
│   ├── write/                새 글 쓰기
│   └── not-found.tsx         없는 글 (404)
├── components/               헤더, 푸터, 로고, 글쓰기 화면, 수정·삭제 버튼, 삭제 확인
└── lib/
    ├── server.ts             서버에서 Spring 조회 (쿠키 전달)
    ├── api.ts                브라우저에서 API 호출 (CSRF), 로그인 시작, 로그아웃
    └── categories.ts, format.ts, links.ts, types.ts
```

### 서버에서 그리는 화면과 브라우저에서 움직이는 화면
| 화면 | 그리는 곳 | 이유 |
|---|---|---|
| 홈, 글 상세, 404 | Next.js 서버 (Server Component) | 검색 엔진이 처음 받은 HTML에서 글을 읽을 수 있게 |
| 글 쓰기·수정, 수정·삭제 버튼, 로그아웃 버튼 | 브라우저 (`"use client"`) | 입력과 클릭에 반응해야 해서 |

> **Server Component와 Client Component란?**
> Next.js(App Router)의 컴포넌트는 기본적으로 서버에서만 실행되어 HTML로 바뀐다(Server Component). 파일 맨 위에 `"use client"`를 쓴 컴포넌트는 브라우저에서도 실행되어 클릭, 입력 같은 상호작용을 처리한다(Client Component).

### 서버에서 그릴 때 로그인 상태 알기: 쿠키 전달
홈 헤더에는 로그인 여부("[닉네임]님" 또는 "로그인")가, 글 상세에는 내 글이면 수정·삭제 버튼이 나와야 한다. 둘 다 "누가 보고 있는지"에 따라 달라진다.

Next.js 서버가 Spring을 부를 때, 브라우저가 보낸 쿠키(`BLOG_SESSION`)를 그대로 넘겼다(`lib/server.ts`).
```ts
const cookieHeader = (await cookies()).toString();
fetch(`${backend}/api/me`, { headers: { Cookie: cookieHeader }, cache: "no-store" });
```
Spring은 이 요청을 그 사용자의 요청으로 보고 `/api/me`, `editable`을 계산한다. 그래서 화면이 처음 그려질 때부터 로그인 상태가 맞게 나오고, 브라우저에서 다시 불러와 바뀌는 깜빡임이 없다.

이 파일 맨 위에는 `import "server-only"`를 두었다. 실수로 브라우저 코드에서 이 파일을 쓰면 빌드가 실패한다.

> **`cookies()`란?**
> Next.js 서버에서 지금 요청에 붙어 온 쿠키를 읽는 함수다. 이 함수를 쓴 화면은 요청마다 새로 그려진다(빌드할 때 미리 만들어 두지 않는다).

### 브라우저에서 API 부르기: CSRF
글 쓰기·수정·삭제는 브라우저에서 `/api/articles`를 부른다. 이 주소는 rewrites로 Spring에 가므로 세션 쿠키가 함께 간다. 쓰기 요청에는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 담는다. accounts frontend의 `lib/api.ts`와 같은 방식이다.

로그아웃은 Spring이 accounts 로그아웃 주소로 리다이렉트해야 하므로, `fetch`가 아니라 `POST /logout` 폼을 만들어 보낸다. `_csrf` 값은 `/api/csrf`에서 받는다.

### 로그인 후 원래 화면으로 돌아오기
글쓰기 버튼을 누른 사람이 로그인을 마치면 글쓰기 화면으로 돌아와야 한다. 그런데 Spring의 "로그인 후 돌아갈 요청 저장"(RequestCache)은 Spring이 받은 요청만 기억한다. `/write`는 Next.js 화면이라 Spring은 모른다.

그래서 화면이 직접 기억한다.
1. `/write`의 서버 화면이 `/api/me`로 로그인 여부를 확인한다. 안 했으면 `LoginRequired` 컴포넌트를 보낸다.
2. `LoginRequired`가 `sessionStorage`에 `"/write"`를 저장하고 `/oauth2/authorization/accounts`로 이동한다.
3. 로그인이 끝나면 Spring이 첫 화면(`/`)으로 보낸다.
4. 첫 화면의 `ReturnAfterLogin`이 저장된 주소를 꺼내 `/write`로 이동한다. `/`로 시작하는 이 사이트 안의 주소만 받는다(`//evil.com` 같은 값 거부).

> **sessionStorage란?**
> 브라우저 탭마다 따로 있는 저장 공간이다. 탭을 닫으면 지워진다. 같은 탭에서 accounts에 다녀오는 동안 값을 들고 있기에 알맞다.

### 프로토타입을 그대로 옮긴 방법
| 프로토타입 | 구현 |
|---|---|
| 라이트·다크 2장씩 | CSS 변수를 `@media (prefers-color-scheme: dark)`에서 바꿔 끼운다. 프로토타입에 테마 전환 버튼이 없어서 운영체제 설정을 따른다 |
| PC(1440)·모바일(390) | 1200px 이상은 PC 배치. 그 아래는 모바일 배치(헤더 아이콘, 가로로 미는 탭, 한 줄 목록). PC에만 있는 요소(`only-desktop`)와 모바일에만 있는 요소(`only-mobile`)를 CSS로 고른다 |
| 카테고리 탭, 카드, "묵상 글 보기" | `/?category=daily` 같은 링크. 서버가 그 카테고리의 글만 그린다. 주소를 공유하면 같은 목록이 보인다 |
| 글쓰기의 비활성 기능 | `disabled`와 흐린 표시, "(준비 중)" |
| 마이페이지, 회원가입 | accounts의 `/account`, `/signup` 링크 |
| 푸터 글자 로고 | 라이트는 밝은 로고, 다크는 기본 로고 (프로토타입 그대로) |

> **`prefers-color-scheme`이란?**
> 사용자가 운영체제에서 라이트·다크 중 무엇을 골랐는지 알려 주는 CSS 미디어 쿼리다.

## 5. 3-5: Thymeleaf 제거
새 화면이 동작하는 것을 확인한 뒤 지웠다. `templates/`, `static/js/article.js`, `BlogViewController`, 화면용 DTO 2개, Thymeleaf 의존성 2개. 보안 설정에서는 `/new-article` 규칙과 정적 파일 제외 규칙을 지웠다. 화면을 확인하던 테스트 3개(로그인이 필요한 화면, `/` → `/articles`, 글 목록의 로그인 상태)도 지웠다.

## 6. 확인

### 자동 테스트
| 프로젝트 | 결과 |
|---|---|
| blog backend | 24개 통과 (3-1에서 5개 추가, 3-2에서 2개 추가, 3-5에서 화면 테스트 3개 삭제) |
| accounts backend | 37개 통과 (3-2의 설정 변경 후) |
| blog frontend | `tsc`, `eslint`, `next build` 통과 |

3-1에서 더한 테스트: 로그인 없이 읽기(`editable=false`), 작성자·다른 회원의 `editable`, 카테고리 필터와 `summary`·`excerpt`, 120자에서 자른 요약, `/api/me`(로그인 전 401, 후 닉네임).
3-2에서 더한 테스트: `X-Forwarded-Host: localhost:3000`이면 redirect_uri와 로그아웃 후 주소가 3000.

### 네 서버를 띄운 확인
accounts(9000, 3001), blog(8080, 3000)를 띄우고 `curl`로 **3000으로만** 요청했다. 쿠키 저장 파일은 하나만 썼다.

| 확인 | 결과 |
|---|---|
| 로그인 전 홈 | "최근 글", 예시 글, 로그인 링크, 회원가입 → `localhost:3001/signup` |
| `/?category=study` | 제목 "학습", 학습 글만 |
| 남의 글 상세 | 수정 버튼 없음 |
| 없는 글 `/articles/999` | 404, "글을 찾을 수 없어요" |
| 로그인 시작 | `redirect_uri=http://localhost:3000/login/oauth2/code/accounts` |
| accounts 로그인 → 토큰 교환 | `http://localhost:3000/`, 홈에 닉네임 |
| 글 작성 (`POST /api/articles`, 3000을 거침) | 201, `editable: true`, `excerpt`는 줄바꿈을 한 칸으로 |
| 내 글 상세 | 수정 버튼, 카테고리, 작성자 |
| 수정 화면 | 기존 제목이 채워짐 |
| 남의 글 수정 화면 | 307 → 글 상세 |
| 수정 → 학습 목록 | 고친 제목이 학습 목록에 보임 |
| 남의 글 삭제 | 403 |
| 내 글 삭제 → 상세 | 204 → 404 |
| 틀린 CSRF 토큰 | 403 |
| 로그아웃 | `post_logout_redirect_uri=http://localhost:3000/` → accounts → 3000. accounts `/api/me` 401 |

**아직 하지 않은 확인:** 실제 브라우저로 화면 보기(PC·모바일, 라이트·다크)와, 로그인 후 원래 화면으로 돌아오기(`sessionStorage`는 JavaScript가 실행되어야 해서 `curl`로는 확인할 수 없다).

## 7. 트러블슈팅

### 1. 프로토타입의 기능 대부분이 백엔드에 없었다
- **문제**: blog 프로토타입의 글쓰기 화면에는 카테고리, 요약, 태그, 공개 범위, 임시저장, 미리보기, 사진, 댓글 허용, 예약 게시가 있고, 홈에는 검색이 있다. 백엔드의 글은 제목, 본문, 작성자뿐이었다. 마이페이지의 개인정보 수정은 2단계에서 accounts가 맡기로 한 일이었다.
- **해결**: 사용자와 범위를 정했다([3단계 계획](./08_step3_blog_frontend_plan.md) 4장). 화면은 프로토타입 그대로 두고, 카테고리와 요약만 백엔드에 더하고, 나머지는 프로토타입에서 먼저 "준비 중"으로 바꾼 뒤 그대로 구현했다. 마이페이지는 accounts 내 계정으로 연결했다.

### 2. 로그인 후 글쓰기 화면이 아니라 첫 화면으로 돌아오게 된다 (설계하면서 막은 문제)
- **문제**: 그대로 두면 글쓰기를 누르고 로그인을 마친 사람이 첫 화면으로 돌아온다. 실행해서 겪은 것이 아니라, 흐름을 설계하면서 찾은 문제다.
- **원인**: Spring의 RequestCache는 Spring이 받은 요청만 기억한다. `/write`는 Next.js가 그리는 화면이라 Spring은 이 주소를 모른다.
- **해결**: 화면이 `sessionStorage`에 돌아올 주소를 저장하고, 로그인 후 첫 화면에서 꺼내 이동한다(4장).

### 3. 커밋에 다른 단계의 삭제가 섞였다
- **문제**: frontend 커밋을 만들었더니 Thymeleaf 파일 삭제까지 함께 들어갔다.
- **원인**: 3-5를 위해 `git rm`으로 지운 파일은 이미 **스테이징**되어 있었다. 그 상태에서 `git add frontend` 후 `git commit`을 하면, 스테이징된 모든 변경이 함께 커밋된다.
  > **스테이징이란?**
  > 다음 커밋에 넣을 변경을 골라 두는 단계다. `git add`와 `git rm`이 변경을 스테이징하고, `git commit`은 스테이징된 것을 모두 커밋한다. `git add 폴더`가 "이 폴더만 커밋"이라는 뜻은 아니다.
- **해결**: 아직 push하지 않은 두 커밋을 `git reset --soft`로 되돌려(파일은 그대로) frontend만, backend만 담아 다시 커밋했다. 이후로는 커밋 전에 `git status`로 스테이징된 파일을 확인한다.

### 4. 확인 스크립트가 틀린 실패를 냈다
`curl` 확인 중에 실제 문제가 아닌 실패가 두 번 나왔다.
| 증상 | 원인 | 해결 |
|---|---|---|
| `no matches found: http://localhost:3000/?category=study` | zsh가 주소의 `?`를 파일 이름 패턴으로 해석했다 | 주소를 따옴표로 감싼다 |
| 글 작성 응답을 JSON으로 읽지 못하고, 이어진 수정·삭제가 308 | 응답의 `\n`을 `echo`가 실제 줄바꿈으로 바꿔 JSON이 깨졌고, 그 결과 글 id가 빈 값이 되어 `/api/articles/`로 요청했다 | 응답을 파일로 저장해 그대로 읽는다 |

## 8. 남은 것
| 남은 것 | 비고 |
|---|---|
| 브라우저로 화면 확인 | PC·모바일, 라이트·다크, 로그인 후 돌아오기 |
| 준비 중인 기능 | 태그, 공개 범위, 임시저장, 미리보기, 사진(S3), 댓글, 예약 게시, 검색, Markdown, 목록 페이지 나누기 |
| 소개 문구, 연락처 | 프로토타입의 자리 표시 그대로 |
| 배포 준비 | [로드맵](./01_roadmap.md) 4장 |
