package kr.antimony.accounts.config;

import kr.antimony.accounts.config.oauth.OAuth2UserCustomService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.boot.security.autoconfigure.web.servlet.PathRequest.toH2Console;

// 인가 서버 주소를 제외한 나머지 요청(로그인 화면, 회원가입 등)을 담당하는 보안 설정
@RequiredArgsConstructor
@Configuration
public class SecurityConfig {

    private final OAuth2UserCustomService oAuth2UserCustomService;

    @Bean
    public WebSecurityCustomizer configure() {
        return (web) -> web.ignoring()
                .requestMatchers(toH2Console())
                .requestMatchers(
                        "/img/**",
                        "/css/**",
                        "/js/**"
                );
    }

    // AuthorizationServerConfig의 체인(@Order(1)) 다음에 검사한다.
    // 세션으로 로그인 상태를 유지하고(SSO의 근거), CSRF 보호를 켠다.
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/signup", "/user", "/error").permitAll()
                        .anyRequest().authenticated())
                // 아이디(uid)와 비밀번호로 로그인 (POST /login)
                .formLogin(formLogin -> formLogin
                        .loginPage("/login"))
                // Google로 로그인
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfoEndpoint -> userInfoEndpoint.userService(oAuth2UserCustomService)))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
