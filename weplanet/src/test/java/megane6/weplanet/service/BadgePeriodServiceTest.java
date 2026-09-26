package megane6.weplanet.service;

import megane6.weplanet.domain.entity.MembershipPeriod;
import megane6.weplanet.domain.entity.community.CommunityMember;
import megane6.weplanet.domain.entity.enumfolder.BadgeCode;
import megane6.weplanet.repository.ArtistAccountProfileRepository;
import megane6.weplanet.repository.MembershipPeriodRepository;
import megane6.weplanet.repository.community.CommunityMemberRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgePeriodServiceTest {

	@Test
	void collectionCheckBackfillsMissingMembershipBadges() {
		CommunityMemberRepository memberRepository = mock(CommunityMemberRepository.class);
		ArtistAccountProfileRepository artistProfileRepository = mock(ArtistAccountProfileRepository.class);
		MembershipPeriodRepository periodRepository = mock(MembershipPeriodRepository.class);
		BadgeAwardService awardService = mock(BadgeAwardService.class);
		BadgePeriodService service = new BadgePeriodService(
				memberRepository, artistProfileRepository, periodRepository, awardService);
		CommunityMember member = CommunityMember.builder()
				.fanId(11L)
				.artistId(22L)
				.joinedAt(LocalDateTime.now())
				.build();

		when(memberRepository.findByFanId(11L)).thenReturn(List.of(member));
		when(periodRepository.findTopByFanIdAndArtistIdOrderByStartedAtDesc(11L, 22L))
				.thenReturn(Optional.of(MembershipPeriod.builder().streakCount(3).build()));
		when(artistProfileRepository.findByUser_Id(22L)).thenReturn(Optional.empty());

		service.checkForFan(11L);

		verify(awardService).award(11L, 22L, BadgeCode.SPECIAL_MEMBERSHIP_1);
		verify(awardService).award(11L, 22L, BadgeCode.SPECIAL_MEMBERSHIP_2);
		verify(awardService).award(11L, 22L, BadgeCode.SPECIAL_MEMBERSHIP_3);
	}
}
