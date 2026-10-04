import type { Category } from "./types";

// 프로토타입의 카테고리. 주소에는 소문자(daily 등)를, API에는 대문자(DAILY 등)를 쓴다.
export const CATEGORIES: { code: Category; slug: string; label: string }[] = [
  { code: "DAILY", slug: "daily", label: "일상" },
  { code: "STUDY", slug: "study", label: "학습" },
  { code: "NEWS", slug: "news", label: "뉴스" },
];

export function categoryLabel(code: Category): string {
  return CATEGORIES.find((c) => c.code === code)?.label ?? code;
}

export function categoryBySlug(slug: string | undefined) {
  return CATEGORIES.find((c) => c.slug === slug);
}
