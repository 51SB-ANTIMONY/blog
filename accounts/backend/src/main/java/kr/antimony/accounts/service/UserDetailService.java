package kr.antimony.accounts.service;

import kr.antimony.accounts.repository.UserRepository;
import kr.antimony.accounts.security.AccountUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// 아이디·비밀번호 로그인에서 아이디(uid)로 회원을 찾는다.
@RequiredArgsConstructor
@Service
public class UserDetailService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String uid) {
        return userRepository.findByUid(uid)
                .map(AccountUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException(uid));
    }
}
