package megane6.weplanet.controller.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.dto.media.BoardMediaViewDTO;
import megane6.weplanet.domain.entity.media.BoardMediaEntity;
import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.media.BoardMediaService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/board")
public class BoardMediaController {

    private final BoardMediaService boardMediaService;

    // ── 목록 화면 : role=AGENCY 면 소속사 화면, 아니면 팬(읽기 전용) ──
    @GetMapping("/media")
    public String media(@RequestParam(defaultValue = "1") Long groupId,
                        @RequestParam(required = false) String role,
                        Model model) {
        List<BoardMediaViewDTO> list = boardMediaService.list(groupId);
        model.addAttribute("list", list);
        model.addAttribute("groupId", groupId);

        if ("AGENCY".equalsIgnoreCase(role)) {
            return "media/boardMediaViewForAgency";
        }
        return "media/boardMediaViewForFan";
    }

    // ── 업로드(글쓰기) ──
    @PostMapping("/media/upload")
    public String upload(@RequestParam Long groupId,
                         @RequestParam String title,
                         @RequestParam(required = false) String content,
                         @RequestParam(value = "files", required = false) List<MultipartFile> files,
                         @RequestParam(required = false) Long artistId,
                         @AuthenticationPrincipal AuthenticatedUser principal,
                         RedirectAttributes redirectAttributes) {
        try {
            requireCommunityOwner(principal, communityKey(artistId, groupId));
            boardMediaService.create(groupId, principal.getId(), title, content, files);
            redirectAttributes.addFlashAttribute("msg", "업로드되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterMutation(artistId, groupId);
    }

    // ── 수정 ──
    @PostMapping("/media/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam Long groupId,
                       @RequestParam String title,
                       @RequestParam(required = false) String content,
                       @RequestParam(value = "files", required = false) List<MultipartFile> files,
                       @RequestParam(required = false) Long artistId,
                       @AuthenticationPrincipal AuthenticatedUser principal,
                       RedirectAttributes redirectAttributes) {
        try {
            Long communityId = communityKey(artistId, groupId);
            requireCommunityOwner(principal, communityId);
            boardMediaService.edit(id, communityId, title, content, files);
            redirectAttributes.addFlashAttribute("msg", "수정되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterMutation(artistId, groupId);
    }

    // ── 삭제(소프트 삭제) ──
    @PostMapping("/media/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam Long groupId,
                         @RequestParam(required = false) Long artistId,
                         @AuthenticationPrincipal AuthenticatedUser principal,
                         RedirectAttributes redirectAttributes) {
        try {
            Long communityId = communityKey(artistId, groupId);
            requireCommunityOwner(principal, communityId);
            boardMediaService.softDelete(id, communityId);
            redirectAttributes.addFlashAttribute("msg", "삭제되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirectAfterMutation(artistId, groupId);
    }

    // ── 파일 스트리밍 : <img>, <video> 가 이 주소로 파일을 불러온다 ──
    @GetMapping("/media/file/{fileId}")
    public ResponseEntity<Resource> file(@PathVariable Long fileId) {
        BoardMediaFileEntity fileEntity = boardMediaService.getFile(fileId);
        Resource resource = boardMediaService.loadResource(fileEntity);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileEntity.getContentType()))
                .body(resource);
    }

    private Long communityKey(Long artistId, Long groupId) {
        return artistId != null ? artistId : groupId;
    }

    private String redirectAfterMutation(Long artistId, Long groupId) {
        if (artistId != null) {
            return "redirect:/community/" + artistId + "/media";
        }
        return "redirect:/board/media?groupId=" + groupId + "&role=AGENCY";
    }

    /**
     * 커뮤니티 미디어는 해당 커뮤니티 아티스트(본인) 또는 소속사만 관리 가능.
     * group_id / artistId 는 커뮤니티 아티스트 users.id 와 동일하게 쓰인다.
     */
    private void requireCommunityOwner(AuthenticatedUser principal, Long communityArtistId) {
        if (principal == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        boolean isAgency = "ROLE_AGENCY".equals(principal.getRoleName());
        boolean isOwner = principal.getId().equals(communityArtistId);
        if (!isAgency && !isOwner) {
            throw new IllegalStateException("이 커뮤니티의 아티스트만 미디어를 관리할 수 있습니다.");
        }
    }
}
