package megane6.weplanet.service;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.email.DormantAccountNoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 휴면 배치 (매일 03:00): 1년이 되기 30일 전에 안내 메일, 안내 후 30일이 지나면 휴면 전환.
// 실제로 1년을 기다릴 수 없으니, 대상 조회 결과를 가짜로 넣고 배치를 한 번 실행해 본다.
class DormantAccountSchedulerTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final DormantAccountNoticeService noticeService = mock(DormantAccountNoticeService.class);
	// 한 명씩 트랜잭션으로 처리하는 부분 - 테스트에서는 실제 DB 트랜잭션 없이 안의 코드만 실행된다
	private final DormantAccountScheduler scheduler = new DormantAccountScheduler(
			userRepository, noticeService, mock(PlatformTransactionManager.class));

	private final User fan = fan(1L, "fan01@test.com");
	private final User kakaoFan = fan(2L, "kakao_2@kakao.weplanet.local");   // 메일을 받을 수 없는 시스템 주소

	@BeforeEach
	void setUp() {
		when(userRepository.findById(1L)).thenReturn(Optional.of(fan));
		when(userRepository.findById(2L)).thenReturn(Optional.of(kakaoFan));
		when(userRepository.findActiveUsersDueForDormantNotice(any())).thenReturn(List.of());
		when(userRepository.findActiveUsersDueForDormantConversion(any(), any())).thenReturn(List.of());
	}

	// 335일 미접속: 안내 메일을 보내고 "안내 완료"로 기록. 받을 수 없는 주소는 메일 없이 기록만 (안 그러면 휴면 전환이 안 됨)
	@Test
	void sendsNoticeThirtyDaysBeforeDormant() {
		when(userRepository.findActiveUsersDueForDormantNotice(any())).thenReturn(List.of(fan, kakaoFan));

		scheduler.processDormantAccounts();

		ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(userRepository).findActiveUsersDueForDormantNotice(threshold.capture());
		assertAbout(LocalDateTime.now().minusDays(365 - 30), threshold.getValue());
		verify(noticeService).sendDormantNotice(fan);
		verify(noticeService, never()).sendDormantNotice(kakaoFan);
		assertNotNull(fan.getDormantNoticeSentAt());
		assertNotNull(kakaoFan.getDormantNoticeSentAt());
	}

	// 메일 발송이 실패하면 "안내 완료"로 남기지 않는다 - 다음 날 다시 보내서 안내 없이 휴면되는 일이 없게
	@Test
	void failedNoticeIsRetriedNextDay() {
		when(userRepository.findActiveUsersDueForDormantNotice(any())).thenReturn(List.of(fan));
		doThrow(new RuntimeException("메일 서버 오류")).when(noticeService).sendDormantNotice(fan);

		scheduler.processDormantAccounts();

		assertNull(fan.getDormantNoticeSentAt());
	}

	// 1년 미접속 + 안내 후 30일: 휴면으로 바꾸고 완료 메일. 받을 수 없는 주소는 전환만
	@Test
	void convertsToDormantAfterNoticePeriod() {
		when(userRepository.findActiveUsersDueForDormantConversion(any(), any())).thenReturn(List.of(fan, kakaoFan));

		scheduler.processDormantAccounts();

		ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> noticeThreshold = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(userRepository).findActiveUsersDueForDormantConversion(threshold.capture(), noticeThreshold.capture());
		assertAbout(LocalDateTime.now().minusDays(365), threshold.getValue());
		assertAbout(LocalDateTime.now().minusDays(30), noticeThreshold.getValue());
		assertEquals(UserStatus.DORMANT, fan.getStatus());
		assertEquals(UserStatus.DORMANT, kakaoFan.getStatus());
		verify(noticeService).sendDormantConvertedNotice(fan);
		verify(noticeService, never()).sendDormantConvertedNotice(kakaoFan);
	}

	private static void assertAbout(LocalDateTime expected, LocalDateTime actual) {
		assertTrue(Duration.between(expected, actual).abs().toMinutes() < 1,
				() -> "기대한 기준 시각 " + expected + " / 실제 " + actual);
	}

	private static User fan(Long id, String email) {
		User user = User.createFan("fan0" + id, "encoded", "이름", "닉네임", email);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}
}
