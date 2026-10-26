export type Category = "DAILY" | "STUDY" | "NEWS" | "PRAYER";

export type Article = {
  id: number;
  title: string;
  // Markdown 본문
  content: string;
  category: Category;
  summary: string | null;
  excerpt: string;
  author: string;
  editable: boolean;
  // 시간대가 붙은 시각 (예: 2026-10-27T08:00:00+09:00)
  createdAt: string;
  updatedAt: string;
};

export type Me = { nickname: string };
