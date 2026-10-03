"use client";

import { formatTimer } from "@/lib/useCountdown";
import { CheckIcon } from "./icons";

type Props = {
  code: string;
  onCode: (code: string) => void;
  secondsLeft: number;
  error: string;
  resent: boolean;
  onResend: () => void;
};

// 인증번호 6자리 입력 + 남은 시간 + 재전송
export default function CodeField({ code, onCode, secondsLeft, error, resent, onResend }: Props) {
  return (
    <div className="stack-8">
      <div className="label-row">
        <label htmlFor="code-input" className="label">
          인증번호
        </label>
        <span aria-live="off" className={secondsLeft <= 30 ? "timer is-urgent" : "timer"}>
          남은 시간 {formatTimer(secondsLeft)}
        </span>
      </div>
      <input
        id="code-input"
        className="input input-code"
        type="text"
        inputMode="numeric"
        autoComplete="one-time-code"
        placeholder="000000"
        value={code}
        onChange={(e) => onCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
        aria-invalid={!!error}
      />
      {error && (
        <p role="alert" className="field-error">
          {error}
        </p>
      )}
      {resent && (
        <p role="status" className="field-ok">
          <CheckIcon />
          인증번호를 다시 보냈어요.
        </p>
      )}
      <div className="resend-row">
        <span>인증번호를 받지 못하셨나요?</span>
        <button type="button" className="text-btn" onClick={onResend}>
          재전송
        </button>
      </div>
    </div>
  );
}

export const EXPIRED_MESSAGE = "인증 시간이 만료됐어요. 인증번호를 재전송해 주세요.";
export const EMPTY_CODE_MESSAGE = "인증번호 6자리를 입력해 주세요.";
