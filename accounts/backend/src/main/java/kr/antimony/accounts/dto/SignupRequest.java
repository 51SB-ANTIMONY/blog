package kr.antimony.accounts.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입 신청. 규칙과 메시지는 회원가입 화면(프로토타입)과 같다.
public record SignupRequest(
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Pattern(regexp = "^[a-z0-9]{4,16}$", message = "영문 소문자·숫자 4–16자로 입력해 주세요.")
        String uid,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 16, message = "닉네임은 2–16자로 입력해 주세요.")
        String nickname,

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "올바른 이메일 형식이 아니에요.")
        @Size(max = 255, message = "올바른 이메일 형식이 아니에요.")
        String email,

        @AssertTrue(message = "필수 약관에 동의해 주세요.")
        boolean termsAgreed,

        @AssertTrue(message = "필수 약관에 동의해 주세요.")
        boolean privacyAgreed,

        boolean marketingAgreed
) {
}
