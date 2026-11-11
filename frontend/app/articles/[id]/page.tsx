import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Tx } from "@/components/Bi";
import Comments from "@/components/Comments";
import { ArticleLabel, Author } from "@/components/Labels";
import Markdown from "@/components/Markdown";
import OwnerActions from "@/components/OwnerActions";
import { Photo } from "@/components/Photo";
import ShareButton from "@/components/ShareButton";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader, { type MenuKey } from "@/components/SiteHeader";
import { ArrowLeftIcon, ArrowRightIcon } from "@/components/icons";
import { categoryOf } from "@/lib/categories";
import { formatDate, formatStamp, isEdited, yearOf } from "@/lib/format";
import { articlesHref } from "@/lib/links";
import { photoOf } from "@/lib/photos";
import { fetchArticle, fetchArticles, fetchComments, fetchMe } from "@/lib/server";
import type { Article } from "@/lib/types";
import { neighbors } from "@/lib/views";

type Props = { params: Promise<{ id: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const article = await fetchArticle((await params).id);
  if (!article) return {};
  return {
    description: article.excerpt,
    openGraph: { title: "Antimony Blog", description: article.title, siteName: "Antimony Blog", type: "article" },
  };
}

// 이 글이 있는 목록. 기도편지는 그 편지의 연도 탭으로 돌아간다.
function listHrefOf(article: Article): string {
  const slug = categoryOf(article.category).slug;
  return article.category === "PRAYER" ? articlesHref({ category: slug, year: yearOf(article.createdAt) }) : articlesHref({ category: slug });
}

function Neighbor({ article, dir }: { article?: Article; dir: "prev" | "next" }) {
  if (!article) {
    return (
      <div className={dir === "next" ? "nonebox r" : "nonebox"}>
        <Tx k={dir === "prev" ? "noPrev" : "noNext"} />
      </div>
    );
  }
  return (
    <Link href={`/articles/${article.id}`} className={dir === "prev" ? "pv" : "nx"}>
      <span className="pl">
        {dir === "prev" && <ArrowLeftIcon size={14} />}
        <Tx k={dir} />
        {dir === "next" && <ArrowRightIcon size={14} />}
      </span>
      <span className="pti" lang="ko">
        {article.title}
      </span>
      <span className="ds">{formatDate(article.createdAt)}</span>
    </Link>
  );
}

export default async function ArticlePage({ params }: Props) {
  const { id } = await params;
  const [me, article, articles] = await Promise.all([fetchMe(), fetchArticle(id), fetchArticles()]);
  if (!article) notFound();
  const comments = await fetchComments(article.id);

  const category = categoryOf(article.category);
  const listHref = listHrefOf(article);
  const { older, newer } = neighbors(articles, article);
  const photo = photoOf(article.id);

  return (
    <div className="app">
      <SiteHeader me={me} current={category.slug as MenuKey} />
      <main className="main">
        <div className="wrap">
          <article className="art">
            <header className="ahead">
              <span className="meta">
                <b className="acat">
                  <ArticleLabel article={article} />
                </b>
              </span>
              <h1 lang="ko">{article.title}</h1>
              <div className="arow">
                <span className="stamps">
                  <Author name={article.author} />
                  <span>
                    <Tx k="wrote" /> {formatStamp(article.createdAt)}
                  </span>
                  {isEdited(article.createdAt, article.updatedAt) && (
                    <span>
                      <Tx k="edited" /> {formatStamp(article.updatedAt)}
                    </span>
                  )}
                </span>
                <div className="mgr">
                  {article.editable && <OwnerActions id={article.id} listHref={listHref} />}
                  <ShareButton id={article.id} />
                </div>
              </div>
            </header>
            {photo && <Photo src={photo} className="pimg" sizes="(max-width: 720px) 100vw, 720px" priority />}
            <Markdown>{article.content}</Markdown>
            <Comments articleId={article.id} initial={comments} loggedIn={me !== null} />
            <nav className="pn" aria-labelledby="pn-h">
              <span id="pn-h" className="sr-only">
                <Tx k="prevNext" />
              </span>
              <Neighbor article={older} dir="prev" />
              <span className="sep" />
              <Neighbor article={newer} dir="next" />
            </nav>
          </article>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
