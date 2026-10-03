package kr.antimony.accounts.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// 이메일 인증을 기다리는 가입 신청. 인증이 끝나면 회원(users)으로 옮기고 지운다.
// 인증하지 않은 신청은 회원으로 만들지 않으므로, users에는 이메일 인증을 마친 회원만 있다.
@Table(name = "pending_signups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class PendingSignup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "uid", nullable = false)
    private String uid;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    public PendingSignup(String email, String uid, String nickname, String passwordHash, boolean marketingAgreed, Instant requestedAt) {
        this.email = email;
        this.uid = uid;
        this.nickname = nickname;
        this.passwordHash = passwordHash;
        this.marketingAgreed = marketingAgreed;
        this.requestedAt = requestedAt;
    }
}
