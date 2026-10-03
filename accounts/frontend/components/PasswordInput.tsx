"use client";

import { EyeIcon, EyeOffIcon } from "./icons";

type Props = {
  id: string;
  value: string;
  onChange: (value: string) => void;
  shown: boolean;
  onToggle: () => void;
  invalid: boolean;
  autoComplete: string;
  placeholder: string;
  name?: string;
  // 두 번째 칸(비밀번호 확인)의 보기 버튼은 Tab 순서에서 뺀다.
  toggleTabbable?: boolean;
};

export default function PasswordInput({ id, value, onChange, shown, onToggle, invalid, autoComplete, placeholder, name, toggleTabbable = true }: Props) {
  const label = shown ? "비밀번호 숨기기" : "비밀번호 보기";
  return (
    <div className="input-wrap">
      <input
        id={id}
        name={name}
        className="input input-with-button"
        type={shown ? "text" : "password"}
        autoComplete={autoComplete}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={invalid}
      />
      <button type="button" className="input-icon-btn" onClick={onToggle} aria-label={label} tabIndex={toggleTabbable ? undefined : -1}>
        {shown ? <EyeOffIcon /> : <EyeIcon />}
      </button>
    </div>
  );
}
