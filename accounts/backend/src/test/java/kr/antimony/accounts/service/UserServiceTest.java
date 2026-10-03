package kr.antimony.accounts.service;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserServiceTest {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @DisplayName("소셜 가입: 아이디는 제공자_임의값으로 만들고, 닉네임은 제공자가 준 이름으로 정한다. 비밀번호는 없다.")
    @Test
    void registerOAuthUser_new() {
        User user = userService.registerOAuthUser("google", "a@gmail.com", "홍길동");

        assertThat(user.getUid()).matches("^google_[0-9a-f]{8}$");
        assertThat(user.getUid()).matches("^[a-z0-9_]{4,20}$");   // 일반 회원가입의 아이디 규칙도 만족
        assertThat(user.getNickname()).isEqualTo("홍길동");
        assertThat(user.getPassword()).isNull();
    }

    @DisplayName("소셜 가입: 이미 가입한 이메일이면 새로 만들지 않고 기존 회원을 돌려준다. 닉네임도 바꾸지 않는다.")
    @Test
    void registerOAuthUser_existing() {
        User first = userService.registerOAuthUser("google", "a@gmail.com", "홍길동");
        User second = userService.registerOAuthUser("google", "a@gmail.com", "다른이름");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getNickname()).isEqualTo("홍길동");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @DisplayName("소셜 가입: 같은 이름의 회원이 이미 있으면 닉네임 뒤에 숫자 4자리를 붙인다.")
    @Test
    void registerOAuthUser_nicknameCollision() {
        userService.signup(new SignupRequest("gildong", "gildong@antimony.kr", "홍길동", "password123"));

        User user = userService.registerOAuthUser("google", "b@gmail.com", "홍길동");

        assertThat(user.getNickname()).matches("^홍길동[0-9]{4}$");
    }

    @DisplayName("소셜 가입: 이름이 없으면 이메일의 @ 앞부분을 닉네임으로 쓴다.")
    @Test
    void registerOAuthUser_noName() {
        User user = userService.registerOAuthUser("google", "someone@gmail.com", null);

        assertThat(user.getNickname()).isEqualTo("someone");
    }
}
