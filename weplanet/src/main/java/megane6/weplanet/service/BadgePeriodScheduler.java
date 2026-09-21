package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BadgePeriodScheduler {
	private final BadgePeriodService bps;
	
	@Scheduled(cron = "0 10 4 * * *")
	public void awardPeriodBadges() {
		int checked = bps.checkAll();
		log.info("기간 배지 확인 완료: 커뮤니티 가입 {}건", checked);
	}
}
