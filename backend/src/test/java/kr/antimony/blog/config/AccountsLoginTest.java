package kr.antimony.blog.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountsLoginTest {

    @Autowired
    MockMvc mockMvc;

    @DisplayName("로그인이 필요한 화면을 브라우저로 열면 accounts 로그인 시작 주소로 보낸다.")
    @Test
    void loginRequiredPageRedirectsToAccounts() throws Exception {
        // 브라우저처럼 HTML을 원하는 요청이어야 로그인 화면으로 보낸다. (Accept가 없으면 401)
        mockMvc.perform(get("/new-article").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/accounts"));
    }

    @DisplayName("로그인 시작: accounts의 권한 요청 주소로 보낸다. client_id, 범위, PKCE, nonce가 들어 있다.")
    @Test
    void authorizationRequest() throws Exception {
        String location = mockMvc.perform(get("/oauth2/authorization/accounts"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        UriComponents uri = UriComponentsBuilder.fromUriString(location).build();
        assertThat(uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + uri.getPath())
                .isEqualTo("http://localhost:3001/oauth2/authorize");
        assertThat(uri.getQueryParams().getFirst("response_type")).isEqualTo("code");
        assertThat(uri.getQueryParams().getFirst("client_id")).isEqualTo("blog");
        assertThat(uri.getQueryParams().getFirst("redirect_uri")).isEqualTo("http://localhost/login/oauth2/code/accounts");
        assertThat(uri.getQueryParams().getFirst("scope").split("%20| ")).containsExactlyInAnyOrder("openid", "profile", "email");
        assertThat(uri.getQueryParams().getFirst("code_challenge")).isNotBlank();
        assertThat(uri.getQueryParams().getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(uri.getQueryParams().getFirst("state")).isNotBlank();
        assertThat(uri.getQueryParams().getFirst("nonce")).isNotBlank();
    }

    @DisplayName("로그인하지 않은 API 요청은 accounts로 보내지 않고 401로 응답한다.")
    @Test
    void apiWithoutLoginIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized());
    }
}
