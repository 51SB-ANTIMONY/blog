package kr.antimony.accounts.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 비밀번호 찾기 2단계: 인증번호 확인
public record PasswordResetVerifyRequest(
        @NotBlank(message = "아이디를 입력해 주세요.")
        String uid,

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아니에요.")
        String email,

        @NotBlank(message = "인증번호 6자리를 입력해 주세요.")
        @Pattern(regexp = "^\\d{6}$", message = "인증번호 6자리를 입력해 주세요.")
        String code
) {
}
