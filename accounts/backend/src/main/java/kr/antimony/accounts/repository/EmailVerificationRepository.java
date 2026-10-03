package kr.antimony.accounts.repository;

import kr.antimony.accounts.domain.EmailVerification;
import kr.antimony.accounts.domain.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByPurposeAndEmail(VerificationPurpose purpose, String email);
    Optional<EmailVerification> findByResetToken(String resetToken);
    void deleteByPurposeAndEmail(VerificationPurpose purpose, String email);
}
