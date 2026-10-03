package kr.antimony.accounts.dto;

// 찾은 아이디. 개인정보 보호를 위해 일부를 가린다. (예: an******01)
public record FindIdResponse(String maskedUid) {
}
