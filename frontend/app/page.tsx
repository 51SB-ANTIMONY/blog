import Link from "next/link";
import MobileFilter from "@/components/MobileFilter";
import NewsList from "@/components/NewsList";
import ReturnAfterLogin from "@/components/ReturnAfterLogin";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader from "@/components/SiteHeader";
import SortSelect from "@/components/SortSelect";
import { CATEGORIES, categoryLabel } from "@/lib/categories";
import { formatDate } from "@/lib/format";
import { buildHome, homeHref, type HomeQuery } from "@/lib/home";
import { fetchArticles, fetchMe } from "@/lib/server";

type Props = { searchParams: Promise<{ category?: string; year?: string; sort?: string; page?: string }> };

type Home = ReturnType<typeof buildHome>;

// 카테고리, 기간 필터. PC는 왼쪽 목록(side), 모바일은 필터 상자 안의 칩(chips)
function Filters({ home, variant }: { home: Home; variant: "side" | "chips" }) {
  const { query } = home;
  const itemClass = variant === "side" ? "filter-row" : "chip-btn";
  // 필터를 바꾸면 정렬은 그대로 두고 첫 쪽부터 보여 준다.
  const href = (change: Partial<HomeQuery>) => homeHref({ ...query, ...change, page: 1 });
  const cats = [{ slug: undefined, code: undefined, label: "전체" }, ...CATEGORIES];
  const years: { year?: string; label: string; count: number }[] = [
    { label: variant === "side" ? "전체 기간" : "전체", count: home.yearTotal },
    ...home.years.map((y) => ({ year: y.year, label: y.year, count: y.count })),
  ];

  return (
    <>
      <div className="filter-group">
        <span className="filter-label">카테고리</span>
        <div className={variant === "side" ? "filter-rows" : "chip-row"}>
          {cats.map((c) => (
            <Link
              key={c.label}
              href={href({ category: c.slug })}
              className={itemClass}
              aria-current={query.category === c.slug ? "true" : undefined}
            >
              <span>{c.label}</span>
              <span className="filter-count">{home.countByCategory(c.code)}</span>
            </Link>
          ))}
        </div>
      </div>
      <div className="filter-group">
        <span className="filter-label">기간</span>
        <div className={variant === "side" ? "filter-rows" : "chip-row"}>
          {years.map((y) => (
            <Link
              key={y.label}
              href={href({ year: y.year })}
              className={itemClass}
              aria-current={query.year === y.year ? "true" : undefined}
            >
              <span>{y.label}</span>
              <span className="filter-count">{y.count}</span>
            </Link>
          ))}
        </div>
      </div>
      <div className="filter-group is-disabled">
        <span className="filter-label">
          태그 <span className="soon">(준비 중)</span>
        </span>
      </div>
    </>
  );
}

function Pagination({ home }: { home: Home }) {
  const { query, totalPages } = home;
  const page = query.page ?? 1;
  return (
    <nav aria-label="페이지" className="pager">
      {page > 1 && (
        <Link href={homeHref({ ...query, page: page - 1 })} aria-label="이전 페이지" className="pager-item">
          ←
        </Link>
      )}
      {Array.from({ length: totalPages }, (_, i) => i + 1).map((n) => (
        <Link
          key={n}
          href={homeHref({ ...query, page: n })}
          className="pager-item"
          aria-current={n === page ? "page" : undefined}
        >
          {n}
        </Link>
      ))}
      {page < totalPages && (
        <Link href={homeHref({ ...query, page: page + 1 })} aria-label="다음 페이지" className="pager-item">
          →
        </Link>
      )}
    </nav>
  );
}

export default async function HomePage({ searchParams }: Props) {
  const [me, articles, raw] = await Promise.all([fetchMe(), fetchArticles(), searchParams]);
  const home = buildHome(articles, raw);
  const { query } = home;
  const title = home.category ? home.category.label : "모든 글";
  const countLabel = `${home.total}개의 글`;
  const resetHref = homeHref({ sort: query.sort });

  return (
    <div className="page">
      {me && <ReturnAfterLogin />}
      <SiteHeader me={me} variant="home" />

      <section aria-labelledby="news-h" className="news">
        <div className="news-side">
          <h2 id="news-h">News</h2>
          <Link href="/?category=news" className="news-more">
            <span className="only-desktop">지난 소식 모두 보기 →</span>
            <span className="only-mobile">모두 보기 →</span>
          </Link>
        </div>
        <NewsList items={home.news} />
      </section>

      <div className="home-body">
        <aside aria-label="필터" className="filters only-desktop">
          <div className="filter-top">
            <span>필터</span>
            <Link href={resetHref} className="reset-link">
              초기화
            </Link>
          </div>
          <div className="filter-group">
            <label htmlFor="sort" className="filter-label">
              정렬
            </label>
            <SortSelect id="sort" query={query} />
          </div>
          <Filters home={home} variant="side" />
        </aside>

        <main className="list">
          <div className="list-head only-desktop">
            <h2>{title}</h2>
            <span>{countLabel}</span>
          </div>
          <div className="mobile-filter only-mobile">
            <MobileFilter title={title} countLabel={countLabel} active={home.activeFilters}>
              <div className="filter-group">
                <span className="filter-label">정렬</span>
                <SortSelect id="sort-m" label="정렬" query={query} />
              </div>
              <Filters home={home} variant="chips" />
              <div className="panel-foot">
                <Link href={resetHref} className="reset-link">
                  초기화
                </Link>
              </div>
            </MobileFilter>
          </div>

          {home.posts.map((article) => (
            <Link key={article.id} href={`/articles/${article.id}`} className="post-item">
              <div className="post-meta">
                <span className="tag">{categoryLabel(article.category)}</span>
                <span className="date">{formatDate(article.createdAt)}</span>
              </div>
              <span className="post-title">{article.title}</span>
              <span className="post-excerpt">{article.excerpt}</span>
            </Link>
          ))}
          {home.posts.length === 0 && (
            <p className="empty">
              조건에 맞는 글이 없어요.<span className="only-desktop-inline"> 필터를 바꿔 보세요.</span>
            </p>
          )}
          <Pagination home={home} />
        </main>
      </div>

      <SiteFooter />
    </div>
  );
}
