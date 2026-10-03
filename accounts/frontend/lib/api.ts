// accounts backend(Spring) API 호출. 같은 주소(Next.js의 rewrites)로 보내므로 쿠키가 그대로 함께 간다.

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

// 폼 전송용 CSRF 값: /api/csrf가 돌려준 token을 _csrf 입력칸에 담는다.
export async function csrfFormValue(): Promise<string> {
  const response = await fetch("/api/csrf", { cache: "no-store" });
  const body = (await response.json()) as { token: string };
  return body.token;
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
      // 본문이 JSON이 아닌 오류 (예: 403)
    }
    throw new ApiError(response.status, data.message ?? "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.", data.errors ?? {});
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export type Providers = Record<"google" | "kakao" | "naver", boolean>;

export type Me = {
  id: number;
  uid: string;
  email: string | null;
  nickname: string;
  hasPassword: boolean;
  identities: { provider: string; linkedAt: string }[];
};

export const api = {
  providers: () => request<Providers>("GET", "/api/auth/providers"),
  me: () => request<Me>("GET", "/api/me"),
  unlink: (provider: string) => request<void>("DELETE", `/api/me/identities/${provider}`),

  checkUid: (uid: string, email: string) =>
    request<{ status: "ok" | "invalid" | "taken"; message: string }>(
      "GET",
      `/api/signup/uid-availability?${new URLSearchParams({ uid, email })}`,
    ),
  signup: (body: {
    uid: string;
    password: string;
    nickname: string;
    email: string;
    termsAgreed: boolean;
    privacyAgreed: boolean;
    marketingAgreed: boolean;
  }) => request<{ email: string; expiresIn: number }>("POST", "/api/signup", body),
  verifySignup: (email: string, code: string) => request<unknown>("POST", "/api/signup/verify", { email, code }),
  resendSignup: (email: string) => request<{ expiresIn: number }>("POST", "/api/signup/resend", { email }),

  sendFindIdCode: (email: string) => request<{ expiresIn: number }>("POST", "/api/find-id/code", { email }),
  verifyFindId: (email: string, code: string) =>
    request<{ maskedUid: string }>("POST", "/api/find-id/verify", { email, code }),

  sendResetCode: (uid: string, email: string) =>
    request<{ expiresIn: number }>("POST", "/api/password-reset/code", { uid, email }),
  verifyReset: (uid: string, email: string, code: string) =>
    request<{ resetToken: string }>("POST", "/api/password-reset/verify", { uid, email, code }),
  completeReset: (resetToken: string, password: string) =>
    request<void>("POST", "/api/password-reset/complete", { resetToken, password }),
};

// 화면을 떠나 서버로 폼을 보낸다. (로그인·로그아웃처럼 서버가 다음 화면으로 이동시키는 요청)
export async function submitForm(action: string, fields: Record<string, string>) {
  const form = document.createElement("form");
  form.method = "post";
  form.action = action;
  const all = { ...fields, _csrf: await csrfFormValue() };
  for (const [name, value] of Object.entries(all)) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }
  document.body.appendChild(form);
  form.submit();
}

// Spring이 처리하는 주소(소셜 로그인 시작 등)로 화면 전체를 이동한다. Next.js 화면이 아니므로 router를 쓰지 않는다.
export function navigateToServer(path: string) {
  window.location.assign(new URL(path, window.location.origin).toString());
}
