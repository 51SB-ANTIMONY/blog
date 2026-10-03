package kr.antimony.accounts.controller;

import kr.antimony.accounts.dto.MeResponse;
import kr.antimony.accounts.security.AccountPrincipal;
import kr.antimony.accounts.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 회원의 계정 정보. 로그인 방식(아이디·비밀번호, 소셜)과 상관없이 principal에서 회원 id를 꺼낸다.
@RequiredArgsConstructor
@RestController
public class MeApiController {
    private final UserService userService;

    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return userService.me(principal.getUserId());
    }

    // 소셜 계정 연결 해제
    @DeleteMapping("/api/me/identities/{provider}")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String provider) {
        userService.unlinkSocial(principal.getUserId(), provider);
        return ResponseEntity.noContent().build();
    }
}
