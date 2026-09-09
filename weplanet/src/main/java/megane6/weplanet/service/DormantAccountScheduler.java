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
            try {
                noticeService.sendDormantNotice(user);
            } catch (Exception e) {
                log.error("[휴면계정] 사전 안내 메일 발송 실패 (userId={})", user.getId(), e);
            } finally {
                user.markDormantNoticeSent();
            }
        }
        if (!targets.isEmpty()) log.info("[휴면계정] 사전 안내 대상 {}건", targets.size());
    }

    private void convertToDormant(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(DORMANT_AFTER_DAYS);
        List<User> targets = userRepository.findActiveUsersDueForDormantConversion(threshold);
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
