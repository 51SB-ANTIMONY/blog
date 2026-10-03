package kr.antimony.accounts.service;

import lombok.RequiredArgsConstructor;
import kr.antimony.accounts.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserDetailService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String uid) {
        return userRepository.findByUid(uid)
                .orElseThrow(() -> new UsernameNotFoundException(uid));
    }
}
