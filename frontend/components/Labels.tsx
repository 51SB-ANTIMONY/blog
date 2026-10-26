import { categoryOf } from "@/lib/categories";
import type { Article, Category } from "@/lib/types";
import { prayerLabel } from "@/lib/views";
import { Bi } from "./Bi";

export function CategoryLabel({ code }: { code: Category }) {
  const c = categoryOf(code);
  return <Bi ko={c.ko} en={c.en} />;
}

// 글의 이름표: 기도편지는 "2026년 10월 기도편지", 나머지는 카테고리 이름
export function ArticleLabel({ article }: { article: Article }) {
  if (article.category === "PRAYER") {
    const label = prayerLabel(article);
    return <Bi ko={label.ko} en={label.en} />;
  }
  return <CategoryLabel code={article.category} />;
}

// 닉네임은 번역하지 않는다. 길면 한 줄에서 자른다 (CSS .by)
export function Author({ name }: { name: string }) {
  return (
    <span className="by" title={name}>
      {name}
    </span>
  );
}
