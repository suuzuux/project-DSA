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
	
	// 한 팬의 모든 커뮤니티 확인 (컬렉션 화면)
	public void checkForFan(Long fanId) {
		if (fanId == null) {
			return;
		}
		checkMembers(cmr.findByFanId(fanId));
	}
	
	// 전체 회원 확인 (매일 스케줄러)
	public int checkAll() {
		List<CommunityMember> members = cmr.findAll();
		checkMembers(members);
		return members.size();
	}
	
	private void checkMembers(List<CommunityMember> members) {
		LocalDate today = LocalDate.now();
		for (CommunityMember member : members) {
			try {
				checkMember(member, today);
			} catch (Exception e) {
				// 한 명이 실패해도 나머지는 계속 확인한다.
				log.warn("기간 배지 확인 실패 : fanId={}, artistId={}, reason={}",
						member.getFanId(), member.getArtistId(), e.getMessage());
			}
		}
	}
	
	private void checkMember(CommunityMember member, LocalDate today) {
		Long fanId = member.getFanId();
		Long artistId = member.getArtistId();
		LocalDate joinedDate = member.getJoinedAt().toLocalDate();
		
		// 가입 후 N일
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
		
		// 가입 후 N년 (윤년을 고려해 YEARS 사용)
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

		// 멤버십 배지 누락 복구 (멱등이라 다시 호출해도 안전).
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
	
	// 데뷔 N주년 배지 - 기념일 당일 가입자에게만 준다.
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
		
		// 오늘이 데뷔 N주년 당일인지
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
