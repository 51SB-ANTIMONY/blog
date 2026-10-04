import Editor from "@/components/Editor";
import LoginRequired from "@/components/LoginRequired";
import { fetchMe } from "@/lib/server";

export default async function WritePage() {
  const me = await fetchMe();
  if (!me) return <LoginRequired returnTo="/write" />;
  return <Editor />;
}
