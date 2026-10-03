package kr.antimony.accounts.config.oauth;

import kr.antimony.accounts.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class OAuth2UserCustomService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);

        // 처음 로그인하면 회원으로 가입시킨다. (provider: application.yml에 등록한 이름. 예: google)
        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = user.getAttributes();
        userService.registerOAuthUser(provider, (String) attributes.get("email"), (String) attributes.get("name"));

        return user;
    }
}
