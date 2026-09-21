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
    
    // 배지를 받은 "기록"이 있는지. 회수(revoked)된 기록도 포함
    // 관리자가 회수한 배지를 시스템이 다시 자동 지급하면 X
    boolean existsByFan_IdAndArtist_IdAndBadgeCode(Long fanId, Long artistId, String badgeCode);
}
