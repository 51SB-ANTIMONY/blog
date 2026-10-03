package kr.antimony.accounts.controller;

import jakarta.validation.Valid;
import kr.antimony.accounts.dto.CodeSentResponse;
import kr.antimony.accounts.dto.EmailCodeRequest;
import kr.antimony.accounts.dto.EmailRequest;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.dto.SignupResponse;
import kr.antimony.accounts.dto.SignupStartResponse;
import kr.antimony.accounts.dto.UidAvailabilityResponse;
import kr.antimony.accounts.service.SignupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class SignupApiController {
    private final SignupService signupService;

    // 아이디 중복확인
    @GetMapping("/api/signup/uid-availability")
    public UidAvailabilityResponse checkUid(@RequestParam String uid,
                                            @RequestParam(required = false) String email) {
        return signupService.checkUid(uid, email);
    }

    // 가입 신청 → 이메일로 인증번호 발송 (202: 접수됨, 아직 회원은 아님)
    @PostMapping("/api/signup")
    public ResponseEntity<SignupStartResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(signupService.requestSignup(request));
    }

    // 이메일 인증 → 회원 생성 (201)
    @PostMapping("/api/signup/verify")
    public ResponseEntity<SignupResponse> verify(@Valid @RequestBody EmailCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SignupResponse.from(signupService.verifySignup(request.email(), request.code())));
    }

    // 인증번호 재전송
    @PostMapping("/api/signup/resend")
    public CodeSentResponse resend(@Valid @RequestBody EmailRequest request) {
        return new CodeSentResponse(signupService.resendSignupCode(request.email()));
    }
}
