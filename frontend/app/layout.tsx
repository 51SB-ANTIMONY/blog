import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Antimony Blog",
  description: "Antimony Blog",
  openGraph: {
    title: "Antimony Blog",
    description: "Antimony Blog",
    siteName: "Antimony Blog",
    type: "website",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
};

// 저장해 둔 테마와 언어(lib/prefs.ts)를 화면을 그리기 전에 <html>에 적용한다.
// 서버는 이 값을 모르기 때문에, 이렇게 하지 않으면 기본값(라이트, 한국어)이 잠깐 보였다가 바뀐다.
const PREFS_SCRIPT = `try{var d=document.documentElement,t=localStorage.getItem("antimony-theme"),l=localStorage.getItem("antimony-lang");if(t==="light"||t==="dark")d.dataset.theme=t;if(l==="ko"||l==="en")d.lang=l}catch(e){}`;

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    // 위 스크립트가 lang, data-theme을 바꾸므로 서버가 그린 값과 달라도 경고하지 않게 한다.
    <html lang="ko" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: PREFS_SCRIPT }} />
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        {/* eslint-disable-next-line @next/next/no-page-custom-font */}
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+KR:wght@600&display=swap"
        />
      </head>
      <body>{children}</body>
    </html>
  );
}
