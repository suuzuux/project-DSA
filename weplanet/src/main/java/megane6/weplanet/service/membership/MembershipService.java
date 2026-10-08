package megane6.weplanet.service.membership;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.MembershipPeriod;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.event.BadgeActivityEvent;
import megane6.weplanet.repository.membership.MembershipPeriodRepository;
import megane6.weplanet.repository.membership.MembershipRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

// 멤버십 가입·조회
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MembershipPeriodRepository periodRepository;
    private final ApplicationEventPublisher eventPublisher; // [배지] 멤버십 가입 이벤트 발행
    // 만료 후 이 기간 안에 재가입하면 연속으로 인정
    private static final int GRACE_DAYS = 7;
    
    // 멤버십 가입 - 이력이 있으면 만료일만 1년 연장, 없으면 새로 만든다 (팬·아티스트당 1행).
    @Transactional
    public Membership join(User fan, User artist) {
        Membership membership = membershipRepository.findByFanAndArtist(fan, artist)
                .orElseGet(() -> Membership.builder().fan(fan).artist(artist).build());
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusYears(1);
        membership.setExpiresAt(expiresAt);
        Membership saved = membershipRepository.save(membership);
        
        // [배지] 연속 횟수를 계산해 이력에 남긴다.
        recordPeriod(fan.getId(), artist.getId(), now, expiresAt);
        eventPublisher.publishEvent(new BadgeActivityEvent(
                fan.getId(), artist.getId(), BadgeActivityEvent.Activity.MEMBERSHIP_JOINED));
        
        return saved;
    }
    
    /** 가입·갱신 이력 추가 (직전 만료일 + 7일 안이면 연속 횟수 +1, 아니면 1부터). */
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

    // 만료되지 않은 멤버십이 있는지 (사이드바 가입중 표시)
    public boolean isActiveMember(User fan, User artist) {
        return membershipRepository.findByFanAndArtist(fan, artist)
                .map(membership -> !membership.isExpired())
                .orElse(false);
    }

    public Optional<Membership> getMembership(User fan, User artist) {
        return membershipRepository.findByFanAndArtist(fan, artist);
    }

    // 멤버십 해지 - 레코드를 지운다 (DM 접근 시 미가입과 같게 처리됨).
    public void cancel(User fan, User artist) {
        membershipRepository.findByFanAndArtist(fan, artist)
                .ifPresent(membershipRepository::delete);
    }
}
