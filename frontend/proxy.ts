import { NextResponse, type NextRequest } from "next/server";

// 화면은 Next.js가, 로그인 처리·세션·글 API는 Spring(blog backend)이 맡는다.
// 브라우저는 이 주소(localhost:3000, blog.antimony.kr) 하나만 보고, 아래 matcher의 경로는 Spring으로 넘긴다.
//
// Spring은 "사용자가 접속한 주소"를 알아야 리다이렉트 주소를 만든다. 그래서 넘길 때 표준 헤더 Forwarded(RFC 7239)에 담아 보낸다.
// 로컬에서는 이 값이 그대로 Spring에 닿는다. 운영(Vercel)에서는 Spring까지 전달되지 않아서,
// EC2의 Nginx(backend/.platform/nginx/conf.d/https.conf)가 공개 주소로 고정한다.
const backend = process.env.BLOG_BACKEND_URL ?? "http://localhost:8080";

export function proxy(request: NextRequest) {
  const { pathname, search, host, protocol } = request.nextUrl;
  const headers = new Headers(request.headers);
  const originalHost = request.headers.get("host") ?? host; // accounts.antimony.kr, 로컬은 localhost:3001
  headers.set("forwarded", `host="${originalHost}";proto=${protocol.replace(":", "")}`);
  return NextResponse.rewrite(new URL(pathname + search, backend), { request: { headers } });
}

export const config = {
  matcher: [
    "/api/:path*", // 글 API, 로그인 상태, CSRF 토큰
    "/oauth2/:path*", // accounts 로그인 시작
    "/login/oauth2/:path*", // accounts 로그인 후 돌아오는 주소
    "/logout", // 로그아웃 (blog 세션 삭제 → accounts 로그아웃)
  ],
};
