package kr.antimony.accounts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// PasswordEncoder를 보안 설정과 분리해 둔다.
// SecurityConfig → OAuth2UserCustomService → UserService → PasswordEncoder 순서로 의존하므로,
// PasswordEncoder가 SecurityConfig 안에 있으면 서로가 서로를 기다리는 순환 참조가 생긴다.
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
