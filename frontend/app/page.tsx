import Image from "next/image";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Tx } from "@/components/Bi";
import { ArticleLabel, Author, CategoryLabel } from "@/components/Labels";
import { Photo } from "@/components/Photo";
import ReturnAfterLogin from "@/components/ReturnAfterLogin";
import SiteFooter from "@/components/SiteFooter";
import SiteHeader from "@/components/SiteHeader";
import { ArrowUpRightIcon, LetterIcon } from "@/components/icons";
import { categoryBySlug } from "@/lib/categories";
import { formatDate, formatShortDate } from "@/lib/format";
import { articlesHref } from "@/lib/links";
import { photoOf } from "@/lib/photos";
import { fetchArticles, fetchMe } from "@/lib/server";
import type { Article } from "@/lib/types";
import { buildHome, isNew } from "@/lib/views";

type Props = { searchParams: Promise<{ category?: string; sort?: string; page?: string }> };

// 첫 화면의 큰 글. 사진이 있으면 사진과 글을 나란히, 없으면 카드 한 장으로.
function Hero({ article }: { article: Article }) {
  const href = `/articles/${article.id}`;
  const photo = photoOf(article.id);
  const copy = (
    <>
      <span className="kick">
        <Tx k="kicker" />
      </span>
      <h2 id="hero-h" lang="ko">
        {article.title}
      </h2>
      <span className="hd">
        {formatDate(article.createdAt)} · <Author name={article.author} />
      </span>
      <span className="ex" lang="ko">
        {article.excerpt}
      </span>
    </>
  );

  if (!photo) {
    return (
      <section className="wrap" aria-labelledby="hero-h">
        <Link href={href} className="card panel heropanel">
          <Image className="tl" src="/logo-mark.png" alt="" width={640} height={558} />
          {copy}
          <span className="btn herobtn">
            <Tx k="readStory" /> <ArrowUpRightIcon />
          </span>
        </Link>
      </section>
    );
  }
  return (
    <section className="wrap hero" aria-labelledby="hero-h">
      {/* 사진 링크는 아래 "이야기 읽기"와 같은 곳이라 키보드 이동과 화면 낭독기에서는 건너뛴다 */}
      <Link href={href} className="imgbtn" tabIndex={-1} aria-hidden="true">
        <Photo src={photo} className="hero-img" sizes="(max-width: 720px) 100vw, 940px" priority />
      </Link>
      <div className="hcopy">
        {copy}
        <Link href={href} className="btn herobtn">
          <Tx k="readStory" /> <ArrowUpRightIcon />
        </Link>
      </div>
    </section>
  );
}

// 최근에 남긴 것들의 카드. 사진이 있는 일상 글은 사진 카드, 나머지(기도편지, 사진 없는 글)는 글 카드.
function RecentCard({ article }: { article: Article }) {
  const href = `/articles/${article.id}`;
  const isLetter = article.category === "PRAYER";
  const photo = isLetter ? undefined : photoOf(article.id);
  if (photo) {
    return (
      <Link href={href} className="card">
        <Photo src={photo} className="card-img" sizes="(max-width: 720px) 100vw, 427px" />
        <span>
          <span className="meta">
            <b>
              <CategoryLabel code={article.category} />
            </b>{" "}
            <span>
              · {formatShortDate(article.createdAt)} · <Author name={article.author} />
            </span>
          </span>
          <h3 lang="ko">{article.title}</h3>
        </span>
      </Link>
    );
  }
  return (
    <Link href={href} className="card panel">
      <span className="panel-top">
        {isLetter ? <LetterIcon /> : <Image className="tl" src="/logo-mark.png" alt="" width={640} height={558} />}
        <span className="meta">
          <b>
            <ArticleLabel article={article} />
          </b>{" "}
          <span>
            · {!isLetter && <>{formatShortDate(article.createdAt)} · </>}
            <Author name={article.author} />
          </span>
        </span>
        <h3 lang="ko">{article.title}</h3>
        <span className="ex" lang="ko">
          {article.excerpt}
        </span>
      </span>
      <span className="ul">
        <Tx k={isLetter ? "readLetter" : "readPost"} /> <ArrowUpRightIcon />
      </span>
    </Link>
  );
}

export default async function HomePage({ searchParams }: Props) {
  const raw = await searchParams;
  // 예전 홈 주소(/?category=news&sort=oldest&page=2)는 새 목록 주소로 보낸다.
  if (raw.category || raw.sort || raw.page) {
    const page = Number.parseInt(raw.page ?? "", 10);
    redirect(
      articlesHref({
        category: categoryBySlug(raw.category)?.slug,
        sort: raw.sort === "oldest" ? "oldest" : undefined,
        page: Number.isNaN(page) ? undefined : page,
      }),
    );
  }

  const [me, articles] = await Promise.all([fetchMe(), fetchArticles()]);
  const home = buildHome(articles);

  return (
    <div className="app">
      {me && <ReturnAfterLogin />}
      <SiteHeader me={me} />
      <main className="main">
        <h1 className="sr-only">ANTIMONY blog</h1>
        {home.news.length > 0 && (
          <section className="wrap news" aria-labelledby="news-h">
            <div className="nhd">
              <h2 id="news-h">
                <Tx k="newsHead" />
              </h2>
              <Link href={articlesHref({ category: "news" })} className="ul">
                <Tx k="viewAll" /> <ArrowUpRightIcon />
              </Link>
            </div>
            {home.news.map((item) => (
              <Link key={item.id} href={`/articles/${item.id}`} className="nitem">
                <span className="nrow">
                  <span>{formatShortDate(item.createdAt)}</span>
                  {isNew(item) && <span className="tag">NEW</span>}
                </span>
                <span className="nt" lang="ko">
                  {item.title}
                </span>
              </Link>
            ))}
          </section>
        )}

        {home.hero ? (
          <Hero article={home.hero} />
        ) : (
          <p className="wrap empty">
            <Tx k="noPosts" />
          </p>
        )}

        {home.recent.length > 0 && (
          <section className="wrap" aria-labelledby="recent-h">
            <div className="rhead">
              <h2 id="recent-h">
                <Tx k="recentHead" />
              </h2>
              <Link href={articlesHref()} className="ul">
                <Tx k="allPosts" /> <ArrowUpRightIcon />
              </Link>
            </div>
            <div className="cards">
              {home.recent.map((article) => (
                <RecentCard key={article.id} article={article} />
              ))}
            </div>
          </section>
        )}
      </main>
      <SiteFooter />
    </div>
  );
}
