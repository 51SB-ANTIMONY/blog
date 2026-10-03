"use client";

import Link from "next/link";
import { useState } from "react";
import BackToLogin from "@/components/BackToLogin";
import Banner from "@/components/Banner";
import CodeField, { EMPTY_CODE_MESSAGE, EXPIRED_MESSAGE } from "@/components/CodeField";
import NewPasswordFields from "@/components/NewPasswordFields";
import Steps from "@/components/Steps";
import { BigCheckIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import { useCountdown } from "@/lib/useCountdown";
import { emailError, maskEmail, passwordConfirmError, passwordError } from "@/lib/validation";

const CODE_SECONDS = 180;

export default function ResetForm() {
  const [step, setStep] = useState(1);
  const [uid, setUid] = useState("");
  const [email, setEmail] = useState("");
  const [tried1, setTried1] = useState(false);
  const [expiresAt, setExpiresAt] = useState<number | null>(null);
  const [code, setCode] = useState("");
  const [codeError, setCodeError] = useState("");
  const [resent, setResent] = useState(false);
  const [resetToken, setResetToken] = useState("");
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [tried3, setTried3] = useState(false);
  const [errorText, setErrorText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const left = useCountdown(expiresAt, CODE_SECONDS);

  const uidErrText = !uid.trim() ? "아이디를 입력해 주세요." : "";
  const emailErrText = emailError(email);
  const uidErr = tried1 && !!uidErrText;
  const emailErr = tried1 && !!emailErrText;

  const sendCode = async (isResend: boolean) => {
    setErrorText("");
    setSubmitting(true);
    try {
      const result = await api.sendResetCode(uid.trim(), email.trim());
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

  const submitIdentity = (e: React.FormEvent) => {
    e.preventDefault();
    setTried1(true);
    if (!uidErrText && !emailErrText) sendCode(false);
  };

  const verify = async () => {
    setErrorText("");
    if (left === 0) return setCodeError(EXPIRED_MESSAGE);
    if (code.length < 6) return setCodeError(EMPTY_CODE_MESSAGE);
    setSubmitting(true);
    try {
      const result = await api.verifyReset(uid.trim(), email.trim(), code);
      setResetToken(result.resetToken);
      setStep(3);
    } catch (e) {
      setResent(false);
      if (e instanceof ApiError && e.fieldErrors.code) setCodeError(e.fieldErrors.code);
      else setErrorText(e instanceof ApiError ? e.message : "인증하지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

  const changePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setTried3(true);
    setErrorText("");
    if (passwordError(pw, "새 비밀번호를 입력해 주세요.") || passwordConfirmError(pw, pw2)) return;
    setSubmitting(true);
    try {
      await api.completeReset(resetToken, pw);
      setStep(4);
    } catch (err) {
      setErrorText(err instanceof ApiError ? (err.fieldErrors.password ?? err.message) : "비밀번호를 바꾸지 못했어요.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <BackToLogin />
      {step < 4 && <Steps names={["본인 확인", "인증번호", "새 비밀번호"]} current={step} />}
      {errorText && <Banner kind="error">{errorText}</Banner>}

      {step === 1 && (
        <form className="stack-28" onSubmit={submitIdentity} noValidate>
          <div className="stack-8">
            <h1 className="title">비밀번호 찾기</h1>
            <p className="desc">가입할 때 입력한 아이디와 이메일을 알려주세요. 이메일로 인증번호를 보내드려요.</p>
          </div>
          <div className="stack-18">
            <div className="stack-8">
              <label htmlFor="rs-id" className="label">
                아이디
              </label>
              <input
                id="rs-id"
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
              <label htmlFor="rs-email" className="label">
                가입한 이메일
              </label>
              <input
                id="rs-email"
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
          </div>
          <button type="submit" className="btn-primary" disabled={submitting}>
            인증번호 받기
          </button>
          <p className="footer-text">
            아이디가 기억나지 않나요? <Link href="/find-id">아이디 찾기</Link>
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
        <form className="stack-28" onSubmit={changePassword} noValidate>
          <div className="stack-8">
            <h1 className="title">새 비밀번호 설정</h1>
            <p className="desc">이전에 쓰지 않았던 비밀번호를 권장해요.</p>
          </div>
          <div className="stack-18">
            <NewPasswordFields
              idPrefix="rs"
              labels={["새 비밀번호", "새 비밀번호 확인"]}
              password={pw}
              confirm={pw2}
              onPassword={setPw}
              onConfirm={setPw2}
              tried={tried3}
              emptyMessage="새 비밀번호를 입력해 주세요."
            />
          </div>
          <button type="submit" className="btn-primary" disabled={submitting}>
            비밀번호 변경
          </button>
        </form>
      )}

      {step === 4 && (
        <div className="stack-24">
          <div className="circle-icon">
            <BigCheckIcon />
          </div>
          <div className="stack-8">
            <h1 className="title">비밀번호가 변경됐어요</h1>
            <p className="desc">새 비밀번호로 다시 로그인해 주세요.</p>
          </div>
          <Link href="/login" className="btn-primary">
            로그인하러 가기
          </Link>
        </div>
      )}
    </>
  );
}
