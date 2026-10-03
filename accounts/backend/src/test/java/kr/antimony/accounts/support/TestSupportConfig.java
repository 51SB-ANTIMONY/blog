package kr.antimony.accounts.support;

import kr.antimony.accounts.domain.VerificationPurpose;
import kr.antimony.accounts.mail.VerificationMailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 테스트용 빈
// - CapturingMailSender: 메일을 보내는 대신 마지막으로 보낸 인증번호를 기억한다
// - MutableClock: 시간을 앞으로 돌려 인증번호 만료를 시험한다
@TestConfiguration
public class TestSupportConfig {

    @Bean
    @Primary
    public CapturingMailSender capturingMailSender() {
        return new CapturingMailSender();
    }

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    public static class CapturingMailSender implements VerificationMailSender {
        private final Map<String, String> codes = new ConcurrentHashMap<>();

        @Override
        public void send(String email, VerificationPurpose purpose, String code) {
            codes.put(purpose + ":" + email, code);
        }

        public String lastCode(VerificationPurpose purpose, String email) {
            return codes.get(purpose + ":" + email);
        }

        public void clear() {
            codes.clear();
        }
    }

    public static class MutableClock extends Clock {
        private Instant now = Instant.now();

        public void advance(Duration duration) {
            now = now.plus(duration);
        }

        public void reset() {
            now = Instant.now();
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
