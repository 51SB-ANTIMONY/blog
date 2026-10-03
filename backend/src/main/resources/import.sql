-- 예시 작성자. accounts에 없는 sub이므로 이 회원으로는 로그인할 수 없고, 글 목록을 채우는 데만 쓴다.
INSERT INTO users (accounts_sub, nickname) VALUES ('sample', '예시 작성자')
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('제목 1', '내용 1', 'DAILY', NULL, 1, NOW(), NOW())
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('제목 2', '내용 2', 'STUDY', '요약 2', 1, NOW(), NOW())
INSERT INTO article (title, content, category, summary, author_id, created_at, updated_at) VALUES ('제목 3', '내용 3', 'TREE', NULL, 1, NOW(), NOW())
