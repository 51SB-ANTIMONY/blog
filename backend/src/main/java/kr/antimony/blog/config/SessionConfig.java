package kr.antimony.blog.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 세션은 Spring Session JDBC가 DB(SPRING_SESSION)에 저장한다.
// Spring Boot는 내장 웹 서버로 실행할 때만 server.servlet.session.cookie.name을 Spring Session 쿠키에 적용한다.
// 테스트(MockMvc)처럼 내장 서버가 없는 환경에서도 같은 쿠키 이름을 쓰도록 직접 지정한다.
@Configuration
public class SessionConfig {

    @Bean
    public DefaultCookieSerializerCustomizer sessionCookieName(
            @Value("${server.servlet.session.cookie.name}") String cookieName) {
        return serializer -> serializer.setCookieName(cookieName);
    }
}
