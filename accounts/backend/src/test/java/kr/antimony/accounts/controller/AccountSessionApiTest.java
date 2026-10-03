package kr.antimony.accounts.controller;

import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.domain.UserIdentity;
import kr.antimony.accounts.repository.UserIdentityRepository;
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

import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 로그인 상태와 관련된 API: 소셜 제공자 목록, 내 계정, 로그인 상태 유지, 로그아웃
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSupportConfig.class, DatabaseCleaner.class})
class AccountSessionApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired UserIdentityRepository userIdentityRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
    }

    @DisplayName("소셜 제공자: 설정에 등록된 Google만 사용 가능")
    @Test
    void providers() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google").value(true))
                .andExpect(jsonPath("$.kakao").value(false))
                .andExpect(jsonPath("$.naver").value(false));
    }

    @DisplayName("내 계정: 로그인하지 않으면 401, 로그인하면 계정 정보와 연결된 소셜 계정")
    @Test
    void me() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

        User user = TestUsers.localUser(userRepository, passwordEncoder, "tester01", "tester@antimony.kr", "테스터", "pass1234!");
        userIdentityRepository.save(new UserIdentity(user, "google", "google-sub-1"));

        mockMvc.perform(get("/api/me").with(authentication(TestUsers.loggedIn(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("tester01"))
                .andExpect(jsonPath("$.nickname").value("테스터"))
                .andExpect(jsonPath("$.email").value("tester@antimony.kr"))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.identities[0].provider").value("google"));
    }

    @DisplayName("연결 해제: 비밀번호가 있으면 해제되고, 소셜만 남은 회원의 마지막 연결은 409")
    @Test
    void unlink() throws Exception {
        User withPassword = TestUsers.localUser(userRepository, passwordEncoder, "tester01", "a@antimony.kr", "테스터", "pass1234!");
        userIdentityRepository.save(new UserIdentity(withPassword, "google", "google-sub-1"));
        mockMvc.perform(delete("/api/me/identities/google").with(csrf()).with(authentication(TestUsers.loggedIn(withPassword))))
                .andExpect(status().isNoContent());
        assertThat(userIdentityRepository.findByUserId(withPassword.getId())).isEmpty();

        User socialOnly = TestUsers.localUser(userRepository, passwordEncoder, "google_1a2b3c4d", "b@gmail.com", "구글회원", null);
        userIdentityRepository.save(new UserIdentity(socialOnly, "google", "google-sub-2"));
        mockMvc.perform(delete("/api/me/identities/google").with(csrf()).with(authentication(TestUsers.loggedIn(socialOnly))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("마지막 로그인 수단은 해제할 수 없어요. 비밀번호 찾기로 비밀번호를 먼저 설정해 주세요."));
    }

    @DisplayName("로그인: 성공하면 /account로, 실패하면 /login?error=bad_credentials로 보낸다.")
    @Test
    void loginRedirects() throws Exception {
        TestUsers.localUser(userRepository, passwordEncoder, "tester01", "a@antimony.kr", "테스터", "pass1234!");

        mockMvc.perform(formLogin("/login/password").user("tester01").password("pass1234!"))
                .andExpect(redirectedUrl("/account"));
        mockMvc.perform(formLogin("/login/password").user("tester01").password("wrong"))
                .andExpect(redirectedUrl("/login?error=bad_credentials"));
    }

    @DisplayName("로그인 상태 유지: 체크하고 로그인하면 쿠키를 주고, 그 쿠키만으로 로그인 상태가 된다. 로그아웃하면 지운다.")
    @Test
    void rememberMe() throws Exception {
        TestUsers.localUser(userRepository, passwordEncoder, "tester01", "a@antimony.kr", "테스터", "pass1234!");

        MvcResult login = mockMvc.perform(post("/login/password").with(csrf())
                        .param("username", "tester01")
                        .param("password", "pass1234!")
                        .param("remember-me", "on"))
                .andExpect(cookie().exists("ACCOUNTS_REMEMBER_ME"))
                .andReturn();
        Cookie rememberMe = login.getResponse().getCookie("ACCOUNTS_REMEMBER_ME");

        // 세션 없이 쿠키만으로 /api/me
        mockMvc.perform(get("/api/me").cookie(rememberMe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("tester01"));

        // 로그아웃하면 쿠키를 지운다 (Max-Age=0)
        mockMvc.perform(post("/logout").with(csrf()).cookie(rememberMe))
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge("ACCOUNTS_REMEMBER_ME", 0));
    }
}
