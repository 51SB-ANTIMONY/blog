import type { NextConfig } from "next";

// 화면은 Next.js가, 로그인 처리·세션·글 API는 Spring(blog backend)이 맡는다.
// 브라우저는 이 주소(localhost:3000) 하나만 보고, 아래 경로는 Spring으로 넘긴다.
const backend = process.env.BLOG_BACKEND_URL ?? "http://localhost:8080";

const backendPaths = [
  "/api/:path*", // 글 API, 로그인 상태, CSRF 토큰
  "/oauth2/:path*", // accounts 로그인 시작
  "/login/oauth2/:path*", // accounts 로그인 후 돌아오는 주소
  "/logout", // 로그아웃 (blog 세션 삭제 → accounts 로그아웃)
];

const nextConfig: NextConfig = {
  async rewrites() {
    return backendPaths.map((source) => ({
      source,
      destination: `${backend}${source}`,
    }));
  },
};

export default nextConfig;
