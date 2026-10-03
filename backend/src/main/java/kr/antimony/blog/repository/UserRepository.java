package kr.antimony.blog.repository;

import kr.antimony.blog.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByAccountsSub(String accountsSub);
}
