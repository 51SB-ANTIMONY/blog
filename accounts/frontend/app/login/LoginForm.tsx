"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import Banner from "@/components/Banner";
import PasswordInput from "@/components/PasswordInput";
import { api, navigateToServer, type Providers, submitForm } from "@/lib/api";

const SOCIAL = [
  { id: "google", name: "Google", label: "Google로 로그인" },
  { id: "kakao", name: "카카오", label: "카카오로 로그인" },
  { id: "naver", name: "네이버", label: "네이버로 로그인" },
] as const;

const SOCIAL_ERRORS: Record<string, string> = {
  email_already_registered: "이미 같은 이메일로 가입한 계정이 있습니다. 기존 계정으로 로그인한 뒤 소셜 계정을 연결해 주세요.",
  linked_to_another_user: "이 소셜 계정은 이미 다른 회원에 연결되어 있습니다.",
};

// 로그인 실패로 돌아왔을 때 아이디를 다시 채우기 위해 잠깐 보관한다.
const UID_KEY = "login.uid";

function initialError(params: URLSearchParams): string {
  if (params.has("error")) return "아이디 또는 비밀번호를 확인해 주세요.";
  const social = params.get("social_error");
  if (social) return SOCIAL_ERRORS[social] ?? "소셜 로그인에 실패했습니다.";
  return "";
}

function savedUid(params: URLSearchParams): string {
  if (!params.has("error")) return "";
  try {
    return sessionStorage.getItem(UID_KEY) ?? "";
  } catch {
    return "";
  }
}

export default function LoginForm() {
  const params = useSearchParams();
  const router = useRouter();
  const [uid, setUid] = useState(() => savedUid(params));
  const [pw, setPw] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [remember, setRemember] = useState(true);
  const [tried, setTried] = useState(false);
  const [errorText, setErrorText] = useState(() => initialError(params));
  const [loggedOut, setLoggedOut] = useState(() => params.has("logout"));
  const [submitting, setSubmitting] = useState(false);
  const [providers, setProviders] = useState<Providers | null>(null);

  useEffect(() => {
    api.providers().then(setProviders, () => setProviders(null));
  }, []);

  // 이미 로그인한 상태에서 소셜 계정 연결이 실패하면 서버가 이 화면으로 보낸다. 그때는 내 계정 화면에서 알려 준다.
  useEffect(() => {
    const social = params.get("social_error");
    if (!social) return;
    api.me().then(
      () => router.replace(`/account?error=${encodeURIComponent(social)}`),
      () => {},
    );
  }, [params, router]);

  const uidErrText = !uid.trim() ? "아이디를 입력해 주세요." : "";
  const pwErrText = !pw ? "비밀번호를 입력해 주세요." : "";
  const uidErr = tried && !!uidErrText;
  const pwErr = tried && !!pwErrText;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setTried(true);
    setErrorText("");
    setLoggedOut(false);
    if (uidErrText || pwErrText) return;
    setSubmitting(true);
    try {
      sessionStorage.setItem(UID_KEY, uid.trim());
    } catch {
      // 보관하지 못해도 로그인은 진행한다.
    }
    try {
      await submitForm("/login/password", {
        username: uid.trim(),
        password: pw,
        ...(remember ? { "remember-me": "on" } : {}),
      });
    } catch {
      setSubmitting(false);
      setErrorText("서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  };

  const socialLogin = (provider: (typeof SOCIAL)[number]["id"]) => {
    if (!providers?.[provider]) {
      setLoggedOut(false);
      setErrorText("준비 중인 로그인 방식이에요.");
      return;
    }
    navigateToServer(`/oauth2/authorization/${provider}`);
  };

  return (
    <>
      <div className="stack-8">
        <h1 className="title">로그인</h1>
        <p className="desc">ANTIMONY 아이디로 로그인하세요.</p>
      </div>
      {errorText && <Banner kind="error">{errorText}</Banner>}
      {loggedOut && <Banner kind="success">로그아웃되었습니다.</Banner>}
      <form className="stack-28" onSubmit={submit} noValidate>
        <div className="stack-18">
          <div className="stack-8">
            <div className="label-row">
              <label htmlFor="login-id" className="label">
                아이디
              </label>
              <Link href="/find-id" className="small-link">
                아이디 찾기
              </Link>
            </div>
            <input
              id="login-id"
              className="input"
              type="text"
              autoComplete="username"
              placeholder="아이디 입력"
              value={uid}
              onChange={(e) => setUid(e.target.value)}
              aria-invalid={uidErr}
            />
            {uidErr && (
              <p role="alert" className="field-error">
                {uidErrText}
              </p>
            )}
          </div>
          <div className="stack-8">
            <div className="label-row">
              <label htmlFor="login-pw" className="label">
                비밀번호
              </label>
              <Link href="/reset-password" className="small-link">
                비밀번호 찾기
              </Link>
            </div>
            <PasswordInput
              id="login-pw"
              value={pw}
              onChange={setPw}
              shown={showPw}
              onToggle={() => setShowPw(!showPw)}
              invalid={pwErr}
              autoComplete="current-password"
              placeholder="비밀번호 입력"
            />
            {pwErr && (
              <p role="alert" className="field-error">
                {pwErrText}
              </p>
            )}
          </div>
          <label className="checkbox-row remember">
            <input type="checkbox" checked={remember} onChange={() => setRemember(!remember)} />
            로그인 상태 유지
          </label>
        </div>
        <div className="stack-20">
          <button type="submit" className="btn-primary" disabled={submitting}>
            로그인
          </button>
          <div className="divider">
            <span>또는</span>
          </div>
          <div className="stack-12">
            <p className="social-title">간편 로그인</p>
            <div className="social-grid">
              {SOCIAL.map((provider) => (
                <button key={provider.id} type="button" className="btn-ghost" aria-label={provider.label} onClick={() => socialLogin(provider.id)}>
                  {provider.name}
                </button>
              ))}
            </div>
          </div>
        </div>
      </form>
      <p className="footer-text">
        아직 계정이 없으신가요? <Link href="/signup">회원가입</Link>
      </p>
    </>
  );
}
