package kr.antimony.accounts.mail;

import kr.antimony.accounts.domain.VerificationPurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

// 로컬 개발용. 메일을 보내지 않고 인증번호를 서버 로그에 남긴다.
@Slf4j
@Component
@ConditionalOnExpression("'${spring.mail.host:}'.isEmpty()")
public class LoggingVerificationMailSender implements VerificationMailSender {

    @Override
    public void send(String email, VerificationPurpose purpose, String code) {
        log.info("[개발용 메일] {} 인증번호 {} → {}", purpose, code, email);
    }
}
