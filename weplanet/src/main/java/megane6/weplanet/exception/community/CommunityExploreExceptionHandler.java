package megane6.weplanet.exception.community;

import megane6.weplanet.controller.community.CommunityExploreController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice(assignableTypes = {CommunityExploreController.class})
public class CommunityExploreExceptionHandler {
	
	@ExceptionHandler(LoginRequiredException.class)
	public ResponseEntity<Map<String, String>> handleLoginRequired(LoginRequiredException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
	}
	
	@ExceptionHandler(ArtistNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleArtistNotFound(ArtistNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
	}
	
	@ExceptionHandler(CommunityNotJoinedException.class)
	public ResponseEntity<Map<String, String>> handleNotJoined(CommunityNotJoinedException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
	}
	
	// CommunityProfileRequestDto @Valid 실패(닉네임 10자 초과 등) - 필드별 에러 메시지 그대로 내려줌
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fe : e.getBindingResult().getFieldErrors()) {
			fieldErrors.put(fe.getField(), fe.getDefaultMessage());
		}
		return ResponseEntity.badRequest().body(Map.of("message", "입력값을 확인해주세요.", "fieldErrors", fieldErrors));
	}
}