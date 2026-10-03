"use client";

import { useState } from "react";
import { passwordChecks, passwordConfirmError, passwordError } from "@/lib/validation";
import PasswordInput from "./PasswordInput";
import { CheckIcon, CircleIcon } from "./icons";

type Props = {
  idPrefix: string;
  labels: [string, string];
  password: string;
  confirm: string;
  onPassword: (value: string) => void;
  onConfirm: (value: string) => void;
  // 제출을 한 번 눌렀으면 빈칸 오류도 보여 준다.
  tried: boolean;
  emptyMessage?: string;
  serverError?: string;
};

// 새 비밀번호 + 강도 막대 + 조건 + 비밀번호 확인 (회원가입, 비밀번호 재설정)
export default function NewPasswordFields({ idPrefix, labels, password, confirm, onPassword, onConfirm, tried, emptyMessage, serverError }: Props) {
  const [shown, setShown] = useState(false);
  const checks = passwordChecks(password);
  const met = checks.filter((check) => check.ok).length;
  const pwErrText = serverError || passwordError(password, emptyMessage);
  const pw2ErrText = passwordConfirmError(password, confirm);
  const pwErr = (tried || !!serverError) && !!pwErrText;
  const mismatch = !!confirm && confirm !== password;
  const pw2Err = (tried && !!pw2ErrText) || mismatch;

  return (
    <>
      <div className="stack-8">
        <label htmlFor={`${idPrefix}-pw`} className="label">
          {labels[0]}
        </label>
        <PasswordInput
          id={`${idPrefix}-pw`}
          value={password}
          onChange={onPassword}
          shown={shown}
          onToggle={() => setShown(!shown)}
          invalid={pwErr}
          autoComplete="new-password"
          placeholder="비밀번호 입력"
        />
        {pwErr && (
          <p role="alert" className="field-error">
            {pwErrText}
          </p>
        )}
      </div>
      <div className="strength">
        <div className="strength-bar">
          <div className="strength-segments">
            {[0, 1, 2].map((i) => (
              <div key={i} className={i < met ? "is-on" : undefined} />
            ))}
          </div>
          <span className="strength-label">{password ? ["약함", "약함", "보통", "안전"][met] : ""}</span>
        </div>
        <div className="strength-rules">
          {checks.map((check) => (
            <span key={check.label} className={check.ok ? "is-ok" : undefined}>
              {check.ok ? <CheckIcon /> : <CircleIcon />}
              {check.label}
            </span>
          ))}
        </div>
      </div>
      <div className="stack-8">
        <label htmlFor={`${idPrefix}-pw2`} className="label">
          {labels[1]}
        </label>
        <PasswordInput
          id={`${idPrefix}-pw2`}
          value={confirm}
          onChange={onConfirm}
          shown={shown}
          onToggle={() => setShown(!shown)}
          invalid={pw2Err}
          autoComplete="new-password"
          placeholder="비밀번호 다시 입력"
          toggleTabbable={false}
        />
        {pw2Err && (
          <p role="alert" className="field-error">
            {pw2ErrText}
          </p>
        )}
      </div>
      {!!confirm && confirm === password && (
        <p className="field-ok match-ok">
          <CheckIcon />
          비밀번호가 일치해요.
        </p>
      )}
    </>
  );
}
