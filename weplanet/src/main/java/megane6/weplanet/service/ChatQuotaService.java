package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.ChatQuota;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ChatQuotaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ChatQuotaService {

    private static final int DAILY_LIMIT = 10;

    private final ChatQuotaRepository chatQuotaRepository;

    // 오늘 이 아티스트한테 메세지를 더 보낼 수 있으면 true 반환하면서 1개 차감
    // 없으면 false
    public boolean tryConsume(User fan, User artist) {
        ChatQuota quota = chatQuotaRepository.findByFanAndArtist(fan, artist)
                .orElseGet(() -> ChatQuota.builder()
                        .fan(fan)
                        .artist(artist)
                        .remainingCount(DAILY_LIMIT)
                        .chargedDate(LocalDate.now())
                        .build());

        // 마지막으로 채워진 날짜가 오늘이 아니면 하루가 지난 것이므로 한도를 다시 채움
        if (!quota.getChargedDate().isEqual(LocalDate.now())) {
            quota.setRemainingCount(DAILY_LIMIT);
            quota.setChargedDate(LocalDate.now());
        }

        if (quota.getRemainingCount() <= 0) {
            chatQuotaRepository.save(quota);
            return false;
        }

        quota.setRemainingCount(quota.getRemainingCount() - 1);
        chatQuotaRepository.save(quota);
        return true;
    }

    // 실제로 소진하지 않고, 지금 남은 횟수만 조회 (화면 표시용)
    public int getRemaining(User fan, User artist) {
        return chatQuotaRepository.findByFanAndArtist(fan, artist)
                .map(quota -> quota.getChargedDate().isEqual(LocalDate.now())
                        ? quota.getRemainingCount()
                        : DAILY_LIMIT)
                .orElse(DAILY_LIMIT);
    }
}
