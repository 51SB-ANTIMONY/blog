package kr.antimony.blog.exception;

import org.springframework.http.HttpStatus;

public class ArticleNotFoundException extends ApiException {
    public ArticleNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다. (id: " + id + ")");
    }
}
