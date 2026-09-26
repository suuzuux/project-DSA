package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.BadgeCode;
import megane6.weplanet.repository.ArtistAccountProfileRepository;
import megane6.weplanet.repository.MembershipPeriodRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BadgePeriodService {
	private final CommunityMemberRepository cmr;
	private final ArtistAccountProfileRepository apr;
	private final MembershipPeriodRepository mpr;
	private final BadgeAwardService bas;
	
	// 한 팬의 모든 커뮤니티를 확인한다 (컬렉션 화면에서 호출)
	public void checkForFan(Long fanId) {
		if (fanId == null) {
			return;
		}
		checkMembers(cmr.findByFanId(fanId));
	}
	
	// 전체 회원 확인 (매일 스케줄러에서 호출)
	public int checkAll() {
		List<CommunityMember> members = cmr.findAll();
		checkMembers(members);
		return members.size();
	}
	
	// ------------------ 내부 helper -------------------
	private void checkMembers(List<CommunityMember> members) {
		LocalDate today = LocalDate.now();
		for (CommunityMember member : members) {
			try {
				checkMember(member, today);
			} catch (Exception e) {
				// 한 명이 실패해도 나머지는 계속 확인
				log.warn("기간 배지 확인 실패 : fanId={}, artistId={}, reason={}",
						member.getFanId(), member.getArtistId(), e.getMessage());
			}
		}
	}
	
	private void checkMember(CommunityMember member, LocalDate today) {
		Long fanId = member.getFanId();
		Long artistId = member.getArtistId();
		LocalDate joinedDate = member.getJoinedAt().toLocalDate();
		
		// ---------- 가입 후 N일 ----------
		// ChronoUnit.DAYS.between(a, b) : a부터 b까지 며칠 지났는지
		long days = ChronoUnit.DAYS.between(joinedDate, today);
		if (days >= 100) {
			bas.award(fanId, artistId, BadgeCode.BASIC_DAY_100);
		}
		if (days >= 200) {
			bas.award(fanId, artistId, BadgeCode.BASIC_DAY_200);
		}
		if (days >= 300) {
			bas.award(fanId, artistId, BadgeCode.BASIC_DAY_300);
		}
		
		// ---------- 가입 후 N년 ----------
		// 365를 직접 나누지 않고 YEARS 쓰는 이유 : 윤년 때문에 하루씩 어긋남
		long years = ChronoUnit.YEARS.between(joinedDate, today);
		if (years >= 1) {
			bas.award(fanId, artistId, BadgeCode.BASIC_YEAR_1);
		}
		if (years >= 2) {
			bas.award(fanId, artistId, BadgeCode.BASIC_YEAR_2);
		}
		if (years >= 3) {
			bas.award(fanId, artistId, BadgeCode.BASIC_YEAR_3);
		}

		// 멤버십 이벤트 처리 누락으로 과거 가입 이력은 있지만 배지가 없는 사용자도 복구한다.
		// 정상 지급됐던 사용자에게 다시 호출해도 BadgeAwardService가 멱등하게 무시한다.
		checkMembershipBadges(fanId, artistId);
		
		checkDebutAnniversary(fanId, artistId, joinedDate, today);
	}

	private void checkMembershipBadges(Long fanId, Long artistId) {
		int streak = mpr.findTopByFanIdAndArtistIdOrderByStartedAtDesc(fanId, artistId)
				.map(period -> period.getStreakCount())
				.orElse(0);
		BadgeCode[] membershipBadges = {
				BadgeCode.SPECIAL_MEMBERSHIP_1,
				BadgeCode.SPECIAL_MEMBERSHIP_2,
				BadgeCode.SPECIAL_MEMBERSHIP_3,
				BadgeCode.SPECIAL_MEMBERSHIP_4,
				BadgeCode.SPECIAL_MEMBERSHIP_5
		};
		for (int i = 0; i < Math.min(streak, membershipBadges.length); i++) {
			bas.award(fanId, artistId, membershipBadges[i]);
		}
	}
	
	// ---------- 데뷔 N주년 배지 ----------
	// 주년 당일에 가입해 있으면 지급이 규칙이라, 오늘이 정확히 데뷔 기념일인 날에만 준다.
	// 이미 지나간 주년은 주지 않는다. (그때 함께하지 않았으므로)
	private void checkDebutAnniversary(Long fanId, Long artistId, LocalDate joinedDate, LocalDate today) {
		LocalDate debutDate = apr.findByUser_Id(artistId)
				.map(ArtistAccountProfile::getDebutDate)
				.orElse(null);
		if (debutDate == null) {
			return;
		}
		
		long debutYears = ChronoUnit.YEARS.between(debutDate, today);
		if (debutYears < 1 || debutYears > 3) {
			return;
		}
		
		// 오늘이 데뷔일의 N주년 당일인가?
		if (!debutDate.plusYears(debutYears).isEqual(today)) {
			return;
		}
		
		if (joinedDate.isAfter(today)) {
			return;
		}
		
		BadgeCode code = switch ((int) debutYears) {
			case 1 -> BadgeCode.SPECIAL_DEBUT_1;
			case 2 -> BadgeCode.SPECIAL_DEBUT_2;
			default -> BadgeCode.SPECIAL_DEBUT_3;
		};
		bas.award(fanId, artistId, code);
	}
}
