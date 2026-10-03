# blog frontend

블로그 화면(Next.js). 홈(카테고리별 글 목록), 글 상세, 글 쓰기·수정을 그린다.
글 API, 로그인 처리, 세션은 `backend`(Spring, 8080)가 맡고, 이 앱이 `next.config.ts`의 rewrites로 요청을 넘긴다.
로그인은 accounts(`localhost:3001`)에서 한다.

```bash
npm install
npm run dev      # http://localhost:3000
```

| 환경 변수 | 기본값 | 용도 |
|---|---|---|
| `BLOG_BACKEND_URL` | `http://localhost:8080` | blog backend 주소 (rewrites, 서버에서 그릴 때 조회) |
| `NEXT_PUBLIC_ACCOUNTS_URL` | `http://localhost:3001` | 회원가입, 마이페이지(accounts 내 계정) 링크 |

- 디자인 원본: ANTIMONY 블로그 프로토타입
- 테마는 운영체제 설정(라이트/다크)을 따른다. 화면 폭 1200px 이상은 PC 배치, 그 아래는 모바일 배치.
