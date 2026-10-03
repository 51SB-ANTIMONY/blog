package kr.antimony.accounts.service;

import kr.antimony.accounts.domain.EmailVerification;
import kr.antimony.accounts.domain.VerificationPurpose;
import kr.antimony.accounts.exception.VerificationException;
import kr.antimony.accounts.mail.VerificationMailSender;
import kr.antimony.accounts.repository.EmailVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

// 이메일 인증번호 발급과 확인
// - 6자리 숫자, 3분 동안 유효 (화면의 "남은 시간 03:00")
// - DB에는 원문이 아니라 SHA-256 해시만 저장한다
// - 5번 틀리면 그 인증번호는 더 쓸 수 없고, 재전송해야 한다
@RequiredArgsConstructor
@Service
public class VerificationCodeService {
    public static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository verificationRepository;
    private final VerificationMailSender mailSender;
    private final Clock clock;

    // 새 인증번호를 만들어 메일로 보낸다. 같은 목적·이메일의 이전 인증번호는 버린다.
    @Transactional
    public void issue(VerificationPurpose purpose, String email) {
        verificationRepository.deleteByPurposeAndEmail(purpose, email);
        verificationRepository.flush();

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        verificationRepository.save(new EmailVerification(purpose, email, hash(code), Instant.now(clock).plus(CODE_TTL)));
        mailSender.send(email, purpose, code);
    }

    // 인증번호를 확인한다. 맞으면 확인된 인증 정보를 돌려주고, 틀리면 이유에 맞는 예외를 던진다.
    // noRollbackFor: 틀린 횟수 기록은 예외가 나도 저장되어야 한다.
    @Transactional(noRollbackFor = VerificationException.class)
    public EmailVerification verify(VerificationPurpose purpose, String email, String code) {
        EmailVerification verification = verificationRepository.findByPurposeAndEmail(purpose, email)
                .orElseThrow(VerificationException::expired);

        if (Instant.now(clock).isAfter(verification.getExpiresAt())) {
            throw VerificationException.expired();
        }
        if (verification.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw VerificationException.tooManyAttempts();
        }
        if (!MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.US_ASCII),
                verification.getCodeHash().getBytes(StandardCharsets.US_ASCII))) {
            verification.recordFailure();
            throw VerificationException.wrong();
        }
        return verification;
    }

    private static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
