package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Membership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.MembershipRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

// 멤버십 가입/조회 로직 (27번 - Membership 가입하기 버튼 실제 동작)
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;

    // 멤버십 가입 - 이미 가입(또는 만료된 가입) 이력이 있으면 만료일만 1년 뒤로 갱신하고,
    // 처음 가입하는 거면 새로 만듦 (fan_id + artist_id 조합에 유니크 제약이 있어서 한 쌍에 레코드 하나만 존재)
    public Membership join(User fan, User artist) {
        Membership membership = membershipRepository.findByFanAndArtist(fan, artist)
                .orElseGet(() -> Membership.builder().fan(fan).artist(artist).build());

        membership.setExpiresAt(LocalDateTime.now().plusYears(1));

        return membershipRepository.save(membership);
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
}
