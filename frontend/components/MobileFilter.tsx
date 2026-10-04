"use client";

import { useState } from "react";
import { FilterIcon } from "./icons";

// 모바일 목록 머리의 "필터" 버튼과, 누르면 펼쳐지는 필터 상자
export default function MobileFilter({
  title,
  countLabel,
  active,
  children,
}: {
  title: string;
  countLabel: string;
  active: number;
  children: React.ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <div className="list-head-mobile">
        <div className="list-head-text">
          <h2>{title}</h2>
          <span>{countLabel}</span>
        </div>
        <button type="button" className="filter-btn" aria-expanded={open} aria-controls="filter-panel" onClick={() => setOpen(!open)}>
          <FilterIcon />
          {active ? `필터 ${active}` : "필터"}
        </button>
      </div>
      {open && (
        <div id="filter-panel" className="filter-panel">
          {children}
        </div>
      )}
    </>
  );
}
