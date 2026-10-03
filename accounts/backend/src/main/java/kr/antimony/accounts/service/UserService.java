package kr.antimony.accounts.service;

import kr.antimony.accounts.domain.PendingSignup;
import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.domain.UserIdentity;
import kr.antimony.accounts.dto.MeResponse;
import kr.antimony.accounts.exception.BadRequestException;
import kr.antimony.accounts.exception.ConflictException;
import kr.antimony.accounts.exception.DuplicateUserException;
import kr.antimony.accounts.exception.SocialLoginException;
import kr.antimony.accounts.repository.UserIdentityRepository;
import kr.antimony.accounts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class UserService {
    private static final int NICKNAME_MAX_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    // 이메일 인증을 마친 가입 신청으로 회원을 만든다.
    // 신청 뒤 인증 전에 다른 사람이 같은 값으로 먼저 가입했을 수 있으므로 한 번 더 확인한다.
    @Transactional
    public User createLocalUser(PendingSignup pending) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (userRepository.existsByUid(pending.getUid())) {
            errors.put("uid", "이미 사용 중인 아이디예요.");
        }
        if (userRepository.existsByEmail(pending.getEmail())) {
            errors.put("email", "이미 가입된 이메일이에요.");
        }
        if (userRepository.existsByNickname(pending.getNickname())) {
            errors.put("nickname", "이미 사용 중인 닉네임이에요.");
        }
        if (!errors.isEmpty()) {
            throw new DuplicateUserException(errors);
        }

        return userRepository.save(User.builder()
                .uid(pending.getUid())
                .email(pending.getEmail())
                .nickname(pending.getNickname())
                .password(pending.getPasswordHash())
                .marketingAgreed(pending.isMarketingAgreed())
                .build());
    }

    // 소셜 로그인. 이 소셜 계정(provider + providerSubject)에 연결된 회원을 찾고, 없으면 새 회원으로 가입시킨다.
    // 이메일이 아니라 제공자의 계정 고유 번호로 찾으므로, 소셜 계정의 이메일이 바뀌어도 같은 회원으로 로그인된다.
    @Transactional
    public User loginWithSocial(String provider, String providerSubject, String email, String name) {
        return userIdentityRepository.findByProviderAndProviderSubject(provider, providerSubject)
                .map(UserIdentity::getUser)
                .orElseGet(() -> registerSocialUser(provider, providerSubject, email, name));
    }

    // 아이디는 "제공자_임의값"으로 자동으로 만들고, 닉네임은 제공자가 준 이름으로 정한다. 이후 로그인에서는 바꾸지 않는다.
    private User registerSocialUser(String provider, String providerSubject, String email, String name) {
        // 같은 이메일의 회원이 있어도 자동으로 합치지 않는다. 이메일이 같다는 것만으로 같은 사람이라고 확신할 수 없기 때문이다.
        // 기존 계정으로 로그인한 뒤 연결(linkSocial)해야 한다.
        if (userRepository.existsByEmail(email)) {
            throw SocialLoginException.emailAlreadyRegistered();
        }
        User user = userRepository.save(User.builder()
                .uid(generateUid(provider))
                .email(email)
                .nickname(uniqueNickname(defaultNickname(name, email)))
                .build());
        userIdentityRepository.save(new UserIdentity(user, provider, providerSubject));
        return user;
    }

    // 명시적 연결. 이미 로그인한 회원(userId)에 소셜 계정을 연결한다.
    @Transactional
    public User linkSocial(Long userId, String provider, String providerSubject) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("로그인한 회원을 찾을 수 없습니다: " + userId));

        return userIdentityRepository.findByProviderAndProviderSubject(provider, providerSubject)
                .map(identity -> {
                    if (!identity.getUser().getId().equals(user.getId())) {
                        throw SocialLoginException.linkedToAnotherUser();
                    }
                    return user;   // 이미 이 회원에 연결되어 있음
                })
                .orElseGet(() -> {
                    userIdentityRepository.save(new UserIdentity(user, provider, providerSubject));
                    return user;
                });
    }

    // 내 계정 정보
    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("로그인한 회원을 찾을 수 없습니다: " + userId));
        List<MeResponse.LinkedIdentity> identities = userIdentityRepository.findByUserId(userId).stream()
                .map(identity -> new MeResponse.LinkedIdentity(identity.getProvider(), identity.getCreatedAt()))
                .toList();
        return new MeResponse(user.getId(), user.getUid(), user.getEmail(), user.getNickname(), user.hasPassword(), identities);
    }

    // 소셜 계정 연결 해제. 로그인할 방법이 하나도 남지 않게 되면 거부한다.
    @Transactional
    public void unlinkSocial(Long userId, String provider) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("로그인한 회원을 찾을 수 없습니다: " + userId));
        UserIdentity identity = userIdentityRepository.findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new BadRequestException("연결되지 않은 계정이에요."));

        if (!user.hasPassword() && userIdentityRepository.findByUserId(userId).size() == 1) {
            throw new ConflictException("마지막 로그인 수단은 해제할 수 없어요. 비밀번호 찾기로 비밀번호를 먼저 설정해 주세요.");
        }
        userIdentityRepository.delete(identity);
    }

    // 예: google_3f9a2c1b
    private String generateUid(String provider) {
        String uid;
        do {
            uid = provider + "_" + randomHex(4);
        } while (userRepository.existsByUid(uid));
        return uid;
    }

    // 이름 정보가 없으면 이메일의 @ 앞부분을 쓴다. 닉네임 최대 길이를 넘으면 자른다.
    private static String defaultNickname(String name, String email) {
        String base = (name != null && !name.isBlank()) ? name.strip() : email.substring(0, email.indexOf('@'));
        return base.length() > NICKNAME_MAX_LENGTH ? base.substring(0, NICKNAME_MAX_LENGTH) : base;
    }

    // 닉네임이 이미 있거나 너무 짧으면 뒤에 숫자 4자리를 붙인다. 예: 홍길동 → 홍길동4821
    private String uniqueNickname(String base) {
        if (base.length() >= 2 && !userRepository.existsByNickname(base)) {
            return base;
        }
        String prefix = base.length() > NICKNAME_MAX_LENGTH - 4 ? base.substring(0, NICKNAME_MAX_LENGTH - 4) : base;
        String nickname;
        do {
            nickname = prefix + String.format("%04d", RANDOM.nextInt(10_000));
        } while (userRepository.existsByNickname(nickname));
        return nickname;
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}
