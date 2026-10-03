import "server-only";
import { cookies } from "next/headers";
import type { Article, Category, Me } from "./types";

// 서버에서 화면을 그릴 때 Spring을 직접 부른다. 브라우저의 쿠키(BLOG_SESSION)를 함께 넘겨,
// 로그인한 사용자라면 그 사람 기준의 응답(editable, /api/me)을 받는다.
const backend = process.env.BLOG_BACKEND_URL ?? "http://localhost:8080";

async function backendFetch(path: string): Promise<Response> {
  const cookieHeader = (await cookies()).toString();
  return fetch(`${backend}${path}`, {
    headers: { Accept: "application/json", ...(cookieHeader ? { Cookie: cookieHeader } : {}) },
    cache: "no-store",
  });
}

// 로그인하지 않았으면 null
export async function fetchMe(): Promise<Me | null> {
  const response = await backendFetch("/api/me");
  if (!response.ok) return null;
  return (await response.json()) as Me;
}

export async function fetchArticles(category?: Category): Promise<Article[]> {
  const response = await backendFetch(category ? `/api/articles?category=${category}` : "/api/articles");
  if (!response.ok) throw new Error(`글 목록을 불러오지 못했습니다 (${response.status})`);
  return (await response.json()) as Article[];
}

// 없는 글이면 null
export async function fetchArticle(id: string): Promise<Article | null> {
  if (!/^\d+$/.test(id)) return null;
  const response = await backendFetch(`/api/articles/${id}`);
  if (response.status === 404) return null;
  if (!response.ok) throw new Error(`글을 불러오지 못했습니다 (${response.status})`);
  return (await response.json()) as Article;
}
