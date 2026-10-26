import { categoryBySlug, type CategoryInfo } from "./categories";
import { currentYear, daysAgo, kst, monthName, yearOf } from "./format";
import type { Article } from "./types";

// 전체 글 목록에서 각 화면에 보일 내용을 계산한다. (글 목록은 서버에서 한 번에 받아 온다: lib/server.ts)

const time = (a: Article) => new Date(a.createdAt).getTime();
const newestFirst = (a: Article, b: Article) => time(b) - time(a) || b.id - a.id;

export function sortNewest(articles: Article[]): Article[] {
  return [...articles].sort(newestFirst);
}

// ---------- 홈 ----------

export const NEWS_COUNT = 2;
export const RECENT_COUNT = 3;
export const NEW_DAYS = 7;

// NEW 표시: 한국 시간으로 7일이 안 된 소식
export function isNew(article: Article): boolean {
  const days = daysAgo(article.createdAt);
  return article.category === "NEWS" && days >= 0 && days < NEW_DAYS;
}

export function buildHome(articles: Article[]) {
  const sorted = sortNewest(articles);
  // 첫 화면의 큰 글: 가장 최근의 일상 글 (없으면 가장 최근 글)
  const hero = sorted.find((a) => a.category === "DAILY") ?? sorted[0];
  return {
    news: sorted.filter((a) => a.category === "NEWS").slice(0, NEWS_COUNT),
    hero,
    // 최근에 남긴 것들: 첫 화면 글을 뺀 일상, 기도편지 (학습, 소식은 넣지 않는다)
    recent: sorted
      .filter((a) => (a.category === "DAILY" || a.category === "PRAYER") && a.id !== hero?.id)
      .slice(0, RECENT_COUNT),
  };
}

// ---------- 전체 기록 ----------

export const PAGE_SIZE = 5;
export const NOTICE_COUNT = 4;

export function buildAll(articles: Article[], raw: { sort?: string; page?: string }) {
  const sorted = sortNewest(articles);
  const sort: "oldest" | undefined = raw.sort === "oldest" ? "oldest" : undefined;
  const ordered = sort ? [...sorted].reverse() : sorted;
  const totalPages = Math.max(1, Math.ceil(ordered.length / PAGE_SIZE));
  const requested = Number.parseInt(raw.page ?? "1", 10);
  const page = Number.isNaN(requested) ? 1 : Math.min(Math.max(requested, 1), totalPages);
  return {
    sort,
    page,
    totalPages,
    total: ordered.length,
    posts: ordered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
    // 공지: 최근 소식
    notices: sorted.filter((a) => a.category === "NEWS").slice(0, NOTICE_COUNT),
  };
}

// ---------- 카테고리 목록 ----------

export function byCategory(articles: Article[], category: CategoryInfo): Article[] {
  return sortNewest(articles.filter((a) => a.category === category.code));
}

// ---------- 기도편지 ----------

// 연도 탭은 가장 오래된 편지의 해부터 올해(한국 시간)까지. 올해의 가장 최근 편지는 크게 보여 준다.
export function buildPrayer(articles: Article[], rawYear: string | undefined) {
  const letters = sortNewest(articles.filter((a) => a.category === "PRAYER"));
  const thisYear = Number(currentYear());
  const years = letters.map((a) => Number(yearOf(a.createdAt)));
  const first = Math.min(...years, thisYear);
  const last = Math.max(...years, thisYear);
  const tabs: string[] = [];
  for (let y = last; y >= first; y--) tabs.push(String(y));

  const year = rawYear && tabs.includes(rawYear) ? rawYear : String(thisYear);
  const inYear = letters.filter((a) => yearOf(a.createdAt) === year);
  const featured = year === String(thisYear) ? inYear[0] : undefined;
  return { tabs, year, featured, rows: featured ? inYear.slice(1) : inYear };
}

// 기도편지 이름은 작성한 달로 정한다: "2026년 10월 기도편지", "Oct 2026 Prayer Letter"
export function prayerLabel(article: Article): { ko: string; en: string } {
  const year = kst(article.createdAt).year;
  const month = monthName(article.createdAt);
  return { ko: `${year}년 ${month.ko} 기도편지`, en: `${month.en} ${year} Prayer Letter` };
}

// ---------- 검색 ----------

// 제목과 본문에서 찾는다. 대소문자는 가리지 않는다.
export function search(articles: Article[], q: string, categorySlug: string | undefined) {
  const category = categoryBySlug(categorySlug);
  const needle = q.trim().toLowerCase();
  const results = sortNewest(articles)
    .filter((a) => !category || a.category === category.code)
    .filter((a) => !needle || `${a.title}\n${a.content}`.toLowerCase().includes(needle));
  return { category, needle, results };
}

// 제목에서 검색어와 맞는 첫 부분을 나눈다 (<mark>로 강조)
export function splitMatch(title: string, needle: string): { pre: string; hit: string; post: string } {
  const at = needle ? title.toLowerCase().indexOf(needle) : -1;
  if (at < 0) return { pre: title, hit: "", post: "" };
  return { pre: title.slice(0, at), hit: title.slice(at, at + needle.length), post: title.slice(at + needle.length) };
}

// ---------- 글 상세 ----------

// 같은 카테고리에서 바로 이전(더 오래된) 글과 다음(더 최근) 글
export function neighbors(articles: Article[], article: Article) {
  const same = sortNewest(articles.filter((a) => a.category === article.category));
  const at = same.findIndex((a) => a.id === article.id);
  return {
    older: at >= 0 ? same[at + 1] : undefined,
    newer: at > 0 ? same[at - 1] : undefined,
  };
}
