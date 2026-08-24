package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.ChatQuota;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.ChatQuotaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

// 팬 -> 아티스트 하루 채팅 전송 횟수 제한(CHAT-05)을 관리하는 서비스
@Service
@RequiredArgsConstructor
public class ChatQuotaService {

    private static final int DAILY_LIMIT = 10; // 하루에 보낼 수 있는 최대 메시지 개수

    private final ChatQuotaRepository chatQuotaRepository;

    /**
     * 오늘 이 팬이 이 아티스트에게 메시지를 더 보낼 수 있으면, 1개를 차감하고 true를 돌려줌.
     * 이미 다 썼으면 아무것도 차감하지 않고 false를 돌려줌.
     * <p>
     * orElseGet(() -> ...) : findByFanAndArtist가 값을 못 찾았을 때(Optional이 비어있을 때)
     * 실행할 "대체 로직"을 넣어주는 부분. 즉 "이 팬-아티스트 조합으로 처음 채팅하는 거라면,
     * 오늘 날짜 기준으로 한도가 꽉 찬 새 ChatQuota를 하나 만들어서 시작하자"는 뜻.
     */
    public boolean tryConsume(User fan, User artist) {
        ChatQuota quota = chatQuotaRepository.findByFanAndArtist(fan, artist)
                .orElseGet(() -> ChatQuota.builder()
                        .fan(fan)
                        .artist(artist)
                        .remainingCount(DAILY_LIMIT)
                        .chargedDate(LocalDate.now())
                        .build());

        // 마지막으로 채워진 날짜(chargedDate)가 오늘이 아니면 "하루가 지났다"는 뜻이므로 한도를 다시 채워줌
        if (!quota.getChargedDate().isEqual(LocalDate.now())) {
            quota.setRemainingCount(DAILY_LIMIT);
            quota.setChargedDate(LocalDate.now());
        }

        if (quota.getRemainingCount() <= 0) {
            chatQuotaRepository.save(quota);
            return false; // 오늘 한도를 이미 다 써버렸음
        }

        quota.setRemainingCount(quota.getRemainingCount() - 1);
        chatQuotaRepository.save(quota);
        return true; // 정상적으로 1개 차감하고 전송 허용
    }

    /**
     * 실제로 개수를 깎지 않고, 지금 남은 횟수만 화면에 보여주기 위해 조회함.
     * <p>
     * .map(quota -> ...) : Optional 안에 값이 "있을 때만" 괄호 안의 계산을 실행해서 그 결과를 담고,
     * 값이 없으면 아무것도 안 하고 그대로 지나감 (마지막 .orElse(DAILY_LIMIT)에서 기본값으로 대체됨).
     * 즉 "아직 한 번도 채팅 안 한 팬이면 기본 한도(DAILY_LIMIT)를, 이미 있으면 그 값을(단, 날짜가 지났으면
     * 다시 꽉 찬 걸로 간주해서) 보여준다"는 로직을 한 줄로 표현한 것.
     */
    public int getRemaining(User fan, User artist) {
        return chatQuotaRepository.findByFanAndArtist(fan, artist)
                .map(quota -> quota.getChargedDate().isEqual(LocalDate.now())
                        ? quota.getRemainingCount()
                        : DAILY_LIMIT)
                .orElse(DAILY_LIMIT);
    }
}
