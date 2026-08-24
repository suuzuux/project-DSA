package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // CHAT-06 AI팬 계정처럼, DB가 초기화돼도 항상 같은 이름으로 찾을 수 있어야 하는 경우 사용
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
    boolean existsByNickname(String nickname);
    boolean existsByEmail(String email);
}
