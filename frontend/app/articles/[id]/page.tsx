import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import OwnerActions from "@/components/OwnerActions";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader from "@/components/SiteHeader";
import { categoryLabel } from "@/lib/categories";
import { formatDate, isEdited } from "@/lib/format";
import { fetchArticle, fetchMe } from "@/lib/server";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const article = await fetchArticle((await params).id);
  return article ? { description: article.excerpt } : {};
}

export default async function ArticlePage({ params }: Props) {
  const { id } = await params;
  const [me, article] = await Promise.all([fetchMe(), fetchArticle(id)]);
  if (!article) notFound();

  return (
    <div className="page">
      <SiteHeader me={me} variant="post" />
      <main className="article-main">
        <div className="article-col">
          <article className="article">
            <Link href="/" className="back-link">
              ← 목록으로
            </Link>
            <div className="article-head">
              <div className="post-meta">
                <span className="tag">{categoryLabel(article.category)}</span>
              </div>
              <h1>{article.title}</h1>
              <div className="article-meta">
                <span>
                  <b>{article.author}</b>
                </span>
                <span aria-hidden="true">·</span>
                <span>{formatDate(article.createdAt)} 작성</span>
                {isEdited(article.createdAt, article.updatedAt) && <span>· {formatDate(article.updatedAt)} 수정</span>}
              </div>
            </div>
            {article.editable && <OwnerActions id={article.id} />}
            <div className="divider" />
            <div className="article-body">{article.content}</div>
          </article>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
