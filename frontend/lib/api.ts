// 브라우저에서 blog backend(Spring) API를 부른다. 같은 주소(Next.js의 rewrites)로 보내므로 세션 쿠키가 함께 간다.
import { LOGIN_PATH } from "./links";
import type { Article, Category } from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly fieldErrors: Record<string, string>,
  ) {
    super(message);
  }
}

function readCookie(name: string): string | null {
  const found = document.cookie.split("; ").find((part) => part.startsWith(name + "="));
  return found ? decodeURIComponent(found.slice(name.length + 1)) : null;
}

// fetch 요청용 CSRF 값: XSRF-TOKEN 쿠키 값을 그대로 X-XSRF-TOKEN 헤더에 담는다.
async function csrfHeaderValue(): Promise<string> {
  let token = readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch("/api/csrf", { cache: "no-store" });
    token = readCookie("XSRF-TOKEN");
  }
  return token ?? "";
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (method !== "GET") headers["X-XSRF-TOKEN"] = await csrfHeaderValue();
  if (body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
    });
  } catch {
    throw new ApiError(0, "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.", {});
  }

  if (!response.ok) {
    let data: { message?: string; errors?: Record<string, string> } = {};
    try {
      data = await response.json();
    } catch {
      // 본문이 JSON이 아닌 오류 (예: 401, 403)
    }
    throw new ApiError(response.status, data.message ?? "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.", data.errors ?? {});
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export type ArticleInput = { title: string; content: string; category: Category; summary: string };

export const api = {
  create: (input: ArticleInput) => request<Article>("POST", "/api/articles", input),
  update: (id: number, input: ArticleInput) => request<Article>("PUT", `/api/articles/${id}`, input),
  remove: (id: number) => request<void>("DELETE", `/api/articles/${id}`),
};

// 로그인이 끝나면 돌아올 화면. Spring은 로그인 후 첫 화면(/)으로 보내므로, 거기서 이 값을 보고 이동한다.
const RETURN_KEY = "blog.returnTo";

export function startLogin(returnTo?: string) {
  try {
    if (returnTo) sessionStorage.setItem(RETURN_KEY, returnTo);
  } catch {
    // 저장하지 못하면 첫 화면에 머문다.
  }
  window.location.assign(new URL(LOGIN_PATH, window.location.origin).toString());
}

export function takeReturnTo(): string | null {
  try {
    const value = sessionStorage.getItem(RETURN_KEY);
    sessionStorage.removeItem(RETURN_KEY);
    // 이 사이트 안의 주소만 받는다.
    return value && value.startsWith("/") && !value.startsWith("//") ? value : null;
  } catch {
    return null;
  }
}

// 로그아웃: POST /logout 폼을 보낸다. Spring이 blog 세션을 지우고 accounts 로그아웃으로 보낸다.
export async function logout() {
  const response = await fetch("/api/csrf", { cache: "no-store" });
  const { token } = (await response.json()) as { token: string };
  const form = document.createElement("form");
  form.method = "post";
  form.action = "/logout";
  const input = document.createElement("input");
  input.type = "hidden";
  input.name = "_csrf";
  input.value = token;
  form.appendChild(input);
  document.body.appendChild(form);
  form.submit();
}
