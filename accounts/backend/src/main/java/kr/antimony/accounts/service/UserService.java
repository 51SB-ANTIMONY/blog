package kr.antimony.accounts.service;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.exception.DuplicateUserException;
import kr.antimony.accounts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class UserService {
    private static final int NICKNAME_MAX_LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 아이디·비밀번호 회원가입. 아이디, 이메일, 닉네임이 이미 있으면 필드별 메시지를 담아 409로 응답한다.
    @Transactional
    public User signup(SignupRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (userRepository.existsByUid(request.getUid())) {
            errors.put("uid", "이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            errors.put("email", "이미 가입된 이메일입니다.");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            errors.put("nickname", "이미 사용 중인 닉네임입니다.");
        }
        if (!errors.isEmpty()) {
            throw new DuplicateUserException(errors);
        }

        return userRepository.save(User.builder()
                .uid(request.getUid())
                .email(request.getEmail())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))
                .build());
    }

    // 소셜 로그인 회원가입. 처음 로그인하면 회원을 만들고, 이미 있으면 그대로 돌려준다.
    // 아이디는 "제공자_임의값"으로 자동으로 만들고, 닉네임은 제공자가 준 이름으로 정한다. 이후 로그인에서는 바꾸지 않는다.
    @Transactional
    public User registerOAuthUser(String provider, String email, String name) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .uid(generateUid(provider))
                        .email(email)
                        .nickname(uniqueNickname(defaultNickname(name, email)))
                        .build()));
    }

    // 예: google_3f9a2c1b (아이디 규칙 ^[a-z0-9_]{4,20}$ 을 만족한다)
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
