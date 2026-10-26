"use client";

import { useSyncExternalStore } from "react";
import type { Lang } from "./i18n";

// 테마와 언어 설정. antimony.kr의 다른 페이지와 같은 키로 브라우저(localStorage)에 저장한다.
// 저장된 값은 app/layout.tsx의 PREFS_SCRIPT가 화면을 그리기 전에 <html>에 적용한다.
export const THEME_KEY = "antimony-theme";
export const LANG_KEY = "antimony-lang";

export type Theme = "light" | "dark";

function save(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // 저장하지 못해도 지금 화면에는 적용된다.
  }
}

// <html lang>, <html data-theme>, 운영체제 테마가 바뀌면 다시 그린다.
function subscribe(onChange: () => void) {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["lang", "data-theme"] });
  const media = matchMedia("(prefers-color-scheme: dark)");
  media.addEventListener("change", onChange);
  return () => {
    observer.disconnect();
    media.removeEventListener("change", onChange);
  };
}

const currentLang = (): Lang => (document.documentElement.lang === "en" ? "en" : "ko");

const currentTheme = (): Theme => {
  const chosen = document.documentElement.dataset.theme;
  if (chosen === "light" || chosen === "dark") return chosen;
  return matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
};

// 서버에서 그릴 때는 기본값(한국어, 라이트)을 쓴다.
export function useLang(): Lang {
  return useSyncExternalStore(subscribe, currentLang, () => "ko");
}

export function useTheme(): Theme {
  return useSyncExternalStore(subscribe, currentTheme, () => "light");
}

export function setLang(lang: Lang) {
  document.documentElement.lang = lang;
  save(LANG_KEY, lang);
}

export function setTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  save(THEME_KEY, theme);
}
