import type { NextConfig } from "next";

// 화면은 Next.js가, 로그인 처리·API·OIDC는 Spring(accounts backend)이 맡는다.
// 브라우저는 이 주소(localhost:3001) 하나만 보고, 아래 경로는 Spring으로 넘긴다.
const backend = process.env.ACCOUNTS_BACKEND_URL ?? "http://localhost:9000";

const backendPaths = [
  "/api/:path*", // 회원가입·계정 찾기·내 계정 API
  "/login/password", // 아이디·비밀번호 로그인 처리
  "/login/oauth2/:path*", // 소셜 로그인 콜백
  "/oauth2/:path*", // 소셜 로그인 시작, OIDC authorize·token·jwks 등
  "/logout", // accounts 로그아웃
  "/connect/:path*", // OIDC 로그아웃
  "/userinfo", // OIDC userinfo
  "/.well-known/:path*", // OIDC discovery
];

const nextConfig: NextConfig = {
  async rewrites() {
    return backendPaths.map((source) => ({
      source,
      destination: `${backend}${source}`,
    }));
  },
  async redirects() {
    return [{ source: "/", destination: "/account", permanent: false }];
  },
};

export default nextConfig;
