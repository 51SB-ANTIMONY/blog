package kr.antimony.accounts.dto;

// 인증번호를 보냈다. expiresIn초 동안 유효하다.
public record CodeSentResponse(long expiresIn) {
}
