package kr.antimony.accounts.controller;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.domain.VerificationPurpose;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.repository.UserRepository;
import kr.antimony.accounts.support.DatabaseCleaner;
import kr.antimony.accounts.support.TestSupportConfig;
import kr.antimony.accounts.support.TestSupportConfig.CapturingMailSender;
import kr.antimony.accounts.support.TestSupportConfig.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSupportConfig.class, DatabaseCleaner.class})
class SignupApiControllerTest {

    private static final String EMAIL = "tester@antimony.kr";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;
    @Autowired CapturingMailSender mailSender;
    @Autowired MutableClock clock;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        mailSender.clear();
        clock.reset();
    }

    private static SignupRequest validRequest() {
        return new SignupRequest("tester01", "pass1234!", "테스터", EMAIL, true, true, false);
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String sentCode() {
        return mailSender.lastCode(VerificationPurpose.SIGNUP, EMAIL);
    }

    @DisplayName("아이디 중복확인: 형식이 틀리면 invalid, 이미 있으면 taken, 아니면 ok")
    @Test
    void uidAvailability() throws Exception {
        userRepository.save(User.builder().uid("taken01").email("x@antimony.kr").nickname("이미있음").build());

        mockMvc.perform(get("/api/signup/uid-availability").param("uid", "AB"))
                .andExpect(jsonPath("$.status").value("invalid"))
                .andExpect(jsonPath("$.message").value("영문 소문자·숫자 4–16자로 입력해 주세요."));
        mockMvc.perform(get("/api/signup/uid-availability").param("uid", "taken01"))
                .andExpect(jsonPath("$.status").value("taken"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디예요."));
        mockMvc.perform(get("/api/signup/uid-availability").param("uid", "fresh01"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.message").value("사용 가능한 아이디예요."));
    }

    @DisplayName("가입: 신청하면 202와 함께 인증번호를 보내고, 인증 전에는 회원이 아니라 로그인할 수 없다. 인증하면 201로 회원이 되고 로그인된다.")
    @Test
    void signupAndVerify() throws Exception {
        postJson("/api/signup", validRequest())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.expiresIn").value(180));

        assertThat(sentCode()).matches("^\\d{6}$");
        assertThat(userRepository.count()).isZero();
        mockMvc.perform(formLogin("/login/password").user("tester01").password("pass1234!"))
                .andExpect(unauthenticated());

        postJson("/api/signup/verify", Map.of("email", EMAIL, "code", sentCode()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uid").value("tester01"))
                .andExpect(jsonPath("$.nickname").value("테스터"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User user = userRepository.findByUid("tester01").orElseThrow();
        assertThat(passwordEncoder.matches("pass1234!", user.getPassword())).isTrue();
        mockMvc.perform(formLogin("/login/password").user("tester01").password("pass1234!"))
                .andExpect(authenticated().withUsername("tester01"));
    }

    @DisplayName("가입: 입력값 규칙에 맞지 않으면 400과 화면과 같은 필드별 메시지를 응답한다.")
    @Test
    void signup_invalid() throws Exception {
        postJson("/api/signup", new SignupRequest("Ab_", "password", "a", "not-an-email", false, true, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.errors.uid").value("영문 소문자·숫자 4–16자로 입력해 주세요."))
                .andExpect(jsonPath("$.errors.password").value("8자 이상, 영문·숫자·특수문자를 모두 포함해 주세요."))
                .andExpect(jsonPath("$.errors.nickname").value("닉네임은 2–16자로 입력해 주세요."))
                .andExpect(jsonPath("$.errors.email").value("올바른 이메일 형식이 아니에요."))
                .andExpect(jsonPath("$.errors.termsAgreed").value("필수 약관에 동의해 주세요."));

        assertThat(sentCode()).isNull();
    }

    @DisplayName("가입: 아이디, 이메일, 닉네임이 이미 회원에게 있으면 409")
    @Test
    void signup_duplicate() throws Exception {
        userRepository.save(User.builder().uid("tester01").email(EMAIL).nickname("테스터").build());

        postJson("/api/signup", validRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.uid").value("이미 사용 중인 아이디예요."))
                .andExpect(jsonPath("$.errors.email").value("이미 가입된 이메일이에요."))
                .andExpect(jsonPath("$.errors.nickname").value("이미 사용 중인 닉네임이에요."));
    }

    @DisplayName("인증: 틀린 번호는 400, 5번 틀리면 재전송 전까지 막힌다.")
    @Test
    void verify_wrongAndTooMany() throws Exception {
        postJson("/api/signup", validRequest()).andExpect(status().isAccepted());
        String wrong = sentCode().equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            postJson("/api/signup/verify", Map.of("email", EMAIL, "code", wrong))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.code").value("인증번호가 일치하지 않아요. 다시 확인해 주세요."));
        }
        // 맞는 번호여도 이미 5번 틀렸으면 거부
        postJson("/api/signup/verify", Map.of("email", EMAIL, "code", sentCode()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("인증번호를 여러 번 틀렸어요. 인증번호를 재전송해 주세요."));
        assertThat(userRepository.count()).isZero();
    }

    @DisplayName("인증: 3분이 지나면 만료되고, 재전송하면 새 번호로 인증된다. 이전 번호는 쓸 수 없다.")
    @Test
    void verify_expiredThenResend() throws Exception {
        postJson("/api/signup", validRequest()).andExpect(status().isAccepted());
        String firstCode = sentCode();

        clock.advance(Duration.ofMinutes(3).plusSeconds(1));
        postJson("/api/signup/verify", Map.of("email", EMAIL, "code", firstCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").value("인증 시간이 만료됐어요. 인증번호를 재전송해 주세요."));

        postJson("/api/signup/resend", Map.of("email", EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(180));
        String secondCode = sentCode();

        if (!secondCode.equals(firstCode)) {
            postJson("/api/signup/verify", Map.of("email", EMAIL, "code", firstCode))
                    .andExpect(status().isBadRequest());
        }
        postJson("/api/signup/verify", Map.of("email", EMAIL, "code", secondCode))
                .andExpect(status().isCreated());
    }

    @DisplayName("가입: CSRF 토큰이 없으면 403")
    @Test
    void signup_withoutCsrf() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
        assertThat(sentCode()).isNull();
    }
}
