package kr.antimony.accounts.config.oauth;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.repository.UserIdentityRepository;
import kr.antimony.accounts.repository.UserRepository;
import kr.antimony.accounts.security.AccountUserDetails;
import kr.antimony.accounts.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OAuth2UserCustomServiceTest {

    @Autowired
    OAuth2UserCustomService oAuth2UserCustomService;

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserIdentityRepository userIdentityRepository;

    @BeforeEach
    void setUp() {
        userIdentityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @DisplayName("로그인하지 않은 상태에서 소셜 로그인을 하면 새 회원으로 가입된다.")
    @Test
    void notLoggedIn_registers() {
        User user = oAuth2UserCustomService.resolveUser(null, "google", "google-sub-1", "a@gmail.com", "홍길동");

        assertThat(user.getUid()).startsWith("google_");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @DisplayName("아이디·비밀번호로 로그인한 상태에서 소셜 로그인을 하면 그 회원에 소셜 계정이 연결된다.")
    @Test
    void loggedInWithPassword_links() {
        User local = userService.signup(new SignupRequest("gildong", "a@gmail.com", "길동", "password123"));
        // 폼 로그인에 성공하면 Authentication의 principal은 UserDetailService가 돌려준 AccountUserDetails다.
        AccountUserDetails details = new AccountUserDetails(local);
        var current = UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());

        User user = oAuth2UserCustomService.resolveUser(current, "google", "google-sub-1", "a@gmail.com", "홍길동");

        assertThat(user.getId()).isEqualTo(local.getId());
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userIdentityRepository.findByProviderAndProviderSubject("google", "google-sub-1"))
                .hasValueSatisfying(identity -> assertThat(identity.getUser().getId()).isEqualTo(local.getId()));
    }
}
