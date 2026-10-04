"use client";

import Link from "next/link";
import { useState } from "react";
import { formatDate } from "@/lib/format";
import type { Article } from "@/lib/types";

// News 목록. 제목을 누르면 요약이 펼쳐진다. 한 번에 하나만 펼친다.
export default function NewsList({ items }: { items: Article[] }) {
  const [openId, setOpenId] = useState<number | null>(null);

  if (items.length === 0) return <p className="news-empty">아직 소식이 없어요.</p>;

  return (
    <ul className="news-list">
      {items.map((item) => {
        const open = openId === item.id;
        const bodyId = `news-body-${item.id}`;
        return (
          <li key={item.id} className="news-item">
            <button
              type="button"
              className="news-toggle"
              aria-expanded={open}
              aria-controls={bodyId}
              onClick={() => setOpenId(open ? null : item.id)}
            >
              <span className="news-text">
                <span className="news-date">{formatDate(item.createdAt)}</span>
                <span className="news-title">{item.title}</span>
              </span>
              <span className="news-sign" aria-hidden="true">
                {open ? "−" : "+"}
              </span>
            </button>
            {open && (
              <div id={bodyId} className="news-body">
                <p>{item.excerpt}</p>
                <Link href={`/articles/${item.id}`}>글 보기 →</Link>
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}
