package kr.antimony.blog.service;

import lombok.RequiredArgsConstructor;
import kr.antimony.blog.domain.User;
import kr.antimony.blog.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;

    // accounts에서 로그인하고 돌아올 때 호출한다.
    // 처음 보는 sub면 blog 회원을 만들고, 이미 있으면 닉네임을 accounts의 최신 값으로 바꾼다.
    @Transactional
    public User syncFromAccounts(String accountsSub, String nickname) {
        return userRepository.findByAccountsSub(accountsSub)
                .map(user -> {
                    user.updateNickname(nickname);
                    return user;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .accountsSub(accountsSub)
                        .nickname(nickname)
                        .build()));
    }

    // 로그인한 사용자(Authentication.getName() = sub)의 blog 회원
    public User findByAccountsSub(String accountsSub) {
        return userRepository.findByAccountsSub(accountsSub)
                .orElseThrow(() -> new IllegalStateException("blog 회원을 찾을 수 없습니다: " + accountsSub));
    }
}
