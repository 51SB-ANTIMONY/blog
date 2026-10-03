package kr.antimony.accounts.service;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.exception.SocialLoginException;
import kr.antimony.accounts.repository.UserIdentityRepository;
import kr.antimony.accounts.repository.UserRepository;
import kr.antimony.accounts.support.TestUsers;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserServiceTest {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserIdentityRepository userIdentityRepository;

    @BeforeEach
    void setUp() {
        userIdentityRepository.deleteAll();
        userRepository.deleteAll();
    }

    @DisplayName("소셜 가입: 처음 로그인하면 회원과 소셜 계정 연결을 만든다. 아이디는 자동 생성, 닉네임은 제공자의 이름, 비밀번호는 없다.")
    @Test
    void loginWithSocial_new() {
        User user = userService.loginWithSocial("google", "google-sub-1", "a@gmail.com", "홍길동");

        assertThat(user.getUid()).matches("^google_[0-9a-f]{8}$");
        assertThat(user.getUid()).matches("^[a-z0-9_]{4,20}$");   // 일반 회원가입의 아이디 규칙도 만족
        assertThat(user.getNickname()).isEqualTo("홍길동");
        assertThat(user.getPassword()).isNull();
        assertThat(userIdentityRepository.findByProviderAndProviderSubject("google", "google-sub-1"))
                .hasValueSatisfying(identity -> assertThat(identity.getUser().getId()).isEqualTo(user.getId()));
    }

    @DisplayName("소셜 로그인: 같은 소셜 계정(sub)이면 이메일이 바뀌어도 같은 회원으로 로그인되고, 닉네임은 바뀌지 않는다.")
    @Test
    void loginWithSocial_sameSubject() {
        User first = userService.loginWithSocial("google", "google-sub-1", "a@gmail.com", "홍길동");
        User second = userService.loginWithSocial("google", "google-sub-1", "changed@gmail.com", "다른이름");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getNickname()).isEqualTo("홍길동");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @DisplayName("소셜 로그인: 같은 이메일로 가입한 회원이 있어도 자동으로 합치지 않고 거부한다.")
    @Test
    void loginWithSocial_emailAlreadyRegistered() {
        TestUsers.localUser(userRepository, passwordEncoder, "gildong", "a@gmail.com", "길동", "password123");

        assertThatThrownBy(() -> userService.loginWithSocial("google", "google-sub-1", "a@gmail.com", "홍길동"))
                .isInstanceOf(SocialLoginException.class)
                .extracting("code").isEqualTo("email_already_registered");

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userIdentityRepository.count()).isZero();
    }

    @DisplayName("명시적 연결: 아이디로 로그인한 회원에 소셜 계정을 연결하면, 이후 그 소셜 계정으로 같은 회원에 로그인된다.")
    @Test
    void linkSocial() {
        User local = TestUsers.localUser(userRepository, passwordEncoder, "gildong", "a@gmail.com", "길동", "password123");

        userService.linkSocial(local.getId(), "google", "google-sub-1");
        User loggedIn = userService.loginWithSocial("google", "google-sub-1", "a@gmail.com", "홍길동");

        assertThat(loggedIn.getId()).isEqualTo(local.getId());
        assertThat(loggedIn.getNickname()).isEqualTo("길동");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @DisplayName("명시적 연결: 이미 다른 회원에 연결된 소셜 계정은 연결할 수 없다.")
    @Test
    void linkSocial_linkedToAnotherUser() {
        userService.loginWithSocial("google", "google-sub-1", "a@gmail.com", "홍길동");
        User local = TestUsers.localUser(userRepository, passwordEncoder, "gildong", "b@antimony.kr", "길동", "password123");

        assertThatThrownBy(() -> userService.linkSocial(local.getId(), "google", "google-sub-1"))
                .isInstanceOf(SocialLoginException.class)
                .extracting("code").isEqualTo("linked_to_another_user");
    }

    @DisplayName("명시적 연결: 이미 이 회원에 연결된 소셜 계정을 다시 연결해도 그대로 둔다.")
    @Test
    void linkSocial_alreadyLinked() {
        User local = TestUsers.localUser(userRepository, passwordEncoder, "gildong", "a@gmail.com", "길동", "password123");

        userService.linkSocial(local.getId(), "google", "google-sub-1");
        userService.linkSocial(local.getId(), "google", "google-sub-1");

        assertThat(userIdentityRepository.count()).isEqualTo(1);
    }

    @DisplayName("소셜 가입: 같은 닉네임의 회원이 이미 있으면 닉네임 뒤에 숫자 4자리를 붙인다.")
    @Test
    void loginWithSocial_nicknameCollision() {
        TestUsers.localUser(userRepository, passwordEncoder, "gildong", "gildong@antimony.kr", "홍길동", "password123");

        User user = userService.loginWithSocial("google", "google-sub-1", "b@gmail.com", "홍길동");

        assertThat(user.getNickname()).matches("^홍길동[0-9]{4}$");
    }

    @DisplayName("소셜 가입: 이름이 없으면 이메일의 @ 앞부분을 닉네임으로 쓴다.")
    @Test
    void loginWithSocial_noName() {
        User user = userService.loginWithSocial("google", "google-sub-1", "someone@gmail.com", null);

        assertThat(user.getNickname()).isEqualTo("someone");
    }
}
