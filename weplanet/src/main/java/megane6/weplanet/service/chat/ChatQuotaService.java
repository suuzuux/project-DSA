package megane6.weplanet.service.chat;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.ChatQuota;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.chat.ChatQuotaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

// 팬 → 아티스트 하루 채팅 전송 횟수 제한
@Service
@RequiredArgsConstructor
public class ChatQuotaService {

    private static final int DAILY_LIMIT = 10; // 하루 최대 전송 수

    private final ChatQuotaRepository chatQuotaRepository;

    /** 남은 횟수가 있으면 1 차감하고 true (첫 채팅이면 한도를 채워 새로 만든다). */
    public boolean tryConsume(User fan, User artist) {
        ChatQuota quota = chatQuotaRepository.findByFanAndArtist(fan, artist)
                .orElseGet(() -> ChatQuota.builder()
                        .fan(fan)
                        .artist(artist)
                        .remainingCount(DAILY_LIMIT)
                        .chargedDate(LocalDate.now())
                        .build());

        // 날짜가 바뀌었으면 한도를 다시 채운다.
        if (!quota.getChargedDate().isEqual(LocalDate.now())) {
            quota.setRemainingCount(DAILY_LIMIT);
            quota.setChargedDate(LocalDate.now());
        }

        if (quota.getRemainingCount() <= 0) {
            chatQuotaRepository.save(quota);
            return false; // 한도 소진
        }

        quota.setRemainingCount(quota.getRemainingCount() - 1);
        chatQuotaRepository.save(quota);
        return true; // 1 차감하고 허용
    }

    /** 차감 없이 남은 횟수 조회 (기록이 없거나 날짜가 지났으면 최대치). */
    public int getRemaining(User fan, User artist) {
        return chatQuotaRepository.findByFanAndArtist(fan, artist)
                .map(quota -> quota.getChargedDate().isEqual(LocalDate.now())
                        ? quota.getRemainingCount()
                        : DAILY_LIMIT)
                .orElse(DAILY_LIMIT);
    }
}
