package kr.antimony.accounts.dto;

// 가입 신청 접수. 이 이메일로 인증번호를 보냈고, expiresIn초 동안 유효하다.
public record SignupStartResponse(String email, long expiresIn) {
}
