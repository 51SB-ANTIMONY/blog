"use client";

import { text } from "@/lib/i18n";
import { setLang, setTheme, useLang, useTheme } from "@/lib/prefs";
import { MoonIcon, SunIcon } from "./icons";

// KO/EN 전환. 선택된 쪽의 색은 CSS가 <html lang>을 보고 칠한다 (처음 그릴 때부터 맞게).
export function LangToggle() {
  const lang = useLang();
  return (
    <div className="lang" role="group" aria-label="Language">
      <button type="button" data-lang="ko" aria-pressed={lang === "ko"} onClick={() => setLang("ko")}>
        KO
      </button>
      <button type="button" data-lang="en" aria-pressed={lang === "en"} onClick={() => setLang("en")}>
        EN
      </button>
    </div>
  );
}

// 라이트/다크 전환. 아이콘은 지금 모드(해 = 라이트, 달 = 다크)를, 이름은 누르면 바뀔 모드를 알려 준다.
export function ThemeToggle() {
  const lang = useLang();
  const theme = useTheme();
  const label = text(lang, theme === "dark" ? "themeToLight" : "themeToDark");
  return (
    <button type="button" className="ib" aria-label={label} title={label} onClick={() => setTheme(theme === "dark" ? "light" : "dark")}>
      <SunIcon className="hide-in-dark" />
      <MoonIcon className="hide-in-light" />
    </button>
  );
}
