"use client";

import { useState } from "react";
import { logout } from "@/lib/api";

export default function LogoutButton() {
  const [busy, setBusy] = useState(false);
  return (
    <button
      type="button"
      className="text-btn"
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
      로그아웃
    </button>
  );
}
