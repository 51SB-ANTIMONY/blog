package kr.antimony.blog.config.oauth;

import kr.antimony.blog.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

// accounts 로그인이 끝나고 ID 토큰 검증까지 마친 뒤 호출된다.
// ID 토큰의 sub로 blog 회원을 찾거나 만들고, 닉네임을 맞춘다.
@RequiredArgsConstructor
@Service
public class AccountsOidcUserService extends OidcUserService {

    private final UserService userService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        userService.syncFromAccounts(oidcUser.getSubject(), nicknameOf(oidcUser));
        return oidcUser;
    }

    // accounts는 profile 범위에서 nickname 클레임을 준다. 없으면 아이디(preferred_username), 그것도 없으면 sub를 쓴다.
    static String nicknameOf(OidcUser oidcUser) {
        String nickname = oidcUser.getClaimAsString("nickname");
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        if (oidcUser.getPreferredUsername() != null) {
            return oidcUser.getPreferredUsername();
        }
        return oidcUser.getSubject();
    }
}
