// 가입 신청 후 인증 화면으로 넘길 값. 새로고침해도 남도록 sessionStorage에 둔다.
const KEY = "signup.pending";

export type PendingSignup = { email: string; expiresAt: number };

export function savePendingSignup(value: PendingSignup) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(value));
  } catch {
    // 저장하지 못하면 인증 화면에서 다시 가입하도록 안내한다.
  }
}

// useSyncExternalStore용: 같은 값이면 같은 문자열을 돌려줘야 하므로 JSON 문자열 그대로 읽는다.
export function readPendingSignupRaw(): string | null {
  try {
    return sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function parsePendingSignup(raw: string | null): PendingSignup | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as PendingSignup;
  } catch {
    return null;
  }
}

export function clearPendingSignup() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // 무시
  }
}
