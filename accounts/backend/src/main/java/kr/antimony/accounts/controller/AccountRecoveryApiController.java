package kr.antimony.accounts.controller;

import jakarta.validation.Valid;
import kr.antimony.accounts.dto.CodeSentResponse;
import kr.antimony.accounts.dto.EmailCodeRequest;
import kr.antimony.accounts.dto.EmailRequest;
import kr.antimony.accounts.dto.FindIdResponse;
import kr.antimony.accounts.dto.PasswordResetCodeRequest;
import kr.antimony.accounts.dto.PasswordResetCompleteRequest;
import kr.antimony.accounts.dto.PasswordResetTokenResponse;
import kr.antimony.accounts.dto.PasswordResetVerifyRequest;
import kr.antimony.accounts.service.AccountRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class AccountRecoveryApiController {
    private final AccountRecoveryService recoveryService;

    // 아이디 찾기 1단계: 인증번호 받기
    @PostMapping("/api/find-id/code")
    public CodeSentResponse sendFindIdCode(@Valid @RequestBody EmailRequest request) {
        return new CodeSentResponse(recoveryService.sendFindIdCode(request.email()));
    }

    // 아이디 찾기 2단계: 인증번호 확인 → 가린 아이디
    @PostMapping("/api/find-id/verify")
    public FindIdResponse verifyFindId(@Valid @RequestBody EmailCodeRequest request) {
        return new FindIdResponse(recoveryService.verifyFindId(request.email(), request.code()));
    }

    // 비밀번호 찾기 1단계: 인증번호 받기
    @PostMapping("/api/password-reset/code")
    public CodeSentResponse sendResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        return new CodeSentResponse(recoveryService.sendResetCode(request.uid(), request.email()));
    }

    // 비밀번호 찾기 2단계: 인증번호 확인 → 일회용 토큰
    @PostMapping("/api/password-reset/verify")
    public PasswordResetTokenResponse verifyReset(@Valid @RequestBody PasswordResetVerifyRequest request) {
        return new PasswordResetTokenResponse(recoveryService.verifyReset(request.uid(), request.email(), request.code()));
    }

    // 비밀번호 찾기 3단계: 새 비밀번호
    @PostMapping("/api/password-reset/complete")
    public ResponseEntity<Void> completeReset(@Valid @RequestBody PasswordResetCompleteRequest request) {
        recoveryService.completeReset(request.resetToken(), request.password());
        return ResponseEntity.noContent().build();
    }
}
