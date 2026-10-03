package kr.antimony.accounts.support;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.repository.UserRepository;
import kr.antimony.accounts.security.AccountUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

// 테스트에서 회원을 바로 만드는 도우미
public final class TestUsers {

    private TestUsers() {
    }

    public static User localUser(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 String uid, String email, String nickname, String rawPassword) {
        return userRepository.save(User.builder()
                .uid(uid)
                .email(email)
                .nickname(nickname)
                .password(rawPassword == null ? null : passwordEncoder.encode(rawPassword))
                .build());
    }

    // 아이디·비밀번호로 로그인한 상태의 Authentication
    public static Authentication loggedIn(User user) {
        AccountUserDetails details = new AccountUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }
}
