---
title: accounts 분리하기 - 3단계 blog 화면을 Next.js로 (목적과 계획)
date: 2026-10-03
tags: [nextjs, bff, ssr, csrf, frontend]
---

# accounts 분리하기 - 3단계 blog 화면을 Next.js로 (목적과 계획)

> 전체 단계는 [로드맵](./01_roadmap.md)에 있다.
> 아직 시작 전이다. 4장의 "정할 것"을 정한 뒤 시작한다.

## 1. 목적
**blog의 임시 Thymeleaf 화면을 Next.js 화면으로 바꾼다.** 로그인 방식은 2단계에서 만든 그대로 둔다. 2단계가 "로그인을 누가 처리하나"를 바꿨다면, 3단계는 "화면을 누가 그리나"를 바꾼다.

| 목적 | 내용 |
|---|---|
| 화면 교체 | 글 목록, 글 상세, 글 쓰기·수정을 Next.js로 만든다. PC·모바일, 라이트·다크 |
| BFF 유지 | 토큰은 계속 blog backend(Spring)의 세션에 둔다. Next.js는 화면만 그리고 로그인 정보를 다루지 않는다 |
| 같은 주소 | 화면과 API를 같은 주소(3000)로 맞춰, 세션 쿠키와 CSRF 쿠키를 지금처럼 쓰고 CORS 설정을 피한다 |
| 검색 노출 | 공개 글은 서버에서 그려서(SSR) 보낸다 |

> **CORS란?**
> 브라우저가 한 주소(예: 3000)에서 연 화면의 JavaScript가 다른 주소(예: 8080)로 요청할 때, 그 서버가 허락했는지 확인하는 규칙이다. 화면과 API를 같은 주소로 맞추면 이 확인이 필요 없다.

> **서버에서 그린다(SSR)는 것은?**
> Next.js 서버가 Spring에서 글을 받아 완성된 HTML을 만들어 보내는 방식이다. 브라우저가 빈 화면을 받은 뒤 JavaScript로 글을 불러오는 방식과 달리, 검색 엔진이 처음 받은 HTML에서 바로 글 내용을 읽을 수 있다.

### 바뀐 뒤의 구조
accounts(1-7)와 같은 구조다.
```
브라우저 ── localhost:3000  blog frontend (Next.js)   화면
                 │ rewrites: /api/**, /oauth2/**, /login/oauth2/**, /logout
                 ▼
            localhost:8080  blog backend (Spring)     API, 로그인 처리, 세션
                 │ back-channel (2단계 그대로)
                 ▼
            accounts (9000 / 3001)
```

> **rewrites란?**
> Next.js 서버가 받은 요청을 다른 주소로 대신 보내고, 받은 응답을 그대로 브라우저에 돌려주는 설정이다. 브라우저의 주소창은 바뀌지 않는다.

## 2. 세부 단계
| 단계 | 목적 | 바뀌는 곳 |
|---|---|---|
| 3-0 | 화면 프로토타입을 먼저 만든다 | 디자인 아티팩트 |
| 3-1 | 화면에 필요한 API를 보완한다 | blog backend |
| 3-2 | blog backend를 Next.js 뒤에 둔다 | blog backend, accounts 설정 |
| 3-3 | Next.js 뼈대 | blog frontend |
| 3-4 | 화면 구현 | blog frontend |
| 3-5 | Thymeleaf 제거 | blog backend |
| 3-6 | 자동 테스트와 네 서버를 띄운 실제 흐름 확인 | |
| 3-7 | README, 노트 | 문서 |

## 3. 수행 방안

### 3-0. 화면 프로토타입
**왜:** 화면을 코드에서 즉흥적으로 정하지 않고, 확인받은 디자인을 그대로 구현하려고. accounts 화면(1-7)과 같은 방법이다.

**어떻게:**
- 디자인 아티팩트에 화면을 만든다. 각 화면을 PC·모바일 × 라이트·다크로.

| 화면 | 들어갈 것 |
|---|---|
| 공통 헤더 | 로고, 로그인 버튼 / 닉네임과 로그아웃, 테마 전환 |
| 글 목록 | 제목, 요약, 작성자, 작성일. 글이 없을 때 안내. 로그인했으면 글쓰기 버튼 |
| 글 상세 | 제목, 작성자, 작성·수정 시각, 본문. 내가 쓴 글이면 수정·삭제 버튼 |
| 글 쓰기·수정 | 제목, 본문 입력. 입력값 오류(제목 없음, 255자 초과, 내용 없음) |
| 안내 화면 | 없는 글(404), 권한 없음(403), 삭제 확인 |

- 확인받은 뒤 3-4에서 그대로 구현한다.

### 3-1. API 보완
| 할 일 | 방법 |
|---|---|
| 로그인 상태 | `GET /api/me`: 로그인했으면 `{nickname}`, 아니면 401. 헤더 표시용 |
| 내가 쓴 글인지 | 글 응답에 `editable`(로그인 사용자가 작성자인가)을 넣는다. 수정·삭제 버튼 표시용. 실제 권한 검사는 지금처럼 서버가 다시 한다 |
| 읽기 공개 (정할 것 1) | `GET /api/articles`, `GET /api/articles/{id}`를 로그인 없이 허용. 쓰기·수정·삭제만 로그인 |
| 테스트 | 위 API의 로그인 전·후 응답 |

