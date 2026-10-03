import type { Category } from "./types";

// 프로토타입의 카테고리. 주소에는 소문자(daily 등)를, API에는 대문자(DAILY 등)를 쓴다.
export const CATEGORIES: { code: Category; slug: string; label: string; desc: string }[] = [
  { code: "DAILY", slug: "daily", label: "일상", desc: "하루하루의 작은 장면들. 먹고, 걷고, 만나고, 생각한 것들을 남깁니다." },
  { code: "STUDY", slug: "study", label: "학습", desc: "공부하며 정리한 노트와 배운 것들. 나중의 나를 위한 기록입니다." },
  { code: "TREE", slug: "tree", label: "시냇가에 심은 나무", desc: "말씀을 묵상하며 뿌리 내리는 시간. 신앙과 삶에 대한 고백입니다." },
];

export function categoryLabel(code: Category): string {
  return CATEGORIES.find((c) => c.code === code)?.label ?? code;
}

export function categoryBySlug(slug: string | undefined) {
  return CATEGORIES.find((c) => c.slug === slug);
}
