"use client";

import { useEffect } from "react";
import { startLogin } from "@/lib/api";

// 로그인이 필요한 화면. accounts 로그인으로 보내고, 로그인 후 이 화면으로 돌아오게 한다.
export default function LoginRequired({ returnTo }: { returnTo: string }) {
  useEffect(() => {
    startLogin(returnTo);
  }, [returnTo]);
  return null;
}
