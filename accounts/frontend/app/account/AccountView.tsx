"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import Banner from "@/components/Banner";
import { api, ApiError, type Me, navigateToServer, type Providers, submitForm } from "@/lib/api";

const PROVIDERS = [
  { id: "google", name: "Google" },
  { id: "kakao", name: "카카오" },
  { id: "naver", name: "네이버" },
] as const;

const LINK_ERRORS: Record<string, string> = {
  linked_to_another_user: "이 소셜 계정은 이미 다른 회원에 연결되어 있습니다.",
  email_already_registered: "이미 같은 이메일로 가입한 계정이 있습니다.",
};

// 소셜 계정 연결을 시작할 때 기록해 두고, 돌아왔을 때 "연결했어요" 안내를 보여 준다.
const LINKING_KEY = "account.linking";

function takeLinkingProvider(): string | null {
  try {
    const provider = sessionStorage.getItem(LINKING_KEY);
    sessionStorage.removeItem(LINKING_KEY);
    return provider;
  } catch {
    return null;
  }
}

export default function AccountView() {
  const params = useSearchParams();
  const router = useRouter();
  const [me, setMe] = useState<Me | null>(null);
  const [providers, setProviders] = useState<Providers | null>(null);
  const [notice, setNotice] = useState("");
  const [errorText, setErrorText] = useState(() => {
    const code = params.get("error");
    return code ? (LINK_ERRORS[code] ?? "소셜 계정을 연결하지 못했습니다.") : "";
  });
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.me().then(
      (result) => {
        setMe(result);
        const linking = takeLinkingProvider();
        const provider = PROVIDERS.find((p) => p.id === linking);
        if (provider && result.identities.some((identity) => identity.provider === provider.id)) {
          setNotice(`${provider.name} 계정을 연결했어요.`);
        }
      },
      (e) => {
        if (e instanceof ApiError && e.status === 401) router.replace("/login");
        else setErrorText(e instanceof ApiError ? e.message : "계정 정보를 불러오지 못했어요.");
      },
    );
    api.providers().then(setProviders, () => setProviders(null));
  }, [router]);

  const link = (provider: string) => {
    try {
      sessionStorage.setItem(LINKING_KEY, provider);
    } catch {
      // 안내만 빠진다.
    }
    navigateToServer(`/oauth2/authorization/${provider}`);
  };

  const unlink = async (provider: string, name: string) => {
    if (!me) return;
    setBusy(true);
    setNotice("");
    setErrorText("");
    try {
      await api.unlink(provider);
      setMe({ ...me, identities: me.identities.filter((identity) => identity.provider !== provider) });
      setNotice(`${name} 연결을 해제했어요.`);
    } catch (e) {
      setErrorText(e instanceof ApiError ? e.message : "연결을 해제하지 못했어요.");
    } finally {
      setBusy(false);
    }
  };

  const logout = async () => {
    setBusy(true);
    try {
      await submitForm("/logout", {});
    } catch {
      setBusy(false);
      setErrorText("서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  };

  return (
    <>
      <div className="stack-8">
        <h1 className="title">내 계정</h1>
        <p className="desc">ANTIMONY 계정 정보와 로그인 방식을 관리해요.</p>
      </div>
      {notice && <Banner kind="success">{notice}</Banner>}
      {errorText && <Banner kind="error">{errorText}</Banner>}
      {!me ? (
        <p className="loading">불러오는 중…</p>
      ) : (
        <>
          <section aria-labelledby="account-info" className="stack-10">
            <h2 id="account-info" className="section-label">
              계정 정보
            </h2>
            <dl className="info-list">
              <div>
                <dt>닉네임</dt>
                <dd>{me.nickname}</dd>
              </div>
              <div>
                <dt>아이디</dt>
                <dd>{me.uid}</dd>
              </div>
              <div>
                <dt>이메일</dt>
                <dd>{me.email ?? "등록 안 됨"}</dd>
              </div>
            </dl>
          </section>
          <section aria-labelledby="account-methods" className="stack-10">
            <h2 id="account-methods" className="section-label">
              로그인 방식
            </h2>
            <ul className="method-list">
              <li>
                <div className="method-name">
                  <span>아이디·비밀번호</span>
                  <span className={me.hasPassword ? "method-status is-on" : "method-status"}>{me.hasPassword ? "사용 중" : "설정 안 됨"}</span>
                </div>
                <Link href="/reset-password" className="row-btn">
                  {me.hasPassword ? "비밀번호 변경" : "비밀번호 설정"}
                </Link>
              </li>
              {PROVIDERS.map((provider) => {
                const available = providers?.[provider.id] === true;
                const linked = me.identities.some((identity) => identity.provider === provider.id);
                return (
                  <li key={provider.id}>
                    <div className="method-name">
                      <span>{provider.name}</span>
                      <span className={linked ? "method-status is-on" : "method-status"}>{linked ? "연결됨" : available ? "연결 안 됨" : "준비 중"}</span>
                    </div>
                    {linked ? (
                      <button type="button" className="row-btn" onClick={() => unlink(provider.id, provider.name)} disabled={busy}>
                        연결 해제
                      </button>
                    ) : available ? (
                      <button type="button" className="row-btn" onClick={() => link(provider.id)} disabled={busy}>
                        연결하기
                      </button>
                    ) : (
                      <button type="button" className="row-btn" disabled aria-disabled="true">
                        준비 중
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
            <p className="hint">연결한 계정으로도 같은 ANTIMONY 계정에 로그인할 수 있어요.</p>
          </section>
          <button type="button" className="btn-ghost is-large" onClick={logout} disabled={busy}>
            로그아웃
          </button>
        </>
      )}
    </>
  );
}
