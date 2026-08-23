package megane6.weplanet.controller.media;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.media.BoardMediaDTO;
import megane6.weplanet.domain.dto.media.BoardMediaViewDTO;
import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;   // ★ 파일 서빙용
import megane6.weplanet.service.media.BoardMediaService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;                         // ★ 빠져있던 import
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/board")
public class BoardMediaController {

	private final BoardMediaService service;

	// ※ 예전 빈 media() 는 삭제했어요. 아래 media(...) 와 같은 /board/media 라 충돌났었음.

	/** 미디어 목록: 소속사면 관리 화면, 그 외(팬 등)는 읽기 전용 */
	@GetMapping("/media")
	public String media(@RequestParam(defaultValue = "1") Long groupId,
	                    @RequestParam(defaultValue = "0") int page,
	                    @RequestParam(required = false) String role,   // ⚠️ 테스트용
	                    HttpSession session, Model model) {
		Page<BoardMediaViewDTO> list = service.list(groupId, page, 12);
		model.addAttribute("list", list);
		model.addAttribute("groupId", groupId);
		// templates/media/ 아래 두 파일
		return isAgency(session, role) ? "media/boardMediaViewForAgency" : "media/boardMediaViewForFan";
	}

	/** 업로드 (소속사만) */
	@PostMapping("/media/upload")
	public String upload(@ModelAttribute BoardMediaDTO dto,
	                     @RequestParam(required = false) String role,
	                     @RequestParam(required = false) Long uploaderId, // 테스트용
	                     HttpSession session, RedirectAttributes ra) {
		if (!isAgency(session, role)) return deny(ra, dto.getGroupId(), role);
		try {
			service.create(dto, currentUserId(session, uploaderId));      // ★ upload → create
			ra.addFlashAttribute("msg", "업로드되었습니다.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return redirect(dto.getGroupId(), role);
	}

	/** 편집 (소속사만) */
	@PostMapping("/media/{id}/edit")
	public String edit(@PathVariable Long id, @ModelAttribute BoardMediaDTO dto,
	                   @RequestParam(required = false) String role,
	                   HttpSession session, RedirectAttributes ra) {
		if (!isAgency(session, role)) return deny(ra, dto.getGroupId(), role);
		try {
			service.edit(id, dto);
			ra.addFlashAttribute("msg", "수정되었습니다.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return redirect(dto.getGroupId(), role);
	}

	/** 게시글 삭제 (소속사만) — 소프트 삭제 */
	@PostMapping("/media/{id}/delete")
	public String delete(@PathVariable Long id, @RequestParam Long groupId,
	                     @RequestParam(required = false) String role,
	                     HttpSession session, RedirectAttributes ra) {
		if (!isAgency(session, role)) return deny(ra, groupId, role);
		service.softDelete(id);                                           // ★ delete → softDelete
		ra.addFlashAttribute("msg", "삭제되었습니다.");
		return redirect(groupId, role);
	}

	/** 첨부 1개 삭제 (소속사만) */
	@PostMapping("/media/{postId}/file/{fileId}/delete")
	public String deleteFile(@PathVariable Long postId, @PathVariable Long fileId,
	                         @RequestParam Long groupId,
	                         @RequestParam(required = false) String role,
	                         HttpSession session, RedirectAttributes ra) {
		if (!isAgency(session, role)) return deny(ra, groupId, role);
		service.deleteFile(postId, fileId);
		ra.addFlashAttribute("msg", "첨부를 삭제했습니다.");
		return redirect(groupId, role);
	}

	/** 저장된 파일 스트리밍 (공통) — 파일 id 기준 */
	@GetMapping("/media/file/{fileId}")
	public ResponseEntity<Resource> file(@PathVariable Long fileId) {
		BoardMediaFileEntity f = service.getFile(fileId);                 // ★ 파일 엔티티
		Resource resource = service.loadResource(f);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(f.getContentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"inline; filename=\"" + f.getOriginalName() + "\"")
				.body(resource);
	}

	// ── 역할/사용자 판단 (지금은 세션/파라미터, 추후 Spring Security로 교체) ──
	private boolean isAgency(HttpSession session, String roleParam) {
		if (roleParam != null) return "AGENCY".equalsIgnoreCase(roleParam); // 테스트용
		Object r = session.getAttribute("loginRole");
		return "AGENCY".equals(String.valueOf(r));
	}
	private Long currentUserId(HttpSession session, Long override) {
		if (override != null) return override;                              // 테스트용
		Object uid = session.getAttribute("loginUserId");
		if (uid == null) throw new IllegalStateException("로그인이 필요합니다.");
		return Long.valueOf(uid.toString());
	}
	private String redirect(Long groupId, String role) {
		String r = (role != null) ? "&role=" + role : "";
		return "redirect:/board/media?groupId=" + groupId + r;
	}
	private String deny(RedirectAttributes ra, Long groupId, String role) {
		ra.addFlashAttribute("error", "권한이 없습니다. (소속사만 가능)");
		return redirect(groupId, role);
	}
}
