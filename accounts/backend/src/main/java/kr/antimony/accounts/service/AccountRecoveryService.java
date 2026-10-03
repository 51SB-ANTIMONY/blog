package kr.antimony.accounts.service;

import kr.antimony.accounts.domain.EmailVerification;
import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.domain.VerificationPurpose;
import kr.antimony.accounts.exception.BadRequestException;
import kr.antimony.accounts.exception.VerificationException;
import kr.antimony.accounts.repository.EmailVerificationRepository;
import kr.antimony.accounts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

// 아이디 찾기, 비밀번호 재설정
// 가입하지 않은 이메일이어도 "보냈다"고 똑같이 응답한다. 응답의 차이로 어떤 이메일이 가입되어 있는지 알아낼 수 없게 하기 위해서다.
@RequiredArgsConstructor
@Service
public class AccountRecoveryService {
    // 인증번호 확인 후 새 비밀번호를 입력할 수 있는 시간
    private static final Duration RESET_WINDOW = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailVerificationRepository verificationRepository;
    private final VerificationCodeService verificationCodeService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    // 아이디 찾기 1단계: 가입한 이메일로 인증번호
    @Transactional
    public long sendFindIdCode(String email) {
        if (userRepository.existsByEmail(email)) {
            verificationCodeService.issue(VerificationPurpose.FIND_ID, email);
        }
        return VerificationCodeService.CODE_TTL.toSeconds();
    }

    // 아이디 찾기 2단계: 인증번호 확인 → 가린 아이디
    @Transactional(noRollbackFor = VerificationException.class)
    public String verifyFindId(String email, String code) {
        verificationCodeService.verify(VerificationPurpose.FIND_ID, email, code);
        User user = userRepository.findByEmail(email).orElseThrow(VerificationException::expired);
        verificationRepository.deleteByPurposeAndEmail(VerificationPurpose.FIND_ID, email);
        return maskUid(user.getUid());
    }

    // 비밀번호 찾기 1단계: 아이디와 이메일이 같은 회원이면 인증번호
    @Transactional
    public long sendResetCode(String uid, String email) {
        if (userRepository.findByUidAndEmail(uid, email).isPresent()) {
            verificationCodeService.issue(VerificationPurpose.RESET_PASSWORD, email);
        }
        return VerificationCodeService.CODE_TTL.toSeconds();
    }

    // 비밀번호 찾기 2단계: 인증번호 확인 → 새 비밀번호를 설정할 때 쓸 일회용 토큰
    @Transactional(noRollbackFor = VerificationException.class)
    public String verifyReset(String uid, String email, String code) {
        EmailVerification verification = verificationCodeService.verify(VerificationPurpose.RESET_PASSWORD, email, code);
        userRepository.findByUidAndEmail(uid, email).orElseThrow(VerificationException::expired);

        String resetToken = randomToken();
        verification.markVerified(Instant.now(clock), resetToken);
        return resetToken;
    }

    // 비밀번호 찾기 3단계: 새 비밀번호 저장
    @Transactional
    public void completeReset(String resetToken, String newPassword) {
        EmailVerification verification = verificationRepository.findByResetToken(resetToken)
                .filter(v -> v.getVerifiedAt() != null
                        && Instant.now(clock).isBefore(v.getVerifiedAt().plus(RESET_WINDOW)))
                .orElseThrow(() -> new BadRequestException("비밀번호 재설정 시간이 지났어요. 처음부터 다시 진행해 주세요."));

        User user = userRepository.findByEmail(verification.getEmail())
                .orElseThrow(() -> new BadRequestException("비밀번호 재설정 시간이 지났어요. 처음부터 다시 진행해 주세요."));
        user.changePassword(passwordEncoder.encode(newPassword));
        verificationRepository.delete(verification);
    }

    // 앞 2자, 뒤 2자만 보여 준다. (antimony01 → an******01) 짧은 아이디는 앞뒤 1자씩
    static String maskUid(String uid) {
        int keep = uid.length() > 4 ? 2 : 1;
        return uid.substring(0, keep) + "*".repeat(uid.length() - keep * 2) + uid.substring(uid.length() - keep);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
