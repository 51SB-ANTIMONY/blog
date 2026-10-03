package kr.antimony.accounts.config.oauth;

import lombok.RequiredArgsConstructor;
import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@RequiredArgsConstructor
@Service
public class OAuth2UserCustomService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);
        registerIfAbsent(user);

        return user;
    }

    // 처음 로그인하면 회원으로 가입시킨다. 닉네임은 OAuth 제공자의 이름으로 정하고, 이후 로그인에서는 바꾸지 않는다.
    private User registerIfAbsent(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .nickname(defaultNickname(name, email))
                        .build()));
    }

    // 이름 정보가 없으면 이메일의 @ 앞부분을 닉네임으로 쓴다.
    private static String defaultNickname(String name, String email) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return email.substring(0, email.indexOf('@'));
    }
}