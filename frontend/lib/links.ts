// accounts 주소. 첫 화면(/)은 로그인했으면 내 계정, 아니면 로그인 화면으로 보낸다.
// 환경 변수가 없으면 운영은 accounts.antimony.kr, 로컬은 localhost:3001
export const ACCOUNTS_URL =
  process.env.NEXT_PUBLIC_ACCOUNTS_URL ?? (process.env.NODE_ENV === "production" ? "https://accounts.antimony.kr" : "http://localhost:3001");

// blog backend가 accounts 로그인을 시작하는 주소 (proxy.ts가 Spring에 넘긴다)
export const LOGIN_PATH = "/oauth2/authorization/accounts";

// 목록 주소. 전체 기록, 카테고리 목록, 기도편지 모두 /articles 아래에 둔다. 기본값은 주소에서 뺀다.
//   /articles                             전체 기록 (?sort=oldest&page=2)
//   /articles?category=daily              카테고리 목록 (news, daily, study)
//   /articles?category=prayer&year=2025   기도편지
export type ListQuery = { category?: string; year?: string; sort?: "oldest"; page?: number };

export function articlesHref(query: ListQuery = {}): string {
  const params = new URLSearchParams();
  if (query.category) params.set("category", query.category);
  if (query.year) params.set("year", query.year);
  if (query.sort) params.set("sort", query.sort);
  if (query.page && query.page > 1) params.set("page", String(query.page));
  const search = params.toString();
  return search ? `/articles?${search}` : "/articles";
}

// 검색 주소: /articles/search?q=가을&category=daily
export function searchHref(q?: string, category?: string): string {
  const params = new URLSearchParams();
  if (q) params.set("q", q);
  if (category) params.set("category", category);
  const search = params.toString();
  return search ? `/articles/search?${search}` : "/articles/search";
}
