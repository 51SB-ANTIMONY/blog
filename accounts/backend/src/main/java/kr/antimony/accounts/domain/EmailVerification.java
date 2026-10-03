package kr.antimony.accounts.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 이메일로 보낸 6자리 인증번호. 원문이 아니라 해시만 저장한다.
// 목적(purpose)과 이메일 한 쌍에 하나만 유지하고, 다시 보내면 새로 만든다.
@Table(name = "email_verifications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"purpose", "email"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private VerificationPurpose purpose;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // 틀린 횟수. 너무 많이 틀리면 이 인증번호는 더 쓸 수 없다.
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    // 인증번호 확인에 성공한 시각. 비밀번호 재설정은 확인 후 새 비밀번호를 받을 때까지 이 값으로 단계를 이어 간다.
    @Column(name = "verified_at")
    private Instant verifiedAt;

    // 비밀번호 재설정 3단계(새 비밀번호)에서 쓰는 일회용 토큰
    @Column(name = "reset_token", unique = true)
    private String resetToken;

    public EmailVerification(VerificationPurpose purpose, String email, String codeHash, Instant expiresAt) {
        this.purpose = purpose;
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public void recordFailure() {
        this.failedAttempts++;
    }

    public void markVerified(Instant now, String resetToken) {
        this.verifiedAt = now;
        this.resetToken = resetToken;
    }
}
