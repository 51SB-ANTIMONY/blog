import Link from "next/link";
import { Bi, SrOnly, Tx } from "@/components/Bi";
import { Author, CategoryLabel } from "@/components/Labels";
import SearchInput from "@/components/SearchInput";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader from "@/components/SiteHeader";
import { CloseIcon, SearchIcon } from "@/components/icons";
import { CATEGORIES } from "@/lib/categories";
import { formatDate } from "@/lib/format";
import { searchHref } from "@/lib/links";
import { fetchArticles, fetchMe } from "@/lib/server";
import { search, splitMatch } from "@/lib/views";

type Props = { searchParams: Promise<{ q?: string; category?: string }> };

// 검색: /articles/search?q=가을&category=daily
// 글 목록을 모두 받아 이 서버(Next.js)에서 제목과 본문을 거른다 (lib/views.ts의 search).
export default async function SearchPage({ searchParams }: Props) {
  const [me, articles, raw] = await Promise.all([fetchMe(), fetchArticles(), searchParams]);
  const q = (raw.q ?? "").trim();
  const { category, needle, results } = search(articles, q, raw.category);
  const tabs = [{ slug: undefined, label: <Tx k="all" /> }, ...CATEGORIES.map((c) => ({ slug: c.slug, label: <Bi ko={c.ko} en={c.en} /> }))];

  return (
    <div className="app">
      <SiteHeader me={me} current="search" />
      <main className="main">
        <section className="wrap page">
          <h1>
            <Tx k="searchTitle" />
          </h1>
          {/* 엔터를 누르면 이 주소로 다시 그린다 (검색어가 주소에 남아 공유, 뒤로 가기가 된다) */}
          <form role="search" className="sform" action="/articles/search">
            {category && <input type="hidden" name="category" value={category.slug} />}
            <label htmlFor="q" className="sr-only">
              <Tx k="search" />
            </label>
            <SearchInput defaultValue={q} />
            {q && (
              <Link href={searchHref(undefined, category?.slug)} className="xb">
                <CloseIcon />
                <SrOnly k="clear" />
              </Link>
            )}
            <button type="submit" className="xb submit">
              <SearchIcon size={26} />
              <SrOnly k="search" />
            </button>
          </form>
          <nav className="tabs" aria-labelledby="cat-h">
            <span id="cat-h" className="sr-only">
              <Tx k="categories" />
            </span>
            {tabs.map((tab) => (
              <Link key={tab.slug ?? "all"} href={searchHref(q, tab.slug)} className="tab" aria-current={tab.slug === category?.slug ? "page" : undefined}>
                {tab.label}
              </Link>
            ))}
          </nav>
          <p className="cnt result-count">
            {q ? (
              <Bi ko={`“${q}” 검색 결과 ${results.length}개`} en={`${results.length} results for “${q}”`} />
            ) : (
              <Bi ko={`전체 글 ${results.length}개`} en={`${results.length} posts`} />
            )}
          </p>
          <div className="rows results">
            {results.map((article) => {
              const title = splitMatch(article.title, needle);
              return (
                <Link key={article.id} href={`/articles/${article.id}`} className="res">
                  <span className="meta">
                    <b>
                      <CategoryLabel code={article.category} />
                    </b>{" "}
                    <span className="res-date">
                      {formatDate(article.createdAt)} · <Author name={article.author} />
                    </span>
                  </span>
                  <span className="rtt" lang="ko">
                    {title.pre}
                    {title.hit && <mark>{title.hit}</mark>}
                    {title.post}
                  </span>
                </Link>
              );
            })}
            {results.length === 0 && (
              <p className="empty">
                <Tx k="empty" />
              </p>
            )}
          </div>
        </section>
      </main>
      <SiteFooter />
    </div>
  );
}
