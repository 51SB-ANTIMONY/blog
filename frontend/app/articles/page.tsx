import Link from "next/link";
import { Bi, Tx } from "@/components/Bi";
import { Author, CategoryLabel } from "@/components/Labels";
import { Photo } from "@/components/Photo";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader, { type MenuKey } from "@/components/SiteHeader";
import { ArrowRightIcon, ArrowUpRightIcon, ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon, LetterIcon } from "@/components/icons";
import { categoryBySlug, type CategoryInfo } from "@/lib/categories";
import { formatDate, kst, monthName } from "@/lib/format";
import { articlesHref } from "@/lib/links";
import { photoOf } from "@/lib/photos";
import { fetchArticles, fetchMe } from "@/lib/server";
import type { Article } from "@/lib/types";
import { buildAll, buildPrayer, byCategory } from "@/lib/views";

type Props = { searchParams: Promise<{ category?: string; year?: string; sort?: string; page?: string }> };

// /articles 하나에서 세 가지 목록을 그린다.
//   category 없음: 전체 기록 / news, daily, study: 카테고리 목록 / prayer: 기도편지
export default async function ArticlesPage({ searchParams }: Props) {
  const [me, articles, raw] = await Promise.all([fetchMe(), fetchArticles(), searchParams]);
  const category = categoryBySlug(raw.category);
  const current: MenuKey = (category?.slug as MenuKey) ?? "all";

  let content: React.ReactNode;
  if (!category) content = <AllList articles={articles} raw={raw} />;
  else if (category.code === "PRAYER") content = <PrayerList articles={articles} rawYear={raw.year} />;
  else content = <CategoryList articles={articles} category={category} />;

  return (
    <div className="app">
      <SiteHeader me={me} current={current} />
      <main className="main">{content}</main>
      <SiteFooter />
    </div>
  );
}

// ---------- 전체 기록: 공지, 정렬, 한 쪽에 5개씩 ----------

function AllList({ articles, raw }: { articles: Article[]; raw: { sort?: string; page?: string } }) {
  const all = buildAll(articles, raw);
  const pageHref = (page: number) => articlesHref({ sort: all.sort, page });
  return (
    <section className="wrap page">
      <h1>
        <Tx k="allTitle" />
      </h1>
      {all.notices.length > 0 && (
        <div className="notice">
          <h2 className="nl">
            <Tx k="notice" />
          </h2>
          <div className="ngrid">
            {all.notices.map((n, i) => (
              // 두 줄째부터 위에 선 (좁은 화면에서는 한 줄씩이라 두 번째부터)
              <Link key={n.id} href={`/articles/${n.id}`} className={`nbtn${i >= 2 ? " rb" : i === 1 ? " r1" : ""}`}>
                <span className="t" lang="ko">
                  {n.title}
                </span>
                <ChevronRightIcon size={16} />
              </Link>
            ))}
          </div>
        </div>
      )}
      <div className="bar2">
        <span>
          <Bi ko={`총 ${all.total}개의 기록`} en={`${all.total} posts`} />
        </span>
        {/* 누르면 최신순과 오래된순이 바뀐다 */}
        <Link href={articlesHref({ sort: all.sort ? undefined : "oldest" })} className="sortb">
          <Tx k={all.sort ? "sortOld" : "sortNew"} /> <ChevronDownIcon />
        </Link>
      </div>
      <div>
        {all.posts.map((article) => {
          const photo = photoOf(article.id);
          return (
            <Link key={article.id} href={`/articles/${article.id}`} className="arow2">
              <span className="tx">
                <span className="meta">
                  <b>
                    <CategoryLabel code={article.category} />
                  </b>{" "}
                  <span>
                    · {formatDate(article.createdAt)} · <Author name={article.author} />
                  </span>
                </span>
                <span className="at" lang="ko">
                  {article.title}
                </span>
                <span className="ex" lang="ko">
                  {article.excerpt}
                </span>
              </span>
              {/* 사진이 없으면 같은 너비의 빈 칸을 둬서 글 너비를 맞춘다 */}
              {photo ? <Photo src={photo} className="thumb" sizes="140px" /> : <span className="ph0" />}
            </Link>
          );
        })}
        {all.total === 0 && (
          <p className="empty">
            <Tx k="noPosts" />
          </p>
        )}
      </div>
      <nav className="pager" aria-labelledby="pager-h">
        <span id="pager-h" className="sr-only">
          <Tx k="pages" />
        </span>
        {all.page > 1 ? (
          <Link href={pageHref(all.page - 1)} className="pgn">
            <ChevronLeftIcon /> <Tx k="pgPrev" />
          </Link>
        ) : (
          <span className="pgn" aria-disabled="true">
            <ChevronLeftIcon /> <Tx k="pgPrev" />
          </span>
        )}
        {Array.from({ length: all.totalPages }, (_, i) => i + 1).map((n) => (
          <Link key={n} href={pageHref(n)} className="pg" aria-current={n === all.page ? "page" : undefined}>
            {n}
          </Link>
        ))}
        {all.page < all.totalPages ? (
          <Link href={pageHref(all.page + 1)} className="pgn">
            <Tx k="pgNext" /> <ChevronRightIcon />
          </Link>
        ) : (
          <span className="pgn" aria-disabled="true">
            <Tx k="pgNext" /> <ChevronRightIcon />
          </span>
        )}
      </nav>
    </section>
  );
}

