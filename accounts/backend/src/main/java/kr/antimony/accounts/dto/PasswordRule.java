package kr.antimony.accounts.dto;

// 비밀번호 규칙: 8자 이상, 영문·숫자·특수문자를 모두 포함 (화면의 "8자 이상 / 영문·숫자 포함 / 특수문자 포함")
public final class PasswordRule {
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$";
    public static final String MESSAGE = "8자 이상, 영문·숫자·특수문자를 모두 포함해 주세요.";

    private PasswordRule() {
    }
}
