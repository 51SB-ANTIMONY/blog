import Link from "next/link";
import LogoutButton from "@/components/LogoutButton";
import ReturnAfterLogin from "@/components/ReturnAfterLogin";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader, { CategoryTabs } from "@/components/SiteHeader";
import { TreeIcon } from "@/components/icons";
import { CATEGORIES, categoryBySlug, categoryLabel } from "@/lib/categories";
import { formatDate } from "@/lib/format";
import { fetchArticles, fetchMe } from "@/lib/server";

type Props = { searchParams: Promise<{ category?: string }> };

const QUOTE = "그는 시냇가에 심은 나무가 철을 따라 열매를 맺으며 그 잎사귀가 마르지 아니함 같으니";

function Quote() {
  return (
    <div className="quote">
      <div className="quote-label">
        <span className="only-desktop">
          <TreeIcon />
        </span>
        시냇가에 심은 나무
      </div>
      <blockquote>{QUOTE}</blockquote>
      <div className="quote-foot">
        <span>시편 1:3</span>
        <Link href="/?category=tree" className="quote-btn">
          묵상 글 보기 →
        </Link>
      </div>
    </div>
  );
}

export default async function HomePage({ searchParams }: Props) {
  const { category: slug } = await searchParams;
  const category = categoryBySlug(slug);
  const [me, articles] = await Promise.all([fetchMe(), fetchArticles(category?.code)]);

  return (
    <div className="page">
      {me && <ReturnAfterLogin />}
      <SiteHeader me={me} variant="home" currentSlug={category?.slug} />
      <CategoryTabs currentSlug={category?.slug} className="tabs-mobile only-mobile" />

      <main className="content">
        <section className="hero">
          <div className="hero-text">
            {me && (
              <div className="greeting only-mobile">
                <span>
                  <b>{me.nickname}</b>님, 반가워요
                </span>
                <LogoutButton />
              </div>
            )}
            <div className="kicker only-desktop">DAILY · STUDY · ROOTED</div>
            <h1>
              천천히 살고,
              <br />
              깊이 배우고,
              <br />
              뿌리 내리는 기록
            </h1>
            <p className="hero-desc">
              <span className="only-desktop">[블로그 한 줄 소개 — 어떤 이야기를 쓰는 곳인지 적어 주세요.]</span>
              <span className="only-mobile">[블로그 한 줄 소개]</span>
            </p>
          </div>
          <div className="only-desktop">
            <Quote />
          </div>
        </section>

        <section className="quote-wrap only-mobile" style={{ flexDirection: "column" }}>
          <Quote />
        </section>

        <section className="cats only-desktop">
          {CATEGORIES.map((c) => (
            <Link key={c.slug} href={`/?category=${c.slug}`} className="cat">
              <span className="cat-dot" />
              <span className="cat-label">{c.label}</span>
              <span className="cat-desc">{c.desc}</span>
              <span className="cat-more">글 보러 가기 →</span>
            </Link>
          ))}
        </section>

        <section id="posts" className="posts">
          <div className="posts-head">
            <h2>{category ? category.label : "최근 글"}</h2>
            {category && (
              <Link href="/" className="only-desktop">
                모든 글 보기 →
              </Link>
            )}
          </div>
          <div className="post-grid">
            {articles.length === 0 ? (
              <p className="empty">아직 글이 없어요.</p>
            ) : (
              articles.map((article) => (
                <Link key={article.id} href={`/articles/${article.id}`} className="post-item">
                  <div className="post-meta">
                    <span className="tag">{categoryLabel(article.category)}</span>
                    <span className="date">{formatDate(article.createdAt)}</span>
                  </div>
                  <span className="post-title">{article.title}</span>
                  <span className="post-excerpt">{article.excerpt}</span>
                </Link>
              ))
            )}
          </div>
        </section>
      </main>

      <SiteFooter />
    </div>
  );
}
