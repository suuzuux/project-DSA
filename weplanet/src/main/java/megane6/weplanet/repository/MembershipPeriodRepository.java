package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.MembershipPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipPeriodRepository extends JpaRepository<MembershipPeriod, Long> {
	// 가장 최근 기간 한 줄. 연속 여부는 직전 기간의 만료일과 streakCount만 있으면 된다
	Optional<MembershipPeriod> findTopByFanIdAndArtistIdOrderByStartedAtDesc(Long fanId, Long artistId);
}
