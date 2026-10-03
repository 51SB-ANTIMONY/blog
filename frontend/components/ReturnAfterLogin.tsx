"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { takeReturnTo } from "@/lib/api";

// 로그인이 필요해서 accounts로 갔다가 첫 화면(/)으로 돌아왔다면, 원래 가려던 화면으로 보낸다.
export default function ReturnAfterLogin() {
  const router = useRouter();
  useEffect(() => {
    const returnTo = takeReturnTo();
    if (returnTo) router.replace(returnTo);
  }, [router]);
  return null;
}
