package kr.antimony.accounts.dto;

// 아이디 중복확인 결과. status: ok | invalid | taken
public record UidAvailabilityResponse(String status, String message) {
}
