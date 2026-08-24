package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByUsername(String username);
	boolean existsByUsername(String username);
	boolean existsByNickname(String nickname);
	boolean existsByEmail(String email);
}


