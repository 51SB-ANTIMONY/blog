"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { text } from "@/lib/i18n";
import { useLang } from "@/lib/prefs";
import { Tx } from "./Bi";

// 내가 쓴 글에만 보이는 수정·삭제. 삭제를 누르면 그 자리에서 한 번 더 묻고, 삭제하면 목록으로 간다.
export default function OwnerActions({ id, listHref }: { id: number; listHref: string }) {
  const router = useRouter();
  const lang = useLang();
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const cancelRef = useRef<HTMLButtonElement>(null);

  // 확인 문구가 나오면 "취소"에 초점을 둔다 (실수로 바로 지우지 않게)
  useEffect(() => {
    if (confirming) cancelRef.current?.focus();
  }, [confirming]);

  const remove = async () => {
    setBusy(true);
    setError("");
    try {
      await api.remove(id);
      router.push(listHref);
      router.refresh();
    } catch (e) {
      setBusy(false);
      setError(e instanceof ApiError ? e.message : text(lang, "delFailed"));
    }
  };

  if (!confirming) {
    return (
      <>
        <Link href={`/articles/${id}/edit`} className="mb">
          <Tx k="editBtn" />
        </Link>
        <button type="button" className="mb danger" onClick={() => setConfirming(true)}>
          <Tx k="delBtn" />
        </button>
      </>
    );
  }
  return (
    <>
      <span className="cf" id="del-q">
        <Tx k="confirmDel" />
      </span>
      <button type="button" className="mb danger" onClick={remove} disabled={busy} aria-describedby="del-q">
        <Tx k="delBtn" />
      </button>
      <button
        type="button"
        ref={cancelRef}
        className="mb"
        disabled={busy}
        onClick={() => {
          setConfirming(false);
          setError("");
        }}
      >
        <Tx k="cancel" />
      </button>
      {error && (
        <p role="alert" className="mgr-err">
          {error}
        </p>
      )}
    </>
  );
}
