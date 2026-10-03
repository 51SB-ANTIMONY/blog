package kr.antimony.blog.controller;

import kr.antimony.blog.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequiredArgsConstructor
@RestController
public class MeApiController {
    private final UserService userService;

    // 로그인한 사용자의 blog 닉네임. 로그인하지 않았으면 보안 설정에서 401로 응답한다
    @GetMapping("/api/me")
    public Map<String, String> me(@AuthenticationPrincipal OidcUser oidcUser) {
        return Map.of("nickname", userService.findByAccountsSub(oidcUser.getSubject()).getNickname());
    }
}
