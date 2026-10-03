package kr.antimony.accounts.controller;

import kr.antimony.accounts.domain.VerificationPurpose;
import kr.antimony.accounts.repository.UserRepository;
import kr.antimony.accounts.support.DatabaseCleaner;
import kr.antimony.accounts.support.TestSupportConfig;
import kr.antimony.accounts.support.TestSupportConfig.CapturingMailSender;
import kr.antimony.accounts.support.TestSupportConfig.MutableClock;
import kr.antimony.accounts.support.TestUsers;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSupportConfig.class, DatabaseCleaner.class})
class AccountRecoveryApiControllerTest {

    private static final String EMAIL = "antimony01@antimony.kr";

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
        TestUsers.localUser(userRepository, passwordEncoder, "antimony01", EMAIL, "안티모니", "oldpass1!");
    }

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String tokenOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("resetToken").asText();
    }

    @DisplayName("아이디 찾기: 가입한 이메일로 인증하면 일부를 가린 아이디를 알려 준다.")
    @Test
    void findId() throws Exception {
        postJson("/api/find-id/code", Map.of("email", EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(180));

        postJson("/api/find-id/verify", Map.of("email", EMAIL, "code", mailSender.lastCode(VerificationPurpose.FIND_ID, EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedUid").value("an******01"));
    }

    @DisplayName("아이디 찾기: 가입하지 않은 이메일도 똑같이 200을 응답하지만 인증번호는 보내지 않는다.")
    @Test
    void findId_unknownEmail() throws Exception {
        postJson("/api/find-id/code", Map.of("email", "nobody@antimony.kr"))
                .andExpect(status().isOk());
        assertThat(mailSender.lastCode(VerificationPurpose.FIND_ID, "nobody@antimony.kr")).isNull();
    }

    @DisplayName("비밀번호 찾기: 아이디·이메일 → 인증번호 → 새 비밀번호. 이후 새 비밀번호로만 로그인된다.")
    @Test
    void resetPassword() throws Exception {
        postJson("/api/password-reset/code", Map.of("uid", "antimony01", "email", EMAIL))
                .andExpect(status().isOk());
        String code = mailSender.lastCode(VerificationPurpose.RESET_PASSWORD, EMAIL);

        String resetToken = tokenOf(postJson("/api/password-reset/verify", Map.of("uid", "antimony01", "email", EMAIL, "code", code))
                .andExpect(status().isOk()));

        postJson("/api/password-reset/complete", Map.of("resetToken", resetToken, "password", "newpass1!"))
                .andExpect(status().isNoContent());

        mockMvc.perform(formLogin("/login/password").user("antimony01").password("newpass1!"))
                .andExpect(authenticated());
        mockMvc.perform(formLogin("/login/password").user("antimony01").password("oldpass1!"))
                .andExpect(unauthenticated());

        // 같은 토큰은 다시 쓸 수 없다.
        postJson("/api/password-reset/complete", Map.of("resetToken", resetToken, "password", "another1!"))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("비밀번호 찾기: 아이디와 이메일이 맞지 않으면 인증번호를 보내지 않는다.")
    @Test
    void resetPassword_mismatch() throws Exception {
        postJson("/api/password-reset/code", Map.of("uid", "someoneelse", "email", EMAIL))
                .andExpect(status().isOk());
        assertThat(mailSender.lastCode(VerificationPurpose.RESET_PASSWORD, EMAIL)).isNull();
    }

    @DisplayName("비밀번호 찾기: 새 비밀번호가 규칙에 맞지 않으면 400, 인증 후 10분이 지나면 다시 시작해야 한다.")
    @Test
    void resetPassword_invalidAndExpired() throws Exception {
        postJson("/api/password-reset/code", Map.of("uid", "antimony01", "email", EMAIL));
        String code = mailSender.lastCode(VerificationPurpose.RESET_PASSWORD, EMAIL);
        String resetToken = tokenOf(postJson("/api/password-reset/verify", Map.of("uid", "antimony01", "email", EMAIL, "code", code)));

        postJson("/api/password-reset/complete", Map.of("resetToken", resetToken, "password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("8자 이상, 영문·숫자·특수문자를 모두 포함해 주세요."));

        clock.advance(Duration.ofMinutes(11));
        postJson("/api/password-reset/complete", Map.of("resetToken", resetToken, "password", "newpass1!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("비밀번호 재설정 시간이 지났어요. 처음부터 다시 진행해 주세요."));
    }
}
