package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.MembershipPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipPeriodRepository extends JpaRepository<MembershipPeriod, Long> {
	// 가장 최근 기간 (연속 여부 계산용)
	Optional<MembershipPeriod> findTopByFanIdAndArtistIdOrderByStartedAtDesc(Long fanId, Long artistId);

	// 가입 이력이 있는지 (해지 후에도 이력은 남음)
	boolean existsByFanIdAndArtistId(Long fanId, Long artistId);
}
