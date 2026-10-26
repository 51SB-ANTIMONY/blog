"use client";

import { useEffect, useState } from "react";
import { text } from "@/lib/i18n";
import { useLang } from "@/lib/prefs";
import { Tx } from "./Bi";
import { ShareIcon } from "./icons";

// 글 주소를 클립보드에 복사하고, 화면 아래에 잠깐 알린다.
export default function ShareButton({ id }: { id: number }) {
  const lang = useLang();
  const [toast, setToast] = useState<"copied" | "copyFailed" | null>(null);
  const label = text(lang, "share");

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 2000);
    return () => clearTimeout(timer);
  }, [toast]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/articles/${id}`);
      setToast("copied");
    } catch {
      setToast("copyFailed");
    }
  };

  return (
    <>
      <button type="button" className="xb share" onClick={copy} aria-label={label} title={label}>
        <ShareIcon />
      </button>
      <div className="toast-area" role="status">
        {toast && (
          <span className="toast">
            <Tx k={toast} />
          </span>
        )}
      </div>
    </>
  );
}
