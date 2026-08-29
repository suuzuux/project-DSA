package megane6.weplanet.service;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.Project;
import megane6.weplanet.domain.entity.enumfolder.FanProjectStatus;
import megane6.weplanet.repository.ProjectRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectStatusScheduler {
	private final ProjectRepository pr;
	
	/**
	 * 매분 0초마다 프로젝트 모금 상태를 현재 시각에 맞춘다.
	 */
	@Scheduled(cron = "0 * * * * *")
	@Transactional
	public void synchronizeFundingStatuses() {
		List<Project> projects = pr.findByStatusInAndDeletedAtIsNull(
				List.of(
						FanProjectStatus.APPROVED,
						FanProjectStatus.FUNDING
				)
		);
		LocalDateTime now = LocalDateTime.now();
		projects.forEach(project -> project.synchronizeFundingStatus(now));
	}
}
