"use client";

import { useEffect, useState } from "react";

// 인증번호 남은 시간(초). expiresAt(밀리초 시각)까지 1초마다 줄어든다.
export function useCountdown(expiresAt: number | null, initialSeconds: number): number {
  const [now, setNow] = useState<number | null>(null);

  useEffect(() => {
    const tick = () => setNow(Date.now());
    const first = setTimeout(tick, 0);
    const timer = setInterval(tick, 1000);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, []);

  if (expiresAt === null) return 0;
  if (now === null) return initialSeconds;
  return Math.max(0, Math.ceil((expiresAt - now) / 1000));
}

export function formatTimer(seconds: number): string {
  return String(Math.floor(seconds / 60)).padStart(2, "0") + ":" + String(seconds % 60).padStart(2, "0");
}
