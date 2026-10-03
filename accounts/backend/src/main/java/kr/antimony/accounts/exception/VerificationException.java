package kr.antimony.accounts.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// 인증번호 확인 실패. errors.code에 화면에 보여 줄 메시지를 담는다.
public class VerificationException extends ApiException {

    private VerificationException(String message) {
        super(HttpStatus.BAD_REQUEST, message, Map.of("code", message));
    }

    public static VerificationException wrong() {
        return new VerificationException("인증번호가 일치하지 않아요. 다시 확인해 주세요.");
    }

    public static VerificationException expired() {
        return new VerificationException("인증 시간이 만료됐어요. 인증번호를 재전송해 주세요.");
    }

    public static VerificationException tooManyAttempts() {
        return new VerificationException("인증번호를 여러 번 틀렸어요. 인증번호를 재전송해 주세요.");
    }
}
