package kr.antimony.accounts.repository;

import kr.antimony.accounts.domain.UserIdentity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    // 연결된 회원(user)도 함께 조회한다. user는 지연 로딩이라, 함께 읽지 않으면 트랜잭션이 끝난 뒤 회원 정보를 읽을 수 없다.
    @EntityGraph(attributePaths = "user")
    Optional<UserIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
