export type Category = "DAILY" | "STUDY" | "TREE";

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
