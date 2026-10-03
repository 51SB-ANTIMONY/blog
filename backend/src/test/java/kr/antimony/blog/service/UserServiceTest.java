package kr.antimony.blog.service;

import kr.antimony.blog.domain.User;
import kr.antimony.blog.repository.BlogRepository;
import kr.antimony.blog.repository.UserRepository;
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

    @Autowired
    BlogRepository blogRepository;

    @BeforeEach
    void setUp() {
        blogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @DisplayName("처음 로그인한 accounts 회원은 blog 회원으로 만든다.")
    @Test
    void createOnFirstLogin() {
        User user = userService.syncFromAccounts("1001", "작성자");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getAccountsSub()).isEqualTo("1001");
        assertThat(user.getNickname()).isEqualTo("작성자");
    }

    @DisplayName("같은 sub로 다시 로그인하면 같은 회원이고, 닉네임은 accounts의 최신 값으로 바뀐다.")
    @Test
    void sameSubIsSameUser() {
        User first = userService.syncFromAccounts("1001", "예전 닉네임");
        User second = userService.syncFromAccounts("1001", "새 닉네임");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByAccountsSub("1001").get().getNickname()).isEqualTo("새 닉네임");
    }
}
