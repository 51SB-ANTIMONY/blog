# blog frontend

블로그 화면(Next.js). 홈, 목록(전체 기록, 카테고리, 기도편지), 검색, 글 상세, 글 쓰기·수정을 그린다.
글 API, 로그인 처리, 세션은 `backend`(Spring, 8080)가 맡고, 이 앱의 `proxy.ts`가 요청을 넘긴다.
로그인은 accounts(`localhost:3001`)에서 한다.

```bash
npm install
npm run dev      # http://localhost:3000
```

| 환경 변수 | 기본값 | 용도 |
|---|---|---|
| `BLOG_BACKEND_URL` | `http://localhost:8080` | blog backend 주소 (proxy, 서버에서 그릴 때 조회) |
| `NEXT_PUBLIC_ACCOUNTS_URL` | `http://localhost:3001` | 마이페이지(accounts 내 계정) 링크 |

## 주소

| 주소 | 화면 |
|---|---|
| `/` | 홈 (소식 2개, 최근 일상 글, 최근 일상·기도편지 3개) |
| `/articles` | 전체 기록 (`?sort=oldest`, `?page=2`) |
| `/articles?category=news` | 카테고리 목록 (`news`, `daily`, `study`) |
| `/articles?category=prayer&year=2025` | 기도편지 (연도 탭) |
| `/articles/search?q=가을&category=daily` | 검색 (제목과 본문) |
| `/articles/3` | 글 상세 |
| `/write`, `/articles/3/edit` | 글 쓰기, 수정 |

예전 홈 주소(`/?category=news` 등)는 `/articles`로 넘겨준다.

## 글 사진

사진 업로드 기능은 없다. 사진 파일을 `public/images/articles/`에 넣고, `lib/photos.ts`에 글 id와 파일 주소를 적는다.

```ts
const PHOTOS: Record<number, string> = {
  3: "/images/articles/3.jpg",
};
```

## 그 밖에

- 디자인 원본: Antimony Blog New Design
- 본문은 Markdown으로 쓴다. 줄바꿈 한 번도 그대로 보인다(`remark-breaks`).
- 테마(라이트/다크)와 언어(KO/EN)는 antimony.kr과 같은 방식이다. 처음에는 운영체제 테마와 한국어, 고르면 브라우저에 저장한다(`antimony-theme`, `antimony-lang`). 영어를 골라도 글 내용은 한국어 그대로다.
- 날짜와 시각, "올해", 소식의 NEW(7일)는 모두 한국 시간 기준이다.
- 상단바는 1180px, 본문은 720px보다 좁으면 좁은 화면 배치로 바뀐다.
