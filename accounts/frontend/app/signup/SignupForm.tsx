"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import Banner from "@/components/Banner";
import NewPasswordFields from "@/components/NewPasswordFields";
import { CheckIcon } from "@/components/icons";
import { api, ApiError } from "@/lib/api";
import { savePendingSignup } from "@/lib/signupSession";
import { emailError, passwordConfirmError, passwordError, UID_PATTERN } from "@/lib/validation";

type IdStatus = "empty" | "invalid" | "taken" | "ok";

const ID_MESSAGES: Record<IdStatus, string> = {
  empty: "아이디를 입력해 주세요.",
  invalid: "영문 소문자·숫자 4–16자로 입력해 주세요.",
  taken: "이미 사용 중인 아이디예요.",
  ok: "사용 가능한 아이디예요.",
};

export default function SignupForm() {
  const router = useRouter();
  const [uid, setUid] = useState("");
  // 중복확인 결과는 확인한 그 아이디에만 유효하다.
  const [idCheck, setIdCheck] = useState<{ uid: string; status: IdStatus } | null>(null);
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [tos, setTos] = useState(false);
  const [privacy, setPrivacy] = useState(false);
  const [marketing, setMarketing] = useState(false);
  const [tried, setTried] = useState(false);
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({});
  const [errorText, setErrorText] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const clearServer = (field: string) => {
    if (serverErrors[field]) setServerErrors({ ...serverErrors, [field]: "" });
  };

  const trimmedUid = uid.trim();
  const idFormatOk = UID_PATTERN.test(trimmedUid);
  const idStatus = idCheck && idCheck.uid === uid ? idCheck.status : null;
  const uidErrText =
    serverErrors.uid ||
    (!trimmedUid
      ? ID_MESSAGES.empty
      : !idFormatOk
        ? ID_MESSAGES.invalid
        : idStatus === "taken"
          ? ID_MESSAGES.taken
          : idStatus !== "ok"
            ? "아이디 중복확인을 해 주세요."
            : "");
  const idShowErr = !!serverErrors.uid || (!!idStatus && idStatus !== "ok") || (tried && !!uidErrText);
  const idMsg = serverErrors.uid
    ? serverErrors.uid
    : idStatus === "ok"
      ? ID_MESSAGES.ok
      : idStatus
        ? ID_MESSAGES[idStatus]
        : tried && uidErrText
          ? uidErrText
          : "영문 소문자·숫자 4–16자";

  const n = name.trim();
  const nameErrText = serverErrors.nickname || (!n ? "닉네임을 입력해 주세요." : n.length < 2 || n.length > 16 ? "닉네임은 2–16자로 입력해 주세요." : "");
  const emailErrText = serverErrors.email || emailError(email);
  const pwErrText = serverErrors.password || passwordError(pw);
  const pw2ErrText = passwordConfirmError(pw, pw2);
  const termsOk = tos && privacy;
  const all = tos && privacy && marketing;
  const show = (text: string, field: string) => (tried || !!serverErrors[field]) && !!text;
  const valid = !uidErrText && !nameErrText && !emailErrText && !pwErrText && !pw2ErrText && termsOk;

  const checkId = async () => {
    setErrorText("");
    clearServer("uid");
    if (!trimmedUid) return setIdCheck({ uid, status: "empty" });
    if (!idFormatOk) return setIdCheck({ uid, status: "invalid" });
    try {
      const result = await api.checkUid(trimmedUid, email.trim());
      setIdCheck({ uid, status: result.status });
    } catch (e) {
      setErrorText(e instanceof ApiError ? e.message : "중복확인을 하지 못했어요.");
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setTried(true);
    setErrorText("");
    if (!valid) return;
    setSubmitting(true);
    try {
      const result = await api.signup({
        uid: trimmedUid,
        password: pw,
        nickname: n,
        email: email.trim(),
        termsAgreed: tos,
        privacyAgreed: privacy,
        marketingAgreed: marketing,
      });
      savePendingSignup({ email: result.email, expiresAt: Date.now() + result.expiresIn * 1000 });
      router.push("/signup/verify");
    } catch (err) {
      setSubmitting(false);
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setServerErrors(err.fieldErrors);
        if (err.fieldErrors.uid) setIdCheck(null);
      } else {
        setErrorText(err instanceof ApiError ? err.message : "가입 신청을 하지 못했어요.");
      }
    }
  };

  return (
    <>
      <div className="stack-8">
        <h1 className="title">회원가입</h1>
        <p className="desc">몇 가지 정보만 입력하면 바로 시작할 수 있어요.</p>
      </div>
      {errorText && <Banner kind="error">{errorText}</Banner>}
      <form className="stack-28" onSubmit={submit} noValidate>
        <div className="stack-18">
          <div className="stack-8">
            <label htmlFor="su-id" className="label">
              아이디
            </label>
            <div className="input-row">
              <input
                id="su-id"
                className="input"
                type="text"
                autoComplete="username"
                placeholder="아이디 입력"
                value={uid}
                onChange={(e) => {
                  setUid(e.target.value);
                  clearServer("uid");
                }}
                aria-invalid={idShowErr}
                aria-describedby="su-id-msg"
              />
              <button type="button" className="btn-accent" onClick={checkId}>
                중복확인
              </button>
            </div>
            <p id="su-id-msg" aria-live="polite" className={`field-msg${idStatus === "ok" && !serverErrors.uid ? " is-ok" : idShowErr ? " is-error" : ""}`}>
              {idStatus === "ok" && !serverErrors.uid && <CheckIcon />}
              {idMsg}
            </p>
          </div>
          <NewPasswordFields
            idPrefix="su"
            labels={["비밀번호", "비밀번호 확인"]}
            password={pw}
            confirm={pw2}
            onPassword={(value) => {
              setPw(value);
              clearServer("password");
            }}
            onConfirm={setPw2}
            tried={tried}
            serverError={serverErrors.password}
          />
          <div className="stack-8">
            <label htmlFor="su-name" className="label">
              닉네임
            </label>
            <input
              id="su-name"
              className="input"
              type="text"
              autoComplete="nickname"
              placeholder="2–16자"
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                clearServer("nickname");
              }}
              aria-invalid={show(nameErrText, "nickname")}
            />
            {show(nameErrText, "nickname") && (
              <p role="alert" className="field-error">
                {nameErrText}
              </p>
            )}
          </div>
          <div className="stack-8">
            <label htmlFor="su-email" className="label">
              이메일 (아이디·비밀번호 찾기용)
            </label>
            <input
              id="su-email"
              className="input"
              type="email"
              autoComplete="email"
              placeholder="name@example.com"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                clearServer("email");
              }}
              aria-invalid={show(emailErrText, "email")}
            />
            {show(emailErrText, "email") && (
              <p role="alert" className="field-error">
                {emailErrText}
              </p>
            )}
          </div>
        </div>
        <div className="stack-8">
          <div className={tried && !termsOk ? "terms is-error" : "terms"}>
            <label className="checkbox-row terms-all">
              <input
                type="checkbox"
                checked={all}
                onChange={() => {
                  setTos(!all);
                  setPrivacy(!all);
                  setMarketing(!all);
                }}
              />
              전체 동의
            </label>
            <div className="terms-line" />
            <div className="terms-row">
              <label className="checkbox-row">
                <input type="checkbox" checked={tos} onChange={() => setTos(!tos)} />
                <span>
                  <span className="terms-tag">[필수]</span> 이용약관 동의
                </span>
              </label>
              <a href="#">보기</a>
            </div>
            <div className="terms-row">
              <label className="checkbox-row">
                <input type="checkbox" checked={privacy} onChange={() => setPrivacy(!privacy)} />
                <span>
                  <span className="terms-tag">[필수]</span> 개인정보 수집·이용 동의
                </span>
              </label>
              <a href="#">보기</a>
            </div>
            <div className="terms-row">
              <label className="checkbox-row">
                <input type="checkbox" checked={marketing} onChange={() => setMarketing(!marketing)} />
                <span>
                  <span className="terms-tag">[선택]</span> 이벤트·소식 수신 동의
                </span>
              </label>
              <a href="#">보기</a>
            </div>
          </div>
          {tried && !termsOk && (
            <p role="alert" className="field-error">
              필수 약관에 동의해 주세요.
            </p>
          )}
        </div>
        <button type="submit" className="btn-primary" disabled={submitting}>
          회원가입
        </button>
      </form>
      <p className="footer-text">
        이미 계정이 있으신가요? <Link href="/login">로그인</Link>
      </p>
    </>
  );
}
