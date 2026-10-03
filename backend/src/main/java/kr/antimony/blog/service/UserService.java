package kr.antimony.blog.service;

import lombok.RequiredArgsConstructor;
import kr.antimony.blog.domain.User;
import kr.antimony.blog.repository.UserRepository;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserService {
    private final UserRepository userRepository;

    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unexpected user"));
    }
}
