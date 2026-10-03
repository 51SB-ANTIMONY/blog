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

    @Column(name = "password")
    private String password;

    @Column(name = "nickname", nullable = false, unique = true)
    private String nickname;

    @Builder
    public User(String uid, String email, String password, String nickname) {
        this.uid = uid;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }
}