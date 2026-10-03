package kr.antimony.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// 비밀번호 찾기 3단계: 새 비밀번호
public record PasswordResetCompleteRequest(
        @NotBlank
        String resetToken,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
        String password
) {
}
