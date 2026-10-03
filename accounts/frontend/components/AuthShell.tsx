import Image from "next/image";
import ThemeToggle from "./ThemeToggle";

function Logo() {
  return (
    <div className="logo">
      <Image className="logo-mark" src="/logo-mark.png" alt="" width={1055} height={926} priority />
      <Image className="logo-wordmark" src="/logo-wordmark.png" alt="ANTIMONY" width={1533} height={192} priority />
    </div>
  );
}

// PC: 왼쪽 소개 영역 + 오른쪽 본문 / 모바일: 위쪽 헤더(로고·테마 버튼) + 본문
export default function AuthShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="shell">
      <aside className="shell-aside">
        <Image className="shell-aside-mark" src="/logo-mark.png" alt="" width={1055} height={926} />
        <Logo />
        <div className="shell-aside-intro">
          <p className="shell-aside-title">[ANTIMONY를 소개하는 한 줄]</p>
          <p className="shell-aside-desc">[서비스 설명 1–2문장]</p>
        </div>
        <p className="shell-aside-copy">© 2026 ANTIMONY</p>
      </aside>
      <div className="shell-body">
        <header className="shell-header">
          <Logo />
          <ThemeToggle />
        </header>
        <main className="shell-main">
          <div className="content">{children}</div>
        </main>
      </div>
    </div>
  );
}
