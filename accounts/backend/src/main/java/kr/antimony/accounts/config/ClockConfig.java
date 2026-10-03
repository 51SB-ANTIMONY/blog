package kr.antimony.accounts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// 현재 시각. 테스트에서 시간을 바꿔 만료를 시험할 수 있도록 빈으로 둔다.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
