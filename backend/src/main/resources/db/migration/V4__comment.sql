-- 댓글. parent_id가 NULL이면 일반 댓글, 값이 있으면 대댓글이다 (대댓글은 한 단계까지).
-- 삭제는 행을 남기고 deleted만 바꾼다. content는 삭제할 때 비우므로 NULL을 허용한다.
-- 글을 지우면 그 글의 댓글도 함께 지운다 (ON DELETE CASCADE).
CREATE TABLE comment (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    article_id BIGINT      NOT NULL,
    parent_id  BIGINT,
    author_id  BIGINT      NOT NULL,
    content    TEXT,
    locked     BOOLEAN     NOT NULL,
    deleted    BOOLEAN     NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_article FOREIGN KEY (article_id) REFERENCES article (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_parent  FOREIGN KEY (parent_id)  REFERENCES comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author  FOREIGN KEY (author_id)  REFERENCES users (id)
);
