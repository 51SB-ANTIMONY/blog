package kr.antimony.blog.config;

import jakarta.servlet.http.Cookie;
import kr.antimony.blog.domain.User;
import kr.antimony.blog.repository.BlogRepository;
import kr.antimony.blog.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 CSRF 설정을 확인한다.
// 다른 테스트에서 쓰는 .with(csrf())는 테스트용 CSRF 저장소로 바꿔 끼우므로, 그 영향이 없도록 새 Spring 컨텍스트에서 실행한다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
class CsrfTest {

    private static final String ARTICLE = "{\"title\":\"title\",\"content\":\"content\",\"category\":\"DAILY\"}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void setUp() {
        blogRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder().accountsSub("1001").nickname("작성자").build());
    }

    @DisplayName("CSRF 토큰 발급: XSRF-TOKEN 쿠키를 설정하고, 헤더 이름은 X-XSRF-TOKEN")
    @Test
    void issueToken() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @DisplayName("로그인했어도 CSRF 토큰이 없으면 403, 쿠키 값을 X-XSRF-TOKEN 헤더로 보내면 처리된다.")
    @Test
    void writeRequiresToken() throws Exception {
        mockMvc.perform(post("/api/articles")
                        .with(oidcLogin().idToken(token -> token.subject("1001")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ARTICLE))
                .andExpect(status().isForbidden());

        Cookie xsrf = mockMvc.perform(get("/api/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/articles")
                        .with(oidcLogin().idToken(token -> token.subject("1001")))
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ARTICLE))
                .andExpect(status().isCreated());
    }
}
