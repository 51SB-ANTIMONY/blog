package kr.antimony.blog.config;

import kr.antimony.blog.config.oauth.AccountsOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import static org.springframework.boot.security.autoconfigure.web.servlet.PathRequest.toH2Console;

// 로그인은 accounts(OIDC 인가 서버)에 맡긴다.
// accounts에서 로그인하고 돌아오면 blog는 세션(BLOG_SESSION 쿠키)으로 로그인 상태를 기억한다.
// 화면은 blog frontend(Next.js)가 그리고, 이 서버는 API와 로그인·로그아웃 처리만 맡는다.
@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    private final AccountsOidcUserService accountsOidcUserService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    // H2 콘솔은 보안 검사에서 뺀다. 콘솔이 켜져 있을 때(로컬)만 등록한다.
    // toH2Console()은 H2 콘솔 설정이 있어야 동작하므로, 콘솔을 끈 운영·테스트에서 등록하면 서버가 켜지지 않는다.
    @Bean
    @ConditionalOnBooleanProperty("spring.h2.console.enabled")
    public WebSecurityCustomizer h2ConsoleSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers(toH2Console());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 로그인 상태를 세션 쿠키로 기억하므로 CSRF 보호가 필요하다.
                // CSRF 토큰을 XSRF-TOKEN 쿠키로 내려 주고, X-XSRF-TOKEN 헤더 또는 _csrf 폼 값으로 받는다.
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/csrf").permitAll()
                        // 글 읽기는 누구나. 쓰기, 수정, 삭제는 로그인 필요
                        .requestMatchers(HttpMethod.GET, "/api/articles", "/api/articles/*").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // 로그인이 필요하면 accounts로 보낸다. (등록된 로그인 제공자가 accounts 하나뿐이라 바로 이동)
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(accountsOidcUserService))
                        // 로그인 후 돌아갈 요청이 없으면 화면의 첫 페이지로
                        .defaultSuccessUrl("/"))
                // 로그아웃(POST /logout): blog 세션을 지운 뒤 accounts의 로그아웃 주소로 보내 accounts 로그인도 끝낸다.
                .logout(logout -> logout
                        .logoutSuccessHandler(accountsLogoutSuccessHandler()))
                // API는 accounts로 보내지 않고 401로 응답한다.
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .build();
    }

    // accounts의 /connect/logout?id_token_hint=...&post_logout_redirect_uri=http://localhost:3000/ 로 보낸다.
    // post_logout_redirect_uri는 accounts에 등록한 주소와 같아야 한다.
    private OidcClientInitiatedLogoutSuccessHandler accountsLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }
}
