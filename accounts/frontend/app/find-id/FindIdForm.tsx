"use client";

import Link from "next/link";
import { useState } from "react";
import BackToLogin from "@/components/BackToLogin";
import Banner from "@/components/Banner";
import CodeField, { EMPTY_CODE_MESSAGE, EXPIRED_MESSAGE } from "@/components/CodeField";
import Steps from "@/components/Steps";
import { api, ApiError } from "@/lib/api";
import { useCountdown } from "@/lib/useCountdown";
import { emailError, maskEmail } from "@/lib/validation";

const CODE_SECONDS = 180;

export default function FindIdForm() {
  const [step, setStep] = useState(1);
  const [email, setEmail] = useState("");
  const [tried, setTried] = useState(false);
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState("");
  const [resent, setResent] = useState(false);
  const [maskedUid, setMaskedUid] = useState("");
  const [errorText, setErrorText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const left = useCountdown(expiresAt, CODE_SECONDS);

  const emailErrText = emailError(email);
  const emailErr = tried && !!emailErrText;

  const sendCode = async (isResend: boolean) => {
    setErrorText("");
    setSubmitting(true);
    try {
      const result = await api.sendFindIdCode(email.trim());
      setExpiresAt(Date.now() + result.expiresIn * 1000);
      setCode("");
      setCodeError("");
      setResent(isResend);
      setStep(2);
    } catch (e) {
      setErrorText(e instanceof ApiError ? e.message : "인증번호를 보내지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

  const submitEmail = (e: React.FormEvent) => {
    e.preventDefault();
    setTried(true);
    if (!emailErrText) sendCode(false);
  };

  const verify = async () => {
    setErrorText("");
    if (left === 0) return setCodeError(EXPIRED_MESSAGE);
    if (code.length < 6) return setCodeError(EMPTY_CODE_MESSAGE);
    setSubmitting(true);
    try {
      const result = await api.verifyFindId(email.trim(), code);
      setMaskedUid(result.maskedUid);
      setStep(3);
    } catch (e) {
      setResent(false);
      if (e instanceof ApiError && e.fieldErrors.code) setCodeError(e.fieldErrors.code);
      else setErrorText(e instanceof ApiError ? e.message : "인증하지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <BackToLogin />
      <Steps names={["이메일 확인", "인증번호", "아이디 확인"]} current={step} />
      {errorText && <Banner kind="error">{errorText}</Banner>}

      {step === 1 && (
        <form className="stack-28" onSubmit={submitEmail} noValidate>
          <div className="stack-8">
            <h1 className="title">아이디 찾기</h1>
            <p className="desc">가입할 때 입력한 이메일을 알려주세요. 인증 후 아이디를 확인할 수 있어요.</p>
          </div>
          <div className="stack-8">
            <label htmlFor="fi-email" className="label">
              가입한 이메일
            </label>
            <input
              id="fi-email"
              className="input"
              type="email"
              autoComplete="email"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={emailErr}
            />
            {emailErr && (
              <p role="alert" className="field-error">
                {emailErrText}
              </p>
            )}
          </div>
          <button type="submit" className="btn-primary" disabled={submitting}>
            인증번호 받기
          </button>
          <p className="footer-text">
            아이디는 기억나시나요? <Link href="/reset-password">비밀번호 찾기</Link>
          </p>
        </form>
      )}

      {step === 2 && (
        <div className="stack-28">
          <div className="stack-8">
            <h1 className="title">인증번호 입력</h1>
            <p className="desc">
              <strong>{maskEmail(email)}</strong>(으)로 보낸 6자리 인증번호를 입력해 주세요.
            </p>
          </div>
          <CodeField
            code={code}
            onCode={(value) => {
              setCode(value);
              setCodeError("");
              setResent(false);
            }}
            secondsLeft={left}
            error={codeError || (left === 0 ? EXPIRED_MESSAGE : "")}
            resent={resent}
            onResend={() => sendCode(true)}
          />
          <button type="button" className="btn-primary" onClick={verify} disabled={submitting}>
            확인
          </button>
        </div>
      )}

      {step === 3 && (
        <div className="stack-24">
          <div className="stack-8">
            <h1 className="title">아이디를 찾았어요</h1>
            <p className="desc">이 이메일로 가입된 아이디예요. 개인정보 보호를 위해 일부를 가렸어요.</p>
          </div>
          <div className="result-box">
            <span>아이디</span>
            <span className="result-value">{maskedUid}</span>
          </div>
          <div className="stack-10">
            <Link href="/login" className="btn-primary">
              로그인하러 가기
            </Link>
            <Link href="/reset-password" className="btn-ghost">
              비밀번호 찾기
            </Link>
          </div>
        </div>
      )}
    </>
  );
}
