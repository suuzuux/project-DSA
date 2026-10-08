package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.FanBadgeOwnership;
import megane6.weplanet.domain.entity.enumfolder.FanBadgeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FanBadgeOwnershipRepository extends JpaRepository<FanBadgeOwnership, Long> {
    List<FanBadgeOwnership> findByFan_IdAndArtist_IdAndRevokedAtIsNull(Long fanId, Long artistId);
    
    long countByFan_IdAndArtist_IdAndBadgeTypeAndRevokedAtIsNull(
            Long fanId,
            Long artistId,
            FanBadgeType badgeType
    );
    
    // 배지 지급 기록이 있는지 (회수된 기록 포함, 회수 후 재지급 방지).
    boolean existsByFan_IdAndArtist_IdAndBadgeCode(Long fanId, Long artistId, String badgeCode);
}
