package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.DormantAccountNoticeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DormantAccountScheduler {

    private static final long DORMANT_AFTER_DAYS = 365;
    private static final long NOTICE_BEFORE_DAYS = 30;

    private final UserRepository userRepository;
    private final DormantAccountNoticeService noticeService;

    // ProjectStatusScheduler(매분)와 달리 하루 단위 판정이라 새벽 3시에 한 번만 돈다.
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void processDormantAccounts() {
        LocalDateTime now = LocalDateTime.now();
        sendDormantNotices(now);
        convertToDormant(now);
    }

    private void sendDormantNotices(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(DORMANT_AFTER_DAYS - NOTICE_BEFORE_DAYS);
        List<User> targets = userRepository.findActiveUsersDueForDormantNotice(threshold);
        for (User user : targets) {
            // AUTH-11: 발송에 성공했을 때만 "안내 완료"로 기록한다. 예전에는 finally 에서 실패해도 기록해서,
            // SMTP 장애가 난 날의 대상자는 안내를 못 받은 채 30일 뒤 휴면으로 바뀌었다. 실패한 사람은 다음 날 다시 보낸다.
            try {
                noticeService.sendDormantNotice(user);
                user.markDormantNoticeSent();
            } catch (Exception e) {
                log.error("[휴면계정] 사전 안내 메일 발송 실패 - 다음 실행 때 다시 시도 (userId={})", user.getId(), e);
            }
        }
        if (!targets.isEmpty()) log.info("[휴면계정] 사전 안내 대상 {}건", targets.size());
    }

    private void convertToDormant(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(DORMANT_AFTER_DAYS);
        // 사전 안내 후 30일이 지나야 전환 (안내 메일의 "30일 후 휴면" 약속을 지키기 위함)
        LocalDateTime noticeThreshold = now.minusDays(NOTICE_BEFORE_DAYS);
        List<User> targets = userRepository.findActiveUsersDueForDormantConversion(threshold, noticeThreshold);
        for (User user : targets) {
            user.markDormant();
            try {
                noticeService.sendDormantConvertedNotice(user);
            } catch (Exception e) {
                log.error("[휴면계정] 전환 완료 메일 발송 실패 (userId={})", user.getId(), e);
            }
        }
        if (!targets.isEmpty()) log.info("[휴면계정] 휴면 전환 {}건", targets.size());
    }
}
