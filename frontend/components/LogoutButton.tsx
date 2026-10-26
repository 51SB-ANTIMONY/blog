"use client";

import { useState } from "react";
import { logout } from "@/lib/api";
import { text } from "@/lib/i18n";
import { useLang } from "@/lib/prefs";
import { LogoutIcon } from "./icons";

export default function LogoutButton() {
  const lang = useLang();
  const [busy, setBusy] = useState(false);
  const label = text(lang, "logout");
  return (
    <button
      type="button"
      className="lo"
      aria-label={label}
      title={label}
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        try {
          await logout();
        } catch {
          setBusy(false);
        }
      }}
    >
      <LogoutIcon />
    </button>
  );
}
