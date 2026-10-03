package kr.antimony.exception;

import org.springframework.http.HttpStatus;

public class ArticleForbiddenException extends ApiException {
    public ArticleForbiddenException() {
        super(HttpStatus.FORBIDDEN, "글 작성자만 수정하거나 삭제할 수 있습니다.");
    }
}
