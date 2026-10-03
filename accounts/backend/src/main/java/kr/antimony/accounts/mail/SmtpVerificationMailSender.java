package kr.antimony.accounts.mail;

import kr.antimony.accounts.domain.VerificationPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// spring.mail.host가 설정되어 있을 때만 쓰인다. 실제 메일을 보낸다.
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpVerificationMailSender implements VerificationMailSender {

    private final JavaMailSender mailSender;

    @Override
    public void send(String email, VerificationPurpose purpose, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[ANTIMONY] " + subject(purpose));
        message.setText("인증번호: " + code + "\n\n3분 안에 입력해 주세요. 요청하지 않으셨다면 이 메일을 무시해 주세요.");
        mailSender.send(message);
    }

    private static String subject(VerificationPurpose purpose) {
        return switch (purpose) {
            case SIGNUP -> "회원가입 이메일 인증번호";
            case FIND_ID -> "아이디 찾기 인증번호";
            case RESET_PASSWORD -> "비밀번호 재설정 인증번호";
        };
    }
}