// ---------- 카테고리 목록 (소식, 일상, 학습) ----------

function CategoryList({ articles, category }: { articles: Article[]; category: CategoryInfo }) {
  const posts = byCategory(articles, category);
  return (
    <section className="wrap page">
      <h1>
        <Bi ko={category.ko} en={category.en} />
      </h1>
      <p className="cnt">
        <Bi ko={`${posts.length}개의 글`} en={`${posts.length} posts`} />
      </p>
      <div className="rows">
        {posts.map((article) => (
          <Link key={article.id} href={`/articles/${article.id}`} className="row">
            <span className="rd">
              {formatDate(article.createdAt)}
              <br />
              <Author name={article.author} />
            </span>
            <span className="rt" lang="ko">
              {article.title}
            </span>
            <ArrowRightIcon className="ar" />
          </Link>
        ))}
        {posts.length === 0 && (
          <p className="empty">
            <Tx k="noPosts" />
          </p>
        )}
      </div>
    </section>
  );
}

// ---------- 기도편지: 연도 탭, 올해의 최근 편지, 편지 목록 ----------

function PrayerList({ articles, rawYear }: { articles: Article[]; rawYear?: string }) {
  const prayer = buildPrayer(articles, rawYear);
  const featured = prayer.featured;
  const featuredPhoto = featured && photoOf(featured.id);
  return (
    <section className="wrap page">
      <h1>
        <Bi ko="기도편지" en="Prayer Letters" />
      </h1>
      <nav className="tabs" aria-labelledby="years-h">
        <span id="years-h" className="sr-only">
          <Tx k="years" />
        </span>
        {prayer.tabs.map((y) => (
          <Link key={y} href={articlesHref({ category: "prayer", year: y })} className="tab" aria-current={y === prayer.year ? "page" : undefined}>
            {y}
          </Link>
        ))}
      </nav>
      {featured && (
        <div className="feat">
          <div className="fc">
            <span className="meta">
              <span className="feat-month">
                <Bi ko={`${kst(featured.createdAt).year}년 ${monthName(featured.createdAt).ko}`} en={`${monthName(featured.createdAt).en} ${kst(featured.createdAt).year}`} /> ·{" "}
                <Author name={featured.author} />
              </span>
            </span>
            <h2 lang="ko">{featured.title}</h2>
            <p className="ex" lang="ko">
              {featured.excerpt}
            </p>
            <Link href={`/articles/${featured.id}`} className="btn feat-btn">
              <Tx k="readLetter" /> <ArrowUpRightIcon />
            </Link>
          </div>
          {featuredPhoto && <Photo src={featuredPhoto} className="feat-img" sizes="(max-width: 720px) 100vw, 340px" />}
        </div>
      )}
      <div className="rows prows">
        {prayer.rows.map((article) => (
          <Link key={article.id} href={`/articles/${article.id}`} className="prow">
            <span className="pm-wrap">
              <span className="py">
                <Bi ko={`${kst(article.createdAt).year}년`} en={kst(article.createdAt).year} />
              </span>
              <span className="pm">
                <Bi ko={monthName(article.createdAt).ko} en={monthName(article.createdAt).en} />
              </span>
            </span>
            <LetterIcon size={28} />
            <span>
              <span className="pt2" lang="ko">
                {article.title}
              </span>
              <span className="py by-line">
                <Author name={article.author} />
              </span>
            </span>
            <ArrowRightIcon className="ar" />
          </Link>
        ))}
        {!featured && prayer.rows.length === 0 && (
          <p className="empty">
            <Tx k="noPrayer" />
          </p>
        )}
      </div>
    </section>
  );
}
