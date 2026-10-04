export type Category = "DAILY" | "STUDY" | "NEWS";

export type Article = {
  id: number;
  title: string;
  content: string;
  category: Category;
  summary: string | null;
  excerpt: string;
  author: string;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
};

export type Me = { nickname: string };
