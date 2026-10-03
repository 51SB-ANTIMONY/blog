package kr.antimony.accounts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationServerTest {

    @Autowired
    MockMvc mockMvc;

    @Value("${accounts.issuer}")
    String issuer;

    @Value("${accounts.clients.blog.client-secret}")
    String blogClientSecret;

    @Value("${accounts.clients.blog.redirect-uri}")
    String blogRedirectUri;

    @DisplayName("설정 문서: issuer와 각 엔드포인트 주소를 공개한다.")
    @Test
    void openidConfiguration() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(issuer))
                .andExpect(jsonPath("$.authorization_endpoint").value(issuer + "/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value(issuer + "/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value(issuer + "/oauth2/jwks"));
    }

    @DisplayName("공개 키: 토큰 서명을 검증할 RSA 공개 키를 공개한다.")
    @Test
    void jwks() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());   // 개인 키는 공개하지 않는다
    }

    @DisplayName("권한 요청: 로그인하지 않은 브라우저 요청은 로그인 화면으로 보낸다.")
    @Test
    void authorize_notLoggedIn() throws Exception {
        mockMvc.perform(get(authorizeUri(blogRedirectUri, "openid", "test-state", codeChallenge(newCodeVerifier())))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @DisplayName("권한 요청: 등록되지 않은 redirect_uri는 거부한다.")
    @Test
    void authorize_invalidRedirectUri() throws Exception {
        mockMvc.perform(get(authorizeUri("https://evil.example.com/callback", "openid", "test-state", codeChallenge(newCodeVerifier())))
                        .with(user("tester")))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("권한 요청: PKCE(code_challenge)가 없으면 코드를 발급하지 않는다.")
    @Test
    void authorize_withoutPkce() throws Exception {
        MvcResult result = mockMvc.perform(get(authorizeUri(blogRedirectUri, "openid", "test-state", null))
                        .with(user("tester")))
                .andReturn();

        // 등록된 redirect_uri로 error=invalid_request를 담아 돌려보내고, code는 주지 않는다.
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(blogRedirectUri);
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(query.getFirst("error")).isEqualTo("invalid_request");
        assertThat(query.getFirst("code")).isNull();
    }

    @DisplayName("권한 부여 코드 방식: 로그인한 사용자는 코드를 받고, blog 서버는 코드를 토큰으로 교환한다.")
    @Test
    void authorizationCodeFlow() throws Exception {
        String codeVerifier = newCodeVerifier();

        // ① 로그인한 사용자의 권한 요청 → 코드와 함께 blog로 리다이렉트
        MvcResult authorizeResult = mockMvc.perform(get(authorizeUri(blogRedirectUri, "openid profile", "test-state", codeChallenge(codeVerifier)))
                        .with(user("tester")))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = authorizeResult.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(blogRedirectUri);
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        assertThat(query.getFirst("state")).isEqualTo("test-state");
        String code = query.getFirst("code");
        assertThat(code).isNotBlank();

        // ② blog 서버가 Client Secret으로 자신을 인증하고 코드를 토큰으로 교환
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("blog", blogClientSecret))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", blogRedirectUri)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @DisplayName("토큰 요청: Client Secret이 틀리면 401을 응답한다.")
    @Test
    void token_invalidClientSecret() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("blog", "wrong-secret"))
                        .param("grant_type", "authorization_code")
                        .param("code", "any-code")
                        .param("redirect_uri", blogRedirectUri))
                .andExpect(status().isUnauthorized());
    }

    // 인가 서버는 GET /oauth2/authorize의 파라미터를 쿼리 문자열에서 읽으므로 주소에 직접 넣는다.
    private static URI authorizeUri(String redirectUri, String scope, String state, String codeChallenge) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "blog")
                .queryParam("scope", scope)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state);
        if (codeChallenge != null) {
            builder.queryParam("code_challenge", codeChallenge)
                    .queryParam("code_challenge_method", "S256");
        }
        return builder.encode().build().toUri();   // URI로 넘겨야 MockMvc가 한 번 더 인코딩하지 않는다
    }

    // PKCE: 임의의 code_verifier를 만들고, code_challenge = BASE64URL(SHA-256(code_verifier))
    private static String newCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
