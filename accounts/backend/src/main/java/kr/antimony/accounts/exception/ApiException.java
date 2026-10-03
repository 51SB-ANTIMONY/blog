package kr.antimony.accounts.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

// 상태 코드와 필드별 에러를 가진 예외. GlobalExceptionHandler가 에러 응답으로 바꾼다.
@Getter
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final Map<String, String> errors;

    protected ApiException(HttpStatus status, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.errors = errors;
    }
}
