package kr.antimony.blog.exception;

import org.springframework.http.HttpStatus;

public class CommentForbiddenException extends ApiException {
    public CommentForbiddenException() {
        super(HttpStatus.FORBIDDEN, "댓글 작성자만 수정하거나 삭제할 수 있습니다.");
    }
}
