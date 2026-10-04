package megane6.weplanet.service.email;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignupEmailVerificationRateLimitTest {

	private final JavaMailSender mailSender = mock(JavaMailSender.class);
	private final VerificationMailAsyncSender asyncSender = mock(VerificationMailAsyncSender.class);
	private final SignupEmailVerificationService service;
	private final MockHttpSession session = new MockHttpSession();

	SignupEmailVerificationRateLimitTest() {
		MessageSource messageSource = mock(MessageSource.class);
		when(messageSource.getMessage(any(), any(), any())).thenReturn("text");
		service = new SignupEmailVerificationService(mailSender, messageSource, asyncSender);
		requestFrom("1.1.1.1");
	}

	@AfterEach
	void clearRequest() {
		RequestContextHolder.resetRequestAttributes();
	}

	// 받는 주소를 바꿔 가며 보내도 같은 IP 는 10분에 5통까지만
	@Test
	void sameIpIsLimitedEvenWithDifferentAddresses() {
		for (int i = 0; i < SignupEmailVerificationService.IP_WINDOW_LIMIT; i++) {
			service.sendVerificationCode(session, VerificationPurpose.SIGNUP, "user" + i + "@test.com");
		}
		VerificationRateLimitException e = assertThrows(VerificationRateLimitException.class,
				() -> service.sendVerificationCode(session, VerificationPurpose.SIGNUP, "another@test.com"));

		assertEquals("verification.error.tooManyRequests", e.getMessageKey());
		verify(mailSender, times(SignupEmailVerificationService.IP_WINDOW_LIMIT)).send(any(SimpleMailMessage.class));

		requestFrom("2.2.2.2"); // 다른 IP 는 영향 없음
		service.sendVerificationCode(session, VerificationPurpose.SIGNUP, "another@test.com");
	}

	// 찾기(계정 존재를 숨기는 곳): 대상이면 백그라운드로 보내고, 대상이 아니어도 발송 제한은 똑같이 센다
	@Test
	void eligibleSendsInBackgroundAndBothCountTowardTheLimit() {
		service.sendVerificationCodeIfEligible(session, VerificationPurpose.FIND_ID, "match@test.com", true);
		verify(asyncSender).send(any(SimpleMailMessage.class));
		verify(mailSender, never()).send(any(SimpleMailMessage.class));

		for (int i = 1; i < SignupEmailVerificationService.IP_WINDOW_LIMIT; i++) {
			service.sendVerificationCodeIfEligible(session, VerificationPurpose.FIND_ID, "nomatch" + i + "@test.com", false);
		}
		assertThrows(VerificationRateLimitException.class,
				() -> service.sendVerificationCodeIfEligible(session, VerificationPurpose.FIND_ID, "x@test.com", false));
		verify(asyncSender, times(1)).send(any(SimpleMailMessage.class));
	}

	// 사이트 전체 하루 한도: 실제로 보낸 메일이 400통이 되면 그날은 더 보내지 않는다
	@Test
	void serviceWideDailyLimitStopsSending() {
		int sent = 0;
		for (int ip = 0; sent < SignupEmailVerificationService.SERVICE_DAILY_LIMIT; ip++) {
			requestFrom("10.0." + (ip / 250) + "." + (ip % 250));
			for (int i = 0; i < SignupEmailVerificationService.IP_WINDOW_LIMIT
					&& sent < SignupEmailVerificationService.SERVICE_DAILY_LIMIT; i++) {
				service.sendVerificationCode(session, VerificationPurpose.SIGNUP, "u" + sent + "@test.com");
				sent++;
			}
		}
		requestFrom("192.168.0.1");
		VerificationRateLimitException e = assertThrows(VerificationRateLimitException.class,
				() -> service.sendVerificationCode(session, VerificationPurpose.SIGNUP, "last@test.com"));
		assertEquals("verification.error.serviceLimit", e.getMessageKey());
	}

	private static void requestFrom(String ip) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(ip);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}
}
