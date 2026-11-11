package kr.antimony.blog.exception;

import org.springframework.http.HttpStatus;

// 댓글 규칙에 맞지 않는 요청 (답글에 답글 달기, 삭제된 댓글 수정). 400 (Bad Request)
public class InvalidCommentException extends ApiException {
    public InvalidCommentException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
