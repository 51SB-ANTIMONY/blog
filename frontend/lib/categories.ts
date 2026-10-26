import type { Category } from "./types";

// back: 글 상세의 "목록으로" 링크 문구 (조사가 이름마다 달라서 따로 적는다)
export type CategoryInfo = { code: Category; slug: string; ko: string; en: string; back: string };

// 카테고리. 주소에는 소문자(news 등)를, API에는 대문자(NEWS 등)를 쓴다. 메뉴와 글쓰기 선택지가 이 순서다.
export const CATEGORIES: CategoryInfo[] = [
  { code: "NEWS", slug: "news", ko: "소식", en: "News", back: "소식으로" },
  { code: "PRAYER", slug: "prayer", ko: "기도편지", en: "Prayer Letters", back: "기도편지로" },
  { code: "DAILY", slug: "daily", ko: "일상", en: "Daily", back: "일상으로" },
  { code: "STUDY", slug: "study", ko: "학습", en: "Study", back: "학습으로" },
];

export function categoryOf(code: Category): CategoryInfo {
  return CATEGORIES.find((c) => c.code === code) ?? { code, slug: code.toLowerCase(), ko: code, en: code, back: "목록으로" };
}

export function categoryBySlug(slug: string | undefined): CategoryInfo | undefined {
  return CATEGORIES.find((c) => c.slug === slug);
}
