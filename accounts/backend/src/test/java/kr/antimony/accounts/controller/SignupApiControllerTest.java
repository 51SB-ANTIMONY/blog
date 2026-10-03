package kr.antimony.accounts.controller;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.dto.SignupRequest;
import kr.antimony.accounts.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

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
class SignupApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private ResultActions signup(SignupRequest request) throws Exception {
        return mockMvc.perform(post("/api/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    @DisplayName("회원가입: 성공하면 201과 회원 정보(비밀번호 제외)를 응답하고, 그 아이디로 로그인할 수 있다.")
    @Test
    void signup_success() throws Exception {
        signup(new SignupRequest("tester_01", "tester@antimony.kr", "테스터", "password123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.uid").value("tester_01"))
                .andExpect(jsonPath("$.email").value("tester@antimony.kr"))
                .andExpect(jsonPath("$.nickname").value("테스터"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // 비밀번호는 원문이 아니라 BCrypt 해시로 저장된다.
        User user = userRepository.findByUid("tester_01").orElseThrow();
        assertThat(user.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", user.getPassword())).isTrue();

        // 아이디(uid)와 비밀번호로 로그인된다. (이메일로는 로그인되지 않는다)
        mockMvc.perform(formLogin("/login").user("tester_01").password("password123"))
                .andExpect(authenticated().withUsername("tester_01"));
        mockMvc.perform(formLogin("/login").user("tester@antimony.kr").password("password123"))
                .andExpect(unauthenticated());
    }

    @DisplayName("회원가입: 입력값 규칙에 맞지 않으면 400과 필드별 에러를 응답한다.")
    @Test
    void signup_invalid() throws Exception {
        signup(new SignupRequest("AB", "not-an-email", "a", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.errors.uid").value("아이디는 4~20자의 영문 소문자, 숫자, 밑줄(_)만 사용할 수 있습니다."))
                .andExpect(jsonPath("$.errors.email").value("이메일 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors.nickname").value("닉네임은 2~20자로 입력해 주세요."))
                .andExpect(jsonPath("$.errors.password").value("비밀번호는 8~64자로 입력해 주세요."));

        assertThat(userRepository.count()).isZero();
    }

    @DisplayName("회원가입: 아이디, 이메일, 닉네임이 이미 있으면 409와 필드별 에러를 응답한다.")
    @Test
    void signup_duplicate() throws Exception {
        signup(new SignupRequest("tester_01", "tester@antimony.kr", "테스터", "password123"))
                .andExpect(status().isCreated());

        signup(new SignupRequest("tester_01", "tester@antimony.kr", "테스터", "password456"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 사용 중인 값이 있습니다."))
                .andExpect(jsonPath("$.errors.uid").value("이미 사용 중인 아이디입니다."))
                .andExpect(jsonPath("$.errors.email").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.errors.nickname").value("이미 사용 중인 닉네임입니다."));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @DisplayName("회원가입: CSRF 토큰이 없으면 403을 응답한다.")
    @Test
    void signup_withoutCsrf() throws Exception {
        mockMvc.perform(post("/api/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SignupRequest("tester_01", "tester@antimony.kr", "테스터", "password123"))))
                .andExpect(status().isForbidden());

        assertThat(userRepository.count()).isZero();
    }
}
