package kr.antimony.accounts.exception;

import lombok.Getter;

// 소셜 로그인을 진행할 수 없을 때. OAuth2UserCustomService가 로그인 실패로 바꿔서 로그인 화면에 메시지를 보여 준다.
@Getter
public class SocialLoginException extends RuntimeException {
    private final String code;

    public SocialLoginException(String code, String message) {
        super(message);
        this.code = code;
    }

    // 같은 이메일로 가입한 회원이 이미 있는데, 그 회원에 연결되지 않은 소셜 계정으로 로그인한 경우
    public static SocialLoginException emailAlreadyRegistered() {
        return new SocialLoginException("email_already_registered",
                "이미 같은 이메일로 가입한 계정이 있습니다. 기존 계정으로 로그인한 뒤 소셜 계정을 연결해 주세요.");
    }

    // 연결하려는 소셜 계정이 이미 다른 회원에 연결되어 있는 경우
    public static SocialLoginException linkedToAnotherUser() {
        return new SocialLoginException("linked_to_another_user",
                "이 소셜 계정은 이미 다른 회원에 연결되어 있습니다.");
    }
}
