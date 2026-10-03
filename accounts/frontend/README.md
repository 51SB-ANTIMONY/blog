# accounts frontend

`accounts.antimony.kr`의 화면(Next.js). 로그인, 회원가입, 이메일 인증, 아이디·비밀번호 찾기, 내 계정 화면을 그린다.
로그인 처리·API·OIDC는 `accounts/backend`(Spring, 9000번 포트)가 맡고, 이 앱이 `next.config.ts`의 rewrites로 요청을 넘긴다.

```bash
npm install
npm run dev      # http://localhost:3001
```

- 백엔드 주소를 바꾸려면 `ACCOUNTS_BACKEND_URL` 환경 변수를 설정한다. (기본값 `http://localhost:9000`)
- 디자인 원본: ANTIMONY 로그인 · 회원가입 프로토타입
