package megane6.weplanet.service.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.email.DormantAccountNoticeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

    // 매일 새벽 3시 - 휴면 30일 전 안내 후 휴면 전환 (한 명씩 커밋).
    @Scheduled(cron = "0 0 3 * * *")
    public void processDormantAccounts() {
        LocalDateTime now = LocalDateTime.now();
        sendDormantNotices(now);
        convertToDormant(now);
    }

    private void sendDormantNotices(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(DORMANT_AFTER_DAYS - NOTICE_BEFORE_DAYS);
        List<Long> targetIds = idsOf(userRepository.findActiveUsersDueForDormantNotice(threshold));
        int done = 0;
        for (Long userId : targetIds) {
            if (sendNotice(userId)) {
                done++;
            }
        }
        if (!targetIds.isEmpty()) log.info("[휴면계정] 사전 안내 대상 {}건 중 {}건 처리", targetIds.size(), done);
    }

    // 한 명씩 처리하고 발송 성공 시에만 안내 완료로 기록한다.
    private boolean sendNotice(Long userId) {
        Boolean result = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getDormantNoticeSentAt() != null) {
                return false;
            }
            // 수신 불가 주소는 메일 없이 안내 완료로 기록한다 (소셜 재로그인 시 바로 해제).
            if (!user.hasPlaceholderEmail()) {
                try {
                    noticeService.sendDormantNotice(user);
                } catch (Exception e) {
                    log.error("[휴면계정] 사전 안내 메일 발송 실패 - 다음 실행 때 다시 시도 (userId={})", userId, e);
                    return false;
                }
            }
            user.markDormantNoticeSent();
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private void convertToDormant(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(DORMANT_AFTER_DAYS);
        // 안내 후 30일이 지나야 전환한다.
        LocalDateTime noticeThreshold = now.minusDays(NOTICE_BEFORE_DAYS);
        List<Long> targetIds = idsOf(userRepository.findActiveUsersDueForDormantConversion(threshold, noticeThreshold));
        for (Long userId : targetIds) {
            // 휴면 전환을 커밋한 뒤 완료 메일을 보낸다.
            User converted = new TransactionTemplate(transactionManager).execute(status -> {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null || !user.isLoginable()) {
                    return null;
                }
                user.markDormant();
                return user;
            });
            if (converted == null || converted.hasPlaceholderEmail()) {
                continue;
            }
            try {
                noticeService.sendDormantConvertedNotice(converted);
            } catch (Exception e) {
                log.error("[휴면계정] 전환 완료 메일 발송 실패 (userId={})", userId, e);
            }
        }
        if (!targetIds.isEmpty()) log.info("[휴면계정] 휴면 전환 {}건", targetIds.size());
    }

    private static List<Long> idsOf(List<User> users) {
        return users.stream().map(User::getId).toList();
    }
}
