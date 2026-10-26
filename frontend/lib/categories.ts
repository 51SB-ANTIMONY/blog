import type { Category } from "./types";

export type CategoryInfo = { code: Category; slug: string; ko: string; en: string };

// 카테고리. 주소에는 소문자(news 등)를, API에는 대문자(NEWS 등)를 쓴다. 메뉴와 글쓰기 선택지가 이 순서다.
export const CATEGORIES: CategoryInfo[] = [
  { code: "NEWS", slug: "news", ko: "소식", en: "News" },
  { code: "PRAYER", slug: "prayer", ko: "기도편지", en: "Prayer Letters" },
  { code: "DAILY", slug: "daily", ko: "일상", en: "Daily" },
  { code: "STUDY", slug: "study", ko: "학습", en: "Study" },
];

export function categoryOf(code: Category): CategoryInfo {
  return CATEGORIES.find((c) => c.code === code) ?? { code, slug: code.toLowerCase(), ko: code, en: code };
}

export function categoryBySlug(slug: string | undefined): CategoryInfo | undefined {
  return CATEGORIES.find((c) => c.slug === slug);
}
