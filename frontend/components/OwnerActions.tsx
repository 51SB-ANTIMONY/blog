"use client";

import Link from "next/link";
import { useState } from "react";
import DeleteConfirm from "./DeleteConfirm";
import { PencilIcon } from "./icons";

// 내가 쓴 글에만 보이는 수정·삭제 버튼
export default function OwnerActions({ id }: { id: number }) {
  const [confirming, setConfirming] = useState(false);
  return (
    <div className="owner">
      <div className="owner-buttons">
        <Link href={`/articles/${id}/edit`} className="pill-edit">
          <PencilIcon />
          수정
        </Link>
        <button type="button" className="danger-btn" onClick={() => setConfirming(true)}>
          삭제
        </button>
      </div>
      {confirming && <DeleteConfirm id={id} onCancel={() => setConfirming(false)} />}
    </div>
  );
}
