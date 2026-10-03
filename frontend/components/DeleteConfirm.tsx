"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api, ApiError } from "@/lib/api";

// "이 글을 삭제할까요?" 확인 상자. 삭제하면 첫 화면으로 간다.
export default function DeleteConfirm({ id, onCancel }: { id: number; onCancel: () => void }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const remove = async () => {
    setBusy(true);
    setError("");
    try {
      await api.remove(id);
      router.push("/");
      router.refresh();
    } catch (e) {
      setBusy(false);
      setError(e instanceof ApiError ? e.message : "삭제하지 못했어요.");
    }
  };

  return (
    <div role="alertdialog" aria-labelledby="del-title" className="confirm">
      <div className="confirm-text">
        <strong id="del-title">이 글을 삭제할까요?</strong>
        <span>삭제하면 되돌릴 수 없어요.</span>
      </div>
      {error && (
        <p role="alert" className="confirm-error">
          {error}
        </p>
      )}
      <div className="confirm-buttons">
        <button type="button" className="btn-cancel" onClick={onCancel} disabled={busy}>
          취소
        </button>
        <button type="button" className="btn-danger" onClick={remove} disabled={busy}>
          삭제
        </button>
      </div>
    </div>
  );
}
