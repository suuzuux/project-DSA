package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.MembershipPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipPeriodRepository extends JpaRepository<MembershipPeriod, Long> {
	// 가장 최근 기간 한 줄. 연속 여부는 직전 기간의 만료일과 streakCount만 있으면 된다
	Optional<MembershipPeriod> findTopByFanIdAndArtistIdOrderByStartedAtDesc(Long fanId, Long artistId);

	// 이 팬이 이 아티스트 멤버십에 한 번이라도 가입한 적이 있는지 (해지로 membership 줄이 지워져도 이력은 남아 있음)
	boolean existsByFanIdAndArtistId(Long fanId, Long artistId);
}
