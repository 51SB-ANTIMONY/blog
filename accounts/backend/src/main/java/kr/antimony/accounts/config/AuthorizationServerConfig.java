package kr.antimony.accounts.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import kr.antimony.accounts.domain.User;
import kr.antimony.accounts.repository.UserRepository;
import kr.antimony.accounts.security.AccountPrincipal;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;

// OAuth 2.1 / OpenID Connect 인가 서버 설정
// 인가 서버 주소(/oauth2/**, /.well-known/**, /userinfo, /connect/**)만 담당하는 필터 체인과,
// 등록된 앱, 토큰 서명 키, issuer 설정을 정의한다.
@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .oauth2AuthorizationServer(authorizationServer -> {
                    // 이 체인은 인가 서버 주소만 담당한다. 나머지는 SecurityConfig의 체인(@Order(2))이 담당한다.
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    // OpenID Connect 기능 사용 (ID 토큰, /userinfo, 설정 문서, 로그아웃)
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated())
                // 브라우저 요청(HTML)이 로그인 안 된 상태로 오면 로그인 화면으로 보낸다.
                // 서버 간 요청(/oauth2/token 등)은 리다이렉트하지 않고 401로 응답한다.
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                // /userinfo는 사용자 세션이 아니라 액세스 토큰(Authorization: Bearer)으로 인증한다.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    // 로그인을 요청할 수 있는 앱 목록. 지금은 blog 하나를 메모리에 등록한다.
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            PasswordEncoder passwordEncoder,
            @Value("${accounts.clients.blog.client-secret}") String blogClientSecret,
            @Value("${accounts.clients.blog.redirect-uri}") String blogRedirectUri,
            @Value("${accounts.clients.blog.post-logout-redirect-uri}") String blogPostLogoutRedirectUri) {
        RegisteredClient blog = RegisteredClient.withId("blog")
                .clientId("blog")
                // 토큰 요청 때 받은 Client Secret을 PasswordEncoder로 비교하므로 암호화해서 저장한다.
                .clientSecret(passwordEncoder.encode(blogClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(blogRedirectUri)
                // 전체 로그아웃(/connect/logout) 후 돌아갈 주소
                .postLogoutRedirectUri(blogPostLogoutRedirectUri)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        // 자사 앱이므로 동의 화면 없이 바로 코드를 발급한다.
                        .requireAuthorizationConsent(false)
                        // 권한 요청에 PKCE(code_challenge)를 반드시 포함해야 한다. (Spring Security 7의 기본값)
                        .requireProofKey(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(30))
                        .refreshTokenTimeToLive(Duration.ofDays(14))
                        // 리프레시 토큰으로 재발급할 때마다 리프레시 토큰도 새것으로 바꾼다.
                        .reuseRefreshTokens(false)
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(blog);
    }

    // 발급하는 토큰에 우리 회원 정보를 넣는다.
    // - sub: 로그인 방식(아이디·비밀번호, Google)과 상관없이 우리 회원 id
    // - ID 토큰: profile 범위면 nickname, preferred_username(아이디), email 범위면 email
    // /userinfo는 ID 토큰의 클레임 중 허락된 범위의 것만 돌려준다.
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(UserRepository userRepository) {
        return context -> {
            if (!(context.getPrincipal().getPrincipal() instanceof AccountPrincipal account)) {
                return;
            }
            context.getClaims().subject(String.valueOf(account.getUserId()));

            if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                User user = userRepository.findById(account.getUserId())
                        .orElseThrow(() -> new IllegalStateException("회원을 찾을 수 없습니다: " + account.getUserId()));
                if (context.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
                    context.getClaims().claim("nickname", user.getNickname());
                    context.getClaims().claim("preferred_username", user.getUid());
                }
                if (context.getAuthorizedScopes().contains(OidcScopes.EMAIL)) {
                    context.getClaims().claim("email", user.getEmail());
                }
            }
        };
    }

    // 토큰 서명용 RSA 키. 개인 키로 서명하고, 공개 키는 /oauth2/jwks로 공개된다.
    // TODO: 지금은 서버를 시작할 때마다 새로 만든다. 재시작해도 기존 토큰이 유효하도록 키를 저장해서 쓰도록 바꾼다.
    // (SecurityContext는 Nimbus 라이브러리의 타입이다. Spring Security의 SecurityContext와 다르다.)
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    // /userinfo 요청의 액세스 토큰을 검증할 때 쓴다. 위의 공개 키로 서명을 확인한다.
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        return new NimbusJwtDecoder(jwtProcessor);
    }

    // 인가 서버의 주소(issuer). 토큰의 iss 클레임과 설정 문서에 들어간다.
    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${accounts.issuer}") String issuer) {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
