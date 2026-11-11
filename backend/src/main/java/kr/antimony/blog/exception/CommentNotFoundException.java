package kr.antimony.blog.exception;

import org.springframework.http.HttpStatus;

public class CommentNotFoundException extends ApiException {
    public CommentNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다. (id: " + id + ")");
    }
}
