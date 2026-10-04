package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
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

    // 새벽 3시에 하루 한 번 - 휴면 전환 30일 전 안내 메일, 그다음 휴면 전환.
    // 한 명씩 짧은 트랜잭션으로 처리하고 바로 커밋한다 (한 명이 실패해도 다른 사람 기록은 남는다).
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

    // 한 명 = 트랜잭션 하나. 발송에 성공했을 때만 "안내 완료"로 기록한다 (실패하면 다음 날 다시 시도).
    private boolean sendNotice(Long userId) {
        Boolean result = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getDormantNoticeSentAt() != null) {
                return false;
            }
            // 받을 수 없는 시스템 주소(카카오/LINE 가입자 등)는 메일 없이 안내한 것으로만 기록한다 (안 그러면 휴면 전환이 안 된다).
            // 이 사람들은 소셜로 다시 로그인하면 코드 없이 바로 휴면이 풀린다 (OAuth2LoginSuccessHandler).
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
        // 사전 안내 후 30일이 지나야 전환 (안내 메일의 "30일 후 휴면" 약속을 지키기 위함)
        LocalDateTime noticeThreshold = now.minusDays(NOTICE_BEFORE_DAYS);
        List<Long> targetIds = idsOf(userRepository.findActiveUsersDueForDormantConversion(threshold, noticeThreshold));
        for (Long userId : targetIds) {
            // 휴면 전환을 먼저 커밋하고, 커밋이 끝난 뒤에 완료 메일을 보낸다 (전환이 롤백됐는데 메일만 나가는 일 방지)
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
