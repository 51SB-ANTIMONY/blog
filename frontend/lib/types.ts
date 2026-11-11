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

// 댓글. 답글(replies)은 한 층까지만 있다.
export type Comment = {
  id: number;
  // 삭제된 댓글, 또는 볼 수 없는 잠긴 댓글이면 null
  content: string | null;
  // 작성자 닉네임. 삭제된 댓글이면 null
  author: string | null;
  locked: boolean;
  deleted: boolean;
  // 로그인한 사용자가 이 댓글의 작성자인가. 수정·삭제 버튼을 보여 줄지 정한다
  editable: boolean;
  createdAt: string;
  updatedAt: string;
  replies: Comment[];
};
