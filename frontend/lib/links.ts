// accounts 화면 주소 (회원가입, 내 계정)
export const ACCOUNTS_URL = process.env.NEXT_PUBLIC_ACCOUNTS_URL ?? "http://localhost:3001";

// blog backend가 accounts 로그인을 시작하는 주소 (rewrites로 Spring에 간다)
export const LOGIN_PATH = "/oauth2/authorization/accounts";
