import { NextResponse, type NextRequest } from "next/server";

// 화면은 Next.js가, 로그인 처리·세션·글 API는 Spring(blog backend)이 맡는다.
// 브라우저는 이 주소(localhost:3000, blog.antimony.kr) 하나만 보고, 아래 matcher의 경로는 Spring으로 넘긴다.
//
// next.config의 rewrites 대신 여기서 넘기는 이유: Spring은 X-Forwarded-Host로 "사용자가 접속한 주소"를 알아
// 로그인 후 돌아올 주소(redirect_uri)와 리다이렉트 주소를 만든다. Vercel의 rewrites는 이 헤더를 넘기지 않아서, 직접 붙여 보낸다.
const backend = process.env.BLOG_BACKEND_URL ?? "http://localhost:8080";

export function proxy(request: NextRequest) {
  const { pathname, search, host, protocol } = request.nextUrl;
  const headers = new Headers(request.headers);
  headers.set("x-forwarded-host", request.headers.get("host") ?? host);
  headers.set("x-forwarded-proto", protocol.replace(":", ""));
  // 포트는 Host에 들어 있다(localhost:3001). 따로 붙은 포트(Next.js 서버의 포트)가 섞이지 않게 지운다.
  headers.delete("x-forwarded-port");
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
