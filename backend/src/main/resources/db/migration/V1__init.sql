-- blog 첫 테이블 (3단계까지의 엔티티)
-- MySQL과 H2(MySQL 호환 모드, 로컬·테스트)에서 모두 실행되도록 표준 문법으로 쓴다.

-- blog 회원. 계정 정보는 accounts에 있고, 여기는 accounts의 sub와 닉네임만 둔다.
CREATE TABLE users (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    accounts_sub VARCHAR(255) NOT NULL,
    nickname     VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_accounts_sub UNIQUE (accounts_sub)
);

-- 글
CREATE TABLE article (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NOT NULL,
    category   VARCHAR(20)  NOT NULL,
    summary    VARCHAR(300),
    author_id  BIGINT       NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_article_author FOREIGN KEY (author_id) REFERENCES users (id)
);
