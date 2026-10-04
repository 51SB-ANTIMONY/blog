import { categoryBySlug } from "./categories";
import type { Article } from "./types";

// 홈의 필터 상태. 주소의 쿼리(?category=news&year=2026&sort=oldest&page=2)로 주고받는다.
export type HomeQuery = {
  category?: string; // 카테고리 slug (전체면 없음)
  year?: string; // 작성 연도 (전체 기간이면 없음)
  sort?: "oldest"; // 오래된순 (최신순이면 없음)
  page?: number; // 2쪽부터
};

export const PAGE_SIZE = 8;
export const NEWS_COUNT = 4;

type RawQuery = { category?: string; year?: string; sort?: string; page?: string };

// 홈 주소. 기본값은 주소에서 뺀다.
export function homeHref(query: HomeQuery): string {
  const params = new URLSearchParams();
  if (query.category) params.set("category", query.category);
  if (query.year) params.set("year", query.year);
  if (query.sort) params.set("sort", query.sort);
  if (query.page && query.page > 1) params.set("page", String(query.page));
  const search = params.toString();
  return search ? `/?${search}` : "/";
}

const yearOf = (article: Article) => article.createdAt.slice(0, 4);

const newestFirst = (a: Article, b: Article) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id;

// 전체 글 목록에서 홈에 보일 내용을 계산한다.
export function buildHome(articles: Article[], raw: RawQuery) {
  const sorted = [...articles].sort(newestFirst);
  const category = categoryBySlug(raw.category);
  const years = [...new Set(sorted.map(yearOf))];

  // 주소에 알 수 없는 값이 있으면 무시한다.
  const query: HomeQuery = {
    category: category?.slug,
    year: raw.year && years.includes(raw.year) ? raw.year : undefined,
    sort: raw.sort === "oldest" ? "oldest" : undefined,
  };

  const byCategory = sorted.filter((a) => !category || a.category === category.code);
  const filtered = byCategory.filter((a) => !query.year || yearOf(a) === query.year);
  if (query.sort === "oldest") filtered.reverse();

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const requested = Number.parseInt(raw.page ?? "1", 10);
  const page = Number.isNaN(requested) ? 1 : Math.min(Math.max(requested, 1), totalPages);
  query.page = page;

  return {
    query,
    category,
    // 뉴스 카테고리의 최근 글
    news: sorted.filter((a) => a.category === "NEWS").slice(0, NEWS_COUNT),
    posts: filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
    total: filtered.length,
    totalPages,
    countByCategory: (code?: string) => (code ? sorted.filter((a) => a.category === code).length : sorted.length),
    years: years.map((year) => ({ year, count: byCategory.filter((a) => yearOf(a) === year).length })),
    yearTotal: byCategory.length,
    // 모바일 필터 버튼에 보이는 "필터 N"
    activeFilters: (query.category ? 1 : 0) + (query.year ? 1 : 0),
  };
}
