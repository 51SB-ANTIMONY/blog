package kr.antimony.accounts.security;

import kr.antimony.accounts.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

// 아이디·비밀번호 로그인에 성공했을 때의 principal. 세션에 저장되므로 엔티티 대신 필요한 값만 담는다.
@Getter
public class AccountUserDetails implements UserDetails, AccountPrincipal, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String uid;
    private final String passwordHash;

    public AccountUserDetails(User user) {
        this.userId = user.getId();
        this.uid = user.getUid();
        this.passwordHash = user.getPassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getUsername() {
        return uid;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }
}
