package kr.antimony.blog.config;

import kr.antimony.blog.domain.User;
import kr.antimony.blog.repository.BlogRepository;
import kr.antimony.blog.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountsLoginTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    UserRepository userRepository;

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

    @DisplayName("Next.js(3000)를 거친 요청이면 redirect_uri도 3000으로 만든다. (X-Forwarded-Host)")
    @Test
    void redirectUriBehindFrontend() throws Exception {
        String location = mockMvc.perform(get("/oauth2/authorization/accounts")
                        .header("X-Forwarded-Host", "localhost:3000"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        UriComponents uri = UriComponentsBuilder.fromUriString(location).build();
        assertThat(uri.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo("http://localhost:3000/login/oauth2/code/accounts");
    }

    @DisplayName("Next.js(3000)를 거친 로그아웃이면 로그아웃 후 돌아올 주소도 3000이다.")
    @Test
    void logoutBehindFrontend() throws Exception {
        String location = mockMvc.perform(post("/logout")
                        .header("X-Forwarded-Host", "localhost:3000")
                        .with(oidcLogin()
                                .clientRegistration(clientRegistrationRepository.findByRegistrationId("accounts"))
                                .idToken(token -> token.subject("1001")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        UriComponents uri = UriComponentsBuilder.fromUriString(location).build();
        assertThat(uri.getQueryParams().getFirst("post_logout_redirect_uri")).isEqualTo("http://localhost:3000/");
    }

    @DisplayName("주소: 브라우저가 가는 주소는 issuer(3001), blog 서버가 직접 부르는 주소는 backchannel-uri(9000)")
    @Test
    void frontAndBackChannelUris() {
        ClientRegistration.ProviderDetails provider =
                clientRegistrationRepository.findByRegistrationId("accounts").getProviderDetails();

        assertThat(provider.getIssuerUri()).isEqualTo("http://localhost:3001");
        assertThat(provider.getAuthorizationUri()).isEqualTo("http://localhost:3001/oauth2/authorize");
        assertThat(provider.getConfigurationMetadata().get("end_session_endpoint")).isEqualTo("http://localhost:3001/connect/logout");
        assertThat(provider.getTokenUri()).isEqualTo("http://localhost:9000/oauth2/token");
        assertThat(provider.getJwkSetUri()).isEqualTo("http://localhost:9000/oauth2/jwks");
    }

    @DisplayName("로그인하지 않은 API 요청은 accounts로 보내지 않고 401로 응답한다.")
    @Test
    void apiWithoutLoginIsUnauthorized() throws Exception {
        // CSRF 검사가 로그인 검사보다 먼저이므로, 토큰은 넣어서 "로그인 안 됨"만 확인한다.
        mockMvc.perform(post("/api/articles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("로그아웃: blog 세션을 지우고 accounts의 로그아웃 주소로 보낸다. ID 토큰과 돌아올 주소를 함께 보낸다.")
    @Test
    void logoutRedirectsToAccounts() throws Exception {
        String location = mockMvc.perform(post("/logout")
                        .with(oidcLogin()
                                .clientRegistration(clientRegistrationRepository.findByRegistrationId("accounts"))
                                .idToken(token -> token.subject("1001")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        UriComponents uri = UriComponentsBuilder.fromUriString(location).build();
        assertThat(uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + uri.getPath())
                .isEqualTo("http://localhost:3001/connect/logout");
        assertThat(uri.getQueryParams().getFirst("id_token_hint")).isNotBlank();
        assertThat(uri.getQueryParams().getFirst("post_logout_redirect_uri")).isEqualTo("http://localhost/");
    }

    @DisplayName("첫 주소(/)는 글 목록으로 보낸다. accounts에서 로그아웃한 뒤 돌아오는 곳이다.")
    @Test
    void homeRedirectsToArticles() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(redirectedUrl("/articles"));
    }

    @DisplayName("글 목록: 로그인 전에는 로그인 버튼, 로그인 후에는 닉네임과 로그아웃 버튼을 보여 준다.")
    @Test
    void articleListShowsLoginState() throws Exception {
        blogRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder().accountsSub("1001").nickname("작성자").build());

        mockMvc.perform(get("/articles"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/accounts")));

        mockMvc.perform(get("/articles").with(oidcLogin().idToken(token -> token.subject("1001"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("작성자님")))
                .andExpect(content().string(containsString("로그아웃")));
    }
}
