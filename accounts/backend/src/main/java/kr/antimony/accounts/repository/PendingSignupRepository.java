package kr.antimony.accounts.repository;

import kr.antimony.accounts.domain.PendingSignup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PendingSignupRepository extends JpaRepository<PendingSignup, Long> {
    Optional<PendingSignup> findByEmail(String email);

    // 다른 이메일로 최근에 신청된 같은 아이디·닉네임이 있는가 (오래된 신청은 무시)
    boolean existsByUidAndEmailNotAndRequestedAtAfter(String uid, String email, Instant after);
    boolean existsByNicknameAndEmailNotAndRequestedAtAfter(String nickname, String email, Instant after);

    void deleteByEmail(String email);
}
