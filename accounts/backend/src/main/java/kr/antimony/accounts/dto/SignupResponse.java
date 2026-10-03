package kr.antimony.accounts.dto;

import kr.antimony.accounts.domain.User;

// 가입 완료. 비밀번호는 담지 않는다.
public record SignupResponse(Long id, String uid, String email, String nickname) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getUid(), user.getEmail(), user.getNickname());
    }
}