### 3-2. blog backend를 Next.js 뒤에 두기
**왜:** 브라우저가 3000만 보게 되면, Spring이 만드는 주소(로그인 후 돌아올 redirect_uri, 로그아웃 후 돌아올 주소, 리다이렉트 `Location`)도 3000이어야 한다.

| 할 일 | 방법 |
|---|---|
| 자기 주소 알기 | `server.forward-headers-strategy: framework`. Next.js가 붙이는 `X-Forwarded-Host: localhost:3000`을 보고 Spring이 자기 주소를 3000으로 여긴다 (accounts 1-7과 같은 방법) |
| 로그인 후 이동 | `defaultSuccessUrl`을 화면의 글 목록(`/`)으로 |
| accounts 등록 정보 | blog의 redirect_uri `http://localhost:3000/login/oauth2/code/accounts`, 로그아웃 후 `http://localhost:3000/`. accounts 설정을 바꾸는 **별도 커밋**(accounts 안의 파일만) |
| 확인 | 권한 요청의 `redirect_uri`, 로그아웃의 `post_logout_redirect_uri`가 3000인지 테스트 |

> **`X-Forwarded-Host`란?**
> 요청을 대신 전달하는 서버(프록시)가 "원래 브라우저가 요청한 호스트는 이것이었다"고 알려 주는 헤더다. Spring은 `forward-headers-strategy` 설정이 있을 때만 이 헤더를 믿는다.

### 3-3. Next.js 뼈대
| 할 일 | 방법 |
|---|---|
| 프로젝트 | `blog/frontend`. Next.js 16, TypeScript, App Router. 포트 3000 |
| rewrites | `/api/**`, `/oauth2/**`, `/login/oauth2/**`, `/logout` → `http://localhost:8080` (환경 변수로 바꿀 수 있게) |
| API 호출 | 쓰기 요청에 `X-XSRF-TOKEN` 헤더. 쿠키가 없으면 `/api/csrf`. 401이면 `/oauth2/authorization/accounts`로 이동 (accounts frontend의 `lib/api.ts`와 같은 방식) |
| 레이아웃, 테마 | 공통 헤더, CSS 변수로 라이트·다크, 첫 화면 전에 테마 적용 |
| 확인 | `npm run lint`, `npm run build` |

### 3-4. 화면 구현
| 화면 | 그리는 곳 | 방법 |
|---|---|---|
| 글 목록, 글 상세 | 서버 (SSR) | Next.js 서버가 Spring의 공개 API를 직접 부른다. 로그인과 상관없는 내용이라 쿠키를 넘길 필요가 없다 |
| 로그인 상태, 수정·삭제 버튼 | 브라우저 | `/api/me`, 글 응답의 `editable`을 브라우저에서 부른다 |
| 글 쓰기·수정 | 브라우저 | 로그인이 필요하다. 401이면 accounts 로그인으로 |
| 삭제 | 브라우저 | 확인 후 `DELETE`, 목록으로 이동 |

### 3-5. Thymeleaf 제거
새 화면이 모두 동작한 뒤에 지운다. `templates/`, `static/js/article.js`, `BlogViewController`, 화면용 DTO(`ArticleListViewResponse`, `ArticleViewResponse`), Thymeleaf 의존성. 2단계의 `/`, `/articles` 관련 테스트는 API 기준으로 바꾼다.

### 3-6. 확인
| 확인할 것 | 방법 |
|---|---|
| 자동 테스트 | blog backend `./gradlew test`, frontend `npm run lint`, `npm run build` |
| 실제 흐름 | accounts 9000·3001, blog 8080·3000을 띄우고 3000으로만 요청 |
| 흐름 목록 | 로그인 전 목록·상세 보기 → 글쓰기 → accounts 로그인 → 돌아와서 작성 → 수정 → 삭제 → SSO → 로그아웃 |
| 반응형 | PC·모바일, 라이트·다크를 브라우저에서 확인 |

### 3-7. 기록
blog README(구조, 실행 방법), frontend README, 이 폴더의 3단계 구현 노트(목적·계획은 이 문서, 구현·확인·트러블슈팅은 새 문서).

## 4. 정할 것
| 질문 | 선택지 | 추천 |
|---|---|---|
| 1. 글 읽기를 공개할까 | 공개 / 지금처럼 로그인 필요 | **공개.** 블로그는 읽기는 누구나, 쓰기만 로그인이 자연스럽고, 서버 렌더링도 단순해진다 |
| 2. 디자인 기준 | accounts 프로토타입과 같은 색·글꼴로 새로 / ttblog.antimony.kr 디자인 참고 | **accounts와 같은 색·글꼴.** antimony.kr 서비스끼리 화면이 일관된다 |
| 3. 이번에 뺄 것 | Markdown 본문, 이미지 업로드(S3), 목록 페이지 나누기 | **모두 이후로 미룸.** 3단계는 화면 교체에 집중 |
