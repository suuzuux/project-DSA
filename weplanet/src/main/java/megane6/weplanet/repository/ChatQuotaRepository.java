package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.ChatQuota;
import megane6.weplanet.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatQuotaRepository extends JpaRepository<ChatQuota, Long> {

    Optional<ChatQuota> findByFanAndArtist(User fan, User artist);
}