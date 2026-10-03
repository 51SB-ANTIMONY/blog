package kr.antimony.accounts.config;

import kr.antimony.accounts.config.oauth.OAuth2UserCustomService;
import kr.antimony.accounts.service.UserDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.time.Duration;

import static org.springframework.boot.security.autoconfigure.web.servlet.PathRequest.toH2Console;

// 인가 서버 주소를 제외한 나머지 요청을 담당하는 보안 설정.
// 화면(로그인, 회원가입 등)은 Next.js가 그리고, 이 서버는 같은 주소 아래에서 API와 로그인 처리를 맡는다.
@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    public static final String REMEMBER_ME_COOKIE = "ACCOUNTS_REMEMBER_ME";

    private final OAuth2UserCustomService oAuth2UserCustomService;

    @Bean
    public WebSecurityCustomizer configure() {
        return (web) -> web.ignoring()
                .requestMatchers(toH2Console());
    }

    // "로그인 상태 유지". 체크하고 로그인하면 14일 동안 유지되는 쿠키를 준다.
    // 쿠키 값은 아이디, 만료 시각과 서명(key)으로 만들어서, 서버의 key를 모르면 위조할 수 없다.
    @Bean
    public TokenBasedRememberMeServices rememberMeServices(UserDetailService userDetailService,
                                                           @Value("${accounts.remember-me.key}") String key) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(key, userDetailService);
        services.setParameter("remember-me");
        services.setCookieName(REMEMBER_ME_COOKIE);
        services.setTokenValiditySeconds((int) Duration.ofDays(14).toSeconds());
        return services;
    }

    // AuthorizationServerConfig의 체인(@Order(1)) 다음에 검사한다.
    // 세션으로 로그인 상태를 유지하고(SSO의 근거), CSRF 보호를 켠다.
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          TokenBasedRememberMeServices rememberMeServices) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/csrf", "/api/auth/providers",
                                "/api/signup/**", "/api/find-id/**", "/api/password-reset/**", "/error").permitAll()
                        .anyRequest().authenticated())
                // CSRF 토큰을 XSRF-TOKEN 쿠키로 내려 주고, X-XSRF-TOKEN 헤더 또는 _csrf 폼 값으로 받는다. (Next.js 화면용)
                .csrf(csrf -> csrf.spa())
                // 아이디·비밀번호 로그인. 화면(/login)은 Next.js, 처리(POST /login/password)는 이 서버
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .loginProcessingUrl("/login/password")
                        .defaultSuccessUrl("/account")
                        .failureUrl("/login?error=bad_credentials"))
                .rememberMe(rememberMe -> rememberMe
                        .rememberMeServices(rememberMeServices))
                // 소셜 로그인
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .defaultSuccessUrl("/account")
                        .userInfoEndpoint(userInfoEndpoint -> userInfoEndpoint.userService(oAuth2UserCustomService))
                        .failureHandler(socialLoginFailureHandler()))
                // accounts에서의 로그아웃 (POST /logout). 세션과 "로그인 상태 유지" 쿠키를 지운다.
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout"))
                // API는 로그인 화면으로 보내지 않고 401로 응답한다.
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .build();
    }

    // 소셜 로그인 실패 시 오류 코드를 붙여 로그인 화면으로 보낸다. 예: /login?social_error=email_already_registered
    private static AuthenticationFailureHandler socialLoginFailureHandler() {
        return (request, response, exception) -> {
            String code = (exception instanceof OAuth2AuthenticationException oAuth2Exception)
                    ? oAuth2Exception.getError().getErrorCode()
                    : "unknown";
            response.sendRedirect(request.getContextPath() + "/login?social_error=" + code);
        };
    }
}
