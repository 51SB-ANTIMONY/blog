package kr.antimony.accounts.support;

import kr.antimony.accounts.repository.EmailVerificationRepository;
import kr.antimony.accounts.repository.PendingSignupRepository;
import kr.antimony.accounts.repository.UserIdentityRepository;
import kr.antimony.accounts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

// 테스트 사이에 데이터를 지운다. user_identities가 users를 참조하므로 연결부터 지운다.
@RequiredArgsConstructor
@TestComponent
public class DatabaseCleaner {
    private final UserIdentityRepository userIdentityRepository;
    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PendingSignupRepository pendingSignupRepository;

    public void clean() {
        userIdentityRepository.deleteAll();
        userRepository.deleteAll();
        emailVerificationRepository.deleteAll();
        pendingSignupRepository.deleteAll();
    }
}
