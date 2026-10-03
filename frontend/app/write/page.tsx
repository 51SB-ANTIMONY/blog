import type { Metadata } from "next";
import Editor from "@/components/Editor";
import LoginRequired from "@/components/LoginRequired";
import { fetchMe } from "@/lib/server";

export const metadata: Metadata = { title: "새 글 쓰기 · ANTIMONY" };

export default async function WritePage() {
  const me = await fetchMe();
  if (!me) return <LoginRequired returnTo="/write" />;
  return <Editor />;
}
