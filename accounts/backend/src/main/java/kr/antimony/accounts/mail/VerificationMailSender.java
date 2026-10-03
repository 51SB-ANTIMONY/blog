package kr.antimony.accounts.mail;

import kr.antimony.accounts.domain.VerificationPurpose;

// 인증번호 메일을 보낸다.
// SMTP 설정(spring.mail.host)이 있으면 실제 메일을 보내고, 없으면 서버 로그에 인증번호를 남긴다(로컬 개발용).
public interface VerificationMailSender {
    void send(String email, VerificationPurpose purpose, String code);
}
