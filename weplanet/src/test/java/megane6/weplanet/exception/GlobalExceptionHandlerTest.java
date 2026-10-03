package megane6.weplanet.exception;

import megane6.weplanet.i18n.Messages;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc;

	GlobalExceptionHandlerTest() {
		StaticMessageSource messageSource = new StaticMessageSource();
		messageSource.addMessage("error.badRequest", Locale.KOREAN, "요청 형식이 올바르지 않습니다.");
		messageSource.addMessage("error.forbidden", Locale.KOREAN, "이 요청을 처리할 권한이 없습니다.");
		messageSource.addMessage("error.test.notOwner", Locale.KOREAN, "본인 글만 수정할 수 있습니다.");
		mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
				.setControllerAdvice(new GlobalExceptionHandler(new Messages(messageSource)))
				.build();
	}

	// 글자가 깨진 요청 등 - Tomcat 내부 문구 대신 "요청 형식 오류"(400)
	@Test
	void tomcatInvalidParameterIsBadRequestWithoutInternalMessage() throws Exception {
		mockMvc.perform(get("/invalid-parameter").header("X-Requested-With", "fetch").locale(Locale.KOREAN))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."));
	}

	// 라이브러리가 던진 IllegalStateException 의 메시지는 숨기고 일반 문구로
	@Test
	void libraryMessageIsReplacedWithGenericText() throws Exception {
		mockMvc.perform(get("/library-state").header("X-Requested-With", "fetch").locale(Locale.KOREAN))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("이 요청을 처리할 권한이 없습니다."));
	}

	// 우리 코드가 보여주려고 던진 예외 문구는 지금처럼 그대로 (메시지 키면 번역)
	@Test
	void ourOwnMessageIsStillShown() throws Exception {
		mockMvc.perform(get("/own-state").header("X-Requested-With", "fetch").locale(Locale.KOREAN))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("본인 글만 수정할 수 있습니다."));
	}

	@RestController
	static class ThrowingController {
		@GetMapping("/invalid-parameter")
		String invalidParameter() {
			throw new InvalidParameterException("Character decoding failed. Parameter [realName] with value [???] has been ignored.");
		}

		@GetMapping("/library-state")
		String libraryState() {
			Assert.state(false, "internal library detail");
			return "unreachable";
		}

		@GetMapping("/own-state")
		String ownState() {
			throw new IllegalStateException("error.test.notOwner");
		}
	}
}
