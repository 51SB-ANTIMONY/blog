-- 카테고리 '시냇가에 심은 나무'(TREE)를 '뉴스'(NEWS)로 바꾼다. 이미 쓴 글도 새 이름으로 옮긴다.
UPDATE article SET category = 'NEWS' WHERE category = 'TREE';
