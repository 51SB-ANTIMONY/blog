package kr.antimony.accounts.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// 아이디, 이메일, 닉네임이 이미 사용 중일 때. errors에 필드별 메시지를 담는다.
public class DuplicateUserException extends ApiException {
    public DuplicateUserException(Map<String, String> errors) {
        super(HttpStatus.CONFLICT, "이미 사용 중인 값이 있습니다.", errors);
    }
}
