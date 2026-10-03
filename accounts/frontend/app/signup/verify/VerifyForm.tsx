"use client";

import Link from "next/link";
import { useMemo, useState, useSyncExternalStore } from "react";
import Banner from "@/components/Banner";
import CodeField, { EMPTY_CODE_MESSAGE, EXPIRED_MESSAGE } from "@/components/CodeField";
import { BigCheckIcon, MailIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import { clearPendingSignup, parsePendingSignup, readPendingSignupRaw, savePendingSignup } from "@/lib/signupSession";
import { useCountdown } from "@/lib/useCountdown";

const CODE_SECONDS = 180;
const noSubscribe = () => () => {};

export default function VerifyForm() {
  // 서버에서 그릴 때는 undefined(아직 모름), 브라우저에서는 저장된 가입 신청 값
  const raw = useSyncExternalStore(noSubscribe, readPendingSignupRaw, () => undefined);
  const pending = useMemo(() => (raw === undefined ? undefined : parsePendingSignup(raw)), [raw]);

  const [resentExpiresAt, setResentExpiresAt] = useState<number | null>(null);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState("");
  const [resent, setResent] = useState(false);
  const [errorText, setErrorText] = useState("");
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const expiresAt = resentExpiresAt ?? pending?.expiresAt ?? null;
  const left = useCountdown(expiresAt, CODE_SECONDS);

  if (pending === undefined) return null;

  if (done) {
    return (
      <div className="stack-24">
        <div className="circle-icon">
          <BigCheckIcon />
        </div>
        <div className="stack-8">
          <h1 className="title">이메일 인증이 완료됐어요</h1>
          <p className="desc">가입이 모두 끝났어요. 이제 ANTIMONY를 시작해 보세요.</p>
        </div>
        <Link href="/login" className="btn-primary">
          로그인하러 가기
        </Link>
      </div>
    );
  }

  if (pending === null) {
    return (
      <div className="stack-28">
        <div className="stack-8">
          <h1 className="title">이메일 인증</h1>
          <p className="desc">가입 신청 정보를 찾을 수 없어요. 다시 가입해 주세요.</p>
        </div>
        <Link href="/signup" className="btn-primary">
          회원가입하러 가기
        </Link>
      </div>
    );
  }

  const shownCodeError = codeError || (left === 0 ? EXPIRED_MESSAGE : "");

  const verify = async () => {
    setErrorText("");
    if (left === 0) return setCodeError(EXPIRED_MESSAGE);
    if (code.length < 6) return setCodeError(EMPTY_CODE_MESSAGE);
    setSubmitting(true);
    try {
      await api.verifySignup(pending.email, code);
      clearPendingSignup();
      setDone(true);
    } catch (e) {
      setResent(false);
      if (e instanceof ApiError && e.fieldErrors.code) setCodeError(e.fieldErrors.code);
      else setErrorText(e instanceof ApiError ? e.message : "인증하지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

  const resend = async () => {
    setErrorText("");
    try {
      const result = await api.resendSignup(pending.email);
      const next = Date.now() + result.expiresIn * 1000;
      savePendingSignup({ email: pending.email, expiresAt: next });
      setResentExpiresAt(next);
      setCode("");
      setCodeError("");
      setResent(true);
    } catch (e) {
      setErrorText(e instanceof ApiError ? e.message : "인증번호를 다시 보내지 못했어요.");
    }
  };

  return (
    <div className="stack-28">
      <div className="circle-icon">
        <MailIcon />
      </div>
      <div className="stack-8">
        <h1 className="title">이메일 인증</h1>
        <p className="desc">
          <strong>{pending.email}</strong>(으)로 6자리 인증번호를 보냈어요. 메일이 보이지 않으면 스팸함도 확인해 주세요.
        </p>
      </div>
      {errorText && <Banner kind="error">{errorText}</Banner>}
      <CodeField
        code={code}
        onCode={(value) => {
          setCode(value);
          setCodeError("");
          setResent(false);
        }}
        secondsLeft={left}
        error={shownCodeError}
        resent={resent}
        onResend={resend}
      />
      <button type="button" className="btn-primary" onClick={verify} disabled={submitting}>
        인증하기
      </button>
      <p className="footer-text">
        이메일 주소를 잘못 입력하셨나요? <Link href="/signup">다시 가입하기</Link>
      </p>
    </div>
  );
}
