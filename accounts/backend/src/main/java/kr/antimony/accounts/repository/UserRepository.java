package kr.antimony.accounts.repository;

import kr.antimony.accounts.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUid(String uid);
    boolean existsByUid(String uid);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
}