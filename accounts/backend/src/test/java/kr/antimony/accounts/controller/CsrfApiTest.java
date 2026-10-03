package kr.antimony.accounts.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// CSRF 토큰 발급 확인.
// spring-security-test의 csrf()는 한 번 쓰면 같은 테스트 컨텍스트의 CSRF 저장소를 테스트용으로 바꾼다.
// 그래서 csrf()를 쓰는 테스트들과 컨텍스트를 나눠 실제 설정(쿠키 저장소)으로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
class CsrfApiTest {

    @Autowired
    MockMvc mockMvc;

    @DisplayName("CSRF: 토큰을 응답하고 XSRF-TOKEN 쿠키를 설정한다. 헤더 이름은 X-XSRF-TOKEN")
    @Test
    void csrfToken() throws Exception {
        mockMvc.perform(get("/api/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }
}
