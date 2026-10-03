package kr.antimony.accounts.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 비밀번호 찾기 1단계: 아이디와 가입한 이메일
public record PasswordResetCodeRequest(
        @NotBlank(message = "아이디를 입력해 주세요.")
        String uid,

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아니에요.")
        String email
) {
}
