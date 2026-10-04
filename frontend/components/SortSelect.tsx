"use client";

import { useRouter } from "next/navigation";
import { homeHref, type HomeQuery } from "@/lib/home";

// 정렬(최신순, 오래된순). 바꾸면 첫 쪽부터 다시 보여 준다.
export default function SortSelect({ query, id, label }: { query: HomeQuery; id: string; label?: string }) {
  const router = useRouter();
  return (
    <select
      id={id}
      aria-label={label}
      className="select"
      value={query.sort ?? "newest"}
      onChange={(e) => router.push(homeHref({ ...query, sort: e.target.value === "oldest" ? "oldest" : undefined, page: 1 }))}
    >
      <option value="newest">최신순</option>
      <option value="oldest">오래된순</option>
    </select>
  );
}
