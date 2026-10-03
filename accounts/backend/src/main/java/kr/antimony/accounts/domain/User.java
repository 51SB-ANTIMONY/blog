package kr.antimony.accounts.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    // 아이디·비밀번호 로그인에서 쓰는 아이디. 소셜 로그인으로 가입하면 자동으로 만든다. (예: google_3f9a2c1b)
    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    // BCrypt 해시. 소셜 로그인으로만 가입한 회원은 비밀번호가 없다(NULL).
    @Column(name = "password")
    private String password;

    @Column(name = "nickname", nullable = false, unique = true)
    private String nickname;

    // [선택] 이벤트·소식 수신 동의
    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed;

    @Builder
    public User(String uid, String email, String password, String nickname, boolean marketingAgreed) {
        this.uid = uid;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.marketingAgreed = marketingAgreed;
    }

    public boolean hasPassword() {
        return password != null;
    }

    public void changePassword(String passwordHash) {
        this.password = passwordHash;
    }
}