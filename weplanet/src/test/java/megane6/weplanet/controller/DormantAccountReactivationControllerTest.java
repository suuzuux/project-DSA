package megane6.weplanet.controller;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.i18n.Messages;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.SocialLoginSessionSupport;
import megane6.weplanet.service.email.SignupEmailVerificationService;
import megane6.weplanet.service.email.VerificationPurpose;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class DormantAccountReactivationControllerTest {

	private static final String SAME_RESPONSE = "{\"success\":true,\"message\":\"reactivate.codeSent\"}";

	private final UserRepository userRepository = mock(UserRepository.class);
	private final SignupEmailVerificationService verificationService = mock(SignupEmailVerificationService.class);
	private final MockMvc mockMvc;

	DormantAccountReactivationControllerTest() {
		MessageSource messageSource = mock(MessageSource.class);
		when(messageSource.getMessage(any(), any(), any())).thenAnswer(inv -> inv.getArgument(0)); // 키를 그대로 문구로
		mockMvc = MockMvcBuilders.standaloneSetup(new DormantAccountReactivationController(userRepository,
				verificationService, mock(SocialLoginSessionSupport.class), messageSource, mock(Messages.class),
				mock(LocaleResolver.class))).build();
	}

	// 휴면·정상·없는 아이디 모두 응답이 같아야 한다
	@Test
	void respondsTheSameWhetherOrNotTheAccountIsDormant() throws Exception {
		User dormant = User.createFan("sleeper", "encoded", "이름", "닉", "sleeper@test.com");
		dormant.markDormant();
		User active = User.createFan("awake", "encoded", "이름", "닉", "awake@test.com");
		when(userRepository.findByUsername("sleeper")).thenReturn(Optional.of(dormant));
		when(userRepository.findByUsername("awake")).thenReturn(Optional.of(active));
		when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

		for (String username : new String[]{"sleeper", "awake", "nobody"}) {
			mockMvc.perform(post("/login/reactivate/code").param("username", username))
					.andExpect(content().json(SAME_RESPONSE, JsonCompareMode.STRICT));
		}

		// 실제 메일은 휴면 계정에만 보낸다
		verify(verificationService).sendVerificationCodeIfEligible(any(), eq(VerificationPurpose.REACTIVATE),
				eq("sleeper@test.com"), eq("sleeper@test.com"));
		verify(verificationService).sendVerificationCodeIfEligible(any(), eq(VerificationPurpose.REACTIVATE),
				eq("reactivate:awake"), isNull());
		verify(verificationService).sendVerificationCodeIfEligible(any(), eq(VerificationPurpose.REACTIVATE),
				eq("reactivate:nobody"), isNull());
	}
}
