package kr.antimony.accounts.domain;

// 이메일 인증번호를 보내는 목적
public enum VerificationPurpose {
    SIGNUP,          // 회원가입 이메일 인증
    FIND_ID,         // 아이디 찾기
    RESET_PASSWORD   // 비밀번호 재설정
}
