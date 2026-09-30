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

    // ProjectStatusScheduler(매분)와 달리 하루 단위 판정이라 새벽 3시에 한 번만 돈다.
    // AUTH-11: 예전에는 배치 전체가 트랜잭션 하나(@Transactional)라서, 대상자 전원에게 메일을 다 보낸 뒤 마지막 커밋이
    // 실패하면 "안내 완료" 기록만 롤백되고 메일은 이미 나간 상태가 되어 다음 날 같은 메일이 또 나갔다. 대상이 많으면
    // DB 커넥션도 배치가 끝날 때까지 붙잡고 있었다. 이제 한 명씩 짧은 트랜잭션으로 처리하고 바로 커밋한다.
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

    // 한 명 = 트랜잭션 하나. 발송에 성공했을 때만 "안내 완료"로 기록하고 곧바로 커밋한다.
    // (발송 실패 시 기록하지 않음 → 다음 날 다시 시도 / AUTH-11 에서 finally 기록을 없앤 것과 같은 규칙)
    private boolean sendNotice(Long userId) {
        Boolean result = new TransactionTemplate(transactionManager).execute(status -> {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getDormantNoticeSentAt() != null) {
                return false;
            }
            // AUTH-11: 카카오/LINE 가입자처럼 받을 수 없는 시스템 주소(*.weplanet.local)는 메일을 보내지 않고
            // 안내한 것으로만 기록한다. 기록하지 않으면 30일 조건 때문에 영원히 휴면 전환이 안 된다.
            // (이 사람들은 소셜로 다시 로그인하면 코드 없이 바로 휴면이 풀린다 - OAuth2LoginSuccessHandler 참고)
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
