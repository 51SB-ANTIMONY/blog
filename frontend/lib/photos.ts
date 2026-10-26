// 글 사진. 사진 파일을 public/images/articles/에 넣고, 글 id와 파일 주소를 여기에 적는다.
// 예: 글 /articles/3의 사진이면  3: "/images/articles/3.jpg",
// 사진이 있는 글은 홈 첫 화면, 최근 글 카드, 전체 기록 목록, 글 상세에서 그 사진을 보여 준다.
const PHOTOS: Record<number, string> = {};

export function photoOf(id: number): string | undefined {
  return PHOTOS[id];
}
