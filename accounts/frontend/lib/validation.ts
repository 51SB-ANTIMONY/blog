// 프로토타입과 서버(SignupRequest)의 규칙을 맞춘 입력값 검사

export const UID_PATTERN = /^[a-z0-9]{4,16}$/;
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function emailError(value: string): string {
  const email = value.trim();
  if (!email) return "이메일을 입력해 주세요.";
  if (!EMAIL_PATTERN.test(email)) return "올바른 이메일 형식이 아니에요.";
  return "";
}

export function passwordChecks(password: string) {
  return [
    { label: "8자 이상", ok: password.length >= 8 },
    { label: "영문·숫자 포함", ok: /[A-Za-z]/.test(password) && /\d/.test(password) },
    { label: "특수문자 포함", ok: /[^A-Za-z0-9]/.test(password) },
  ];
}

export function passwordError(password: string, emptyMessage = "비밀번호를 입력해 주세요."): string {
  if (!password) return emptyMessage;
  if (passwordChecks(password).some((check) => !check.ok)) return "아래 조건을 모두 충족해 주세요.";
  return "";
}

export function passwordConfirmError(password: string, confirm: string): string {
  if (!confirm) return "비밀번호를 한 번 더 입력해 주세요.";
  if (confirm !== password) return "비밀번호가 일치하지 않아요.";
  return "";
}

// name@example.com → n***@example.com
export function maskEmail(value: string): string {
  const email = value.trim();
  const at = email.indexOf("@");
  return at > 0 ? email.slice(0, 1) + "***" + email.slice(at) : email;
}
