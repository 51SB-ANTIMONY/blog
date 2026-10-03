package kr.antimony.blog.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// blog의 회원. 아이디, 비밀번호, 이메일 같은 계정 정보는 accounts에만 있고,
// blog는 accounts 회원과 연결할 값(sub)과 화면에 보여 줄 닉네임만 가진다.
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false)
    private Long id;

    // accounts가 발급한 ID 토큰의 sub (accounts의 회원 id). 로그인 방식과 상관없이 같은 사람이면 같은 값이다.
    @Column(name = "accounts_sub", nullable = false, unique = true, updatable = false)
    private String accountsSub;

    // accounts의 닉네임을 로그인할 때마다 복사해 둔다. 중복 검사는 accounts가 한다.
    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Builder
    public User(String accountsSub, String nickname) {
        this.accountsSub = accountsSub;
        this.nickname = nickname;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
