package kr.antimony.accounts.config.oauth;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.exception.SocialLoginException;
import kr.antimony.accounts.security.AccountOAuth2User;
import kr.antimony.accounts.security.AccountUserDetails;
import kr.antimony.accounts.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class OAuth2UserCustomService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // provider: application.yml에 등록한 이름 (예: google)
        // providerSubject: 제공자의 계정 고유 번호. Google은 sub (Spring Security의 Google 기본 설정 user-name-attribute)
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerSubject = oAuth2User.getName();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        User user;
        try {
            user = resolveUser(SecurityContextHolder.getContext().getAuthentication(),
                    provider, providerSubject, (String) attributes.get("email"), (String) attributes.get("name"));
        } catch (SocialLoginException e) {
            // 로그인 실패로 바꾼다. 실패 처리(SecurityConfig)가 오류 코드를 붙여 로그인 화면으로 보낸다.
            throw new OAuth2AuthenticationException(new OAuth2Error(e.getCode()), e.getMessage(), e);
        }

        // 로그인 정보(principal)에 우리 회원 id를 담는다. 토큰의 sub가 이 id가 된다.
        return new AccountOAuth2User(user.getId(), attributes, oAuth2User.getAuthorities());
    }

    // 아이디·비밀번호로 이미 로그인한 상태에서 소셜 로그인을 하면 → 그 회원에 소셜 계정을 연결한다. (명시적 연결)
    // 로그인하지 않은 상태라면 → 소셜 계정으로 로그인한다. 처음이면 가입시킨다.
    User resolveUser(Authentication current, String provider, String providerSubject, String email, String name) {
        if (current != null && current.isAuthenticated() && current.getPrincipal() instanceof AccountUserDetails localUser) {
            return userService.linkSocial(localUser.getUid(), provider, providerSubject);
        }
        return userService.loginWithSocial(provider, providerSubject, email, name);
    }
}
