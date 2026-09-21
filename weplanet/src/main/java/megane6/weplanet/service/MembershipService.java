package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.MembershipPeriod;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.MembershipPeriodRepository;
import megane6.weplanet.repository.MembershipRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

// 멤버십 가입/조회 로직 (27번 - Membership 가입하기 버튼 실제 동작)
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MembershipPeriodRepository periodRepository;
    private final ApplicationEventPublisher eventPublisher; // [배지] 멤버십 가입 알림 발행용
    // 만료 후 이 기간 안에 다시 가입하면 "연속"으로 인정한다
    private static final int GRACE_DAYS = 7;
    
    // 멤버십 가입 - 이미 가입(또는 만료된 가입) 이력이 있으면 만료일만 1년 뒤로 갱신하고,
    // 처음 가입하는 거면 새로 만듦 (fan_id + artist_id 조합에 유니크 제약이 있어서 한 쌍에 레코드 하나만 존재)
    @Transactional
    public Membership join(User fan, User artist) {
        Membership membership = membershipRepository.findByFanAndArtist(fan, artist)
                .orElseGet(() -> Membership.builder().fan(fan).artist(artist).build());
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusYears(1);
        membership.setExpiresAt(expiresAt);
        Membership saved = membershipRepository.save(membership);
        
        // [배지] 이번 가입이 연속 몇 번째인지 계산해서 이력으로 남긴다
        recordPeriod(fan.getId(), artist.getId(), now, expiresAt);
        eventPublisher.publishEvent(new BadgeActivityEvent(
                fan.getId(), artist.getId(), BadgeActivityEvent.Activity.MEMBERSHIP_JOINED));
        
        return saved;
    }
    
    /**
     * 이번 가입/갱신을 이력에 한 줄 추가한다.
     * <p>
     * 직전 기간의 만료일 + 7일(유예) 안에 다시 가입했으면 연속으로 보고 횟수를 1 늘린다.
     * 그보다 늦었으면 끊긴 것으로 보고 1부터 다시 센다.
     */
    private void recordPeriod(Long fanId, Long artistId, LocalDateTime startedAt, LocalDateTime expiresAt) {
        int streakCount = periodRepository.findTopByFanIdAndArtistIdOrderByStartedAtDesc(fanId, artistId)
                .filter(previous -> !startedAt.isAfter(previous.getExpiresAt().plusDays(GRACE_DAYS)))
                .map(previous -> previous.getStreakCount() + 1)
                .orElse(1);
        
        periodRepository.save(MembershipPeriod.builder()
                .fanId(fanId)
                .artistId(artistId)
                .startedAt(startedAt)
                .expiresAt(expiresAt)
                .streakCount(streakCount)
                .build());
    }

    // 사이드바에 "✔️ 가입중" 표시할지 확인용 - 만료 안 된 멤버십이 있으면 true
    public boolean isActiveMember(User fan, User artist) {
        return membershipRepository.findByFanAndArtist(fan, artist)
                .map(membership -> !membership.isExpired())
                .orElse(false);
    }

    public Optional<Membership> getMembership(User fan, User artist) {
        return membershipRepository.findByFanAndArtist(fan, artist);
    }

    // 멤버십 해지 - 레코드 자체를 지움 (레코드 없음 = DM 접근 시 "미가입"과 동일하게 처리되므로 자연스럽게 막힘)
    public void cancel(User fan, User artist) {
        membershipRepository.findByFanAndArtist(fan, artist)
                .ifPresent(membershipRepository::delete);
    }
}
