"use client";

import { text } from "@/lib/i18n";
import { useLang } from "@/lib/prefs";

// 검색어 입력칸. 안내 문구(placeholder)만 언어를 따라 바꾼다.
export default function SearchInput({ defaultValue }: { defaultValue: string }) {
  const lang = useLang();
  return (
    <input
      id="q"
      name="q"
      type="search"
      className="qin"
      defaultValue={defaultValue}
      placeholder={text(lang, "searchPh")}
      autoComplete="off"
      enterKeyHint="search"
    />
  );
}
