import { AlertIcon, CheckIcon } from "./icons";

// 화면 위쪽 안내 상자. success: 초록 테두리, error: 빨간 테두리
export default function Banner({ kind, children }: { kind: "success" | "error"; children: React.ReactNode }) {
  if (kind === "error") {
    return (
      <div role="alert" className="banner is-error">
        <span className="banner-icon">
          <AlertIcon />
        </span>
        <span>{children}</span>
      </div>
    );
  }
  return (
    <div role="status" className="banner">
      <span className="banner-icon">
        <CheckIcon />
      </span>
      <span>{children}</span>
    </div>
  );
}
