package kr.antimony.accounts.service;

import kr.antimony.accounts.domain.PendingSignup;
import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.domain.VerificationPurpose;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.dto.SignupStartResponse;
import kr.antimony.accounts.dto.UidAvailabilityResponse;
import kr.antimony.accounts.exception.BadRequestException;
import kr.antimony.accounts.exception.DuplicateUserException;
import kr.antimony.accounts.exception.VerificationException;
import kr.antimony.accounts.repository.EmailVerificationRepository;
import kr.antimony.accounts.repository.PendingSignupRepository;
import kr.antimony.accounts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// 회원가입: 신청 → 이메일 인증 → 회원 생성
@RequiredArgsConstructor
@Service
public class SignupService {
    // 이메일 인증을 하지 않은 신청은 이 시간이 지나면 다른 사람의 같은 아이디·닉네임 신청을 막지 않는다.
    private static final Duration PENDING_HOLD = Duration.ofHours(1);
    private static final String UID_PATTERN = "^[a-z0-9]{4,16}$";

    private final UserRepository userRepository;
    private final PendingSignupRepository pendingSignupRepository;
    private final EmailVerificationRepository verificationRepository;
    private final VerificationCodeService verificationCodeService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    // 아이디 중복확인
    @Transactional(readOnly = true)
    public UidAvailabilityResponse checkUid(String uid, String email) {
        if (uid == null || !uid.matches(UID_PATTERN)) {
            return new UidAvailabilityResponse("invalid", "영문 소문자·숫자 4–16자로 입력해 주세요.");
        }
        if (userRepository.existsByUid(uid) || pendingSignupRepository.existsByUidAndEmailNotAndRequestedAtAfter(
                uid, email == null ? "" : email, Instant.now(clock).minus(PENDING_HOLD))) {
            return new UidAvailabilityResponse("taken", "이미 사용 중인 아이디예요.");
        }
        return new UidAvailabilityResponse("ok", "사용 가능한 아이디예요.");
    }

    // 가입 신청. 입력값을 저장해 두고 이메일로 인증번호를 보낸다. 회원은 인증 후에 만든다.
    @Transactional
    public SignupStartResponse requestSignup(SignupRequest request) {
        Instant now = Instant.now(clock);
        Instant holdAfter = now.minus(PENDING_HOLD);

        Map<String, String> errors = new LinkedHashMap<>();
        if (userRepository.existsByUid(request.uid())
                || pendingSignupRepository.existsByUidAndEmailNotAndRequestedAtAfter(request.uid(), request.email(), holdAfter)) {
            errors.put("uid", "이미 사용 중인 아이디예요.");
        }
        if (userRepository.existsByEmail(request.email())) {
            errors.put("email", "이미 가입된 이메일이에요.");
        }
        if (userRepository.existsByNickname(request.nickname())
                || pendingSignupRepository.existsByNicknameAndEmailNotAndRequestedAtAfter(request.nickname(), request.email(), holdAfter)) {
            errors.put("nickname", "이미 사용 중인 닉네임이에요.");
        }
        if (!errors.isEmpty()) {
            throw new DuplicateUserException(errors);
        }

        // 같은 이메일로 다시 신청하면("다시 가입하기") 이전 신청을 바꾼다.
        pendingSignupRepository.deleteByEmail(request.email());
        pendingSignupRepository.flush();
        pendingSignupRepository.save(new PendingSignup(request.email(), request.uid(), request.nickname(),
                passwordEncoder.encode(request.password()), request.marketingAgreed(), now));

        verificationCodeService.issue(VerificationPurpose.SIGNUP, request.email());
        return new SignupStartResponse(request.email(), VerificationCodeService.CODE_TTL.toSeconds());
    }

    // 인증번호 재전송
    @Transactional
    public long resendSignupCode(String email) {
        if (pendingSignupRepository.findByEmail(email).isEmpty()) {
            throw new BadRequestException("가입 신청을 찾을 수 없어요. 다시 가입해 주세요.");
        }
        verificationCodeService.issue(VerificationPurpose.SIGNUP, email);
        return VerificationCodeService.CODE_TTL.toSeconds();
    }

    // 이메일 인증. 맞으면 회원을 만들고 신청을 지운다.
    @Transactional(noRollbackFor = VerificationException.class)
    public User verifySignup(String email, String code) {
        PendingSignup pending = pendingSignupRepository.findByEmail(email)
                .orElseThrow(VerificationException::expired);
        verificationCodeService.verify(VerificationPurpose.SIGNUP, email, code);

        User user = userService.createLocalUser(pending);
        pendingSignupRepository.delete(pending);
        verificationRepository.deleteByPurposeAndEmail(VerificationPurpose.SIGNUP, email);
        return user;
    }
}
