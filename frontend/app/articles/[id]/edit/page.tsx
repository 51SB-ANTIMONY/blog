import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import Editor from "@/components/Editor";
import LoginRequired from "@/components/LoginRequired";
import { fetchArticle, fetchMe } from "@/lib/server";

export const metadata: Metadata = { title: "글 수정 · ANTIMONY" };

type Props = { params: Promise<{ id: string }> };

export default async function EditPage({ params }: Props) {
  const { id } = await params;
  const me = await fetchMe();
  if (!me) return <LoginRequired returnTo={`/articles/${id}/edit`} />;
  const article = await fetchArticle(id);
  if (!article) notFound();
  // 작성자가 아니면 글 상세로 (수정·삭제 권한은 서버가 다시 확인한다)
  if (!article.editable) redirect(`/articles/${id}`);
  return <Editor article={article} />;
}
