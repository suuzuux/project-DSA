package megane6.weplanet.controller.media;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.controller.AuthenticatedUserResolver;
import megane6.weplanet.domain.dto.media.BoardMediaViewDTO;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.media.BoardMediaFileEntity;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityArtistResolver;
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
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/board")
public class BoardMediaController {

    private final BoardMediaService boardMediaService;
    private final AuthenticatedUserResolver userResolver;
    // SETTINGS-03 커밋3: 커뮤니티 미디어 탭의 flash 문구 번역용
    private final megane6.weplanet.i18n.Messages messages;
    private final CommunityArtistResolver communityArtistResolver;

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
                         @RequestParam(defaultValue = "false") boolean membershipOnly,
                         @RequestParam(required = false) Long artistId,
                         @AuthenticationPrincipal AuthenticatedUser principal,
                         RedirectAttributes redirectAttributes) {
        try {
            requireCommunityOwner(principal, communityKey(artistId, groupId));
            boardMediaService.create(groupId, principal.getId(), title, content, files, membershipOnly);
            redirectAttributes.addFlashAttribute("msg", messages.get("community.media.uploaded"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", messages.resolve(e));
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
            redirectAttributes.addFlashAttribute("msg", messages.get("community.media.updated"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", messages.resolve(e));
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
            redirectAttributes.addFlashAttribute("msg", messages.get("community.media.deleted"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", messages.resolve(e));
        }
        return redirectAfterMutation(artistId, groupId);
    }

    // ── 좋아요 토글 (팬 게시글 /posts/detail/{id}/like 와 동일) ──
    @PostMapping("/media/{id}/like")
    @ResponseBody
    public Map<String, Object> like(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = userResolver.requireAuthenticated(principal);
        boolean liked = boardMediaService.toggleLike(id, user);
        return Map.of(
                "liked", liked,
                "likeCount", boardMediaService.getLikeCount(id)
        );
    }
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
            throw new IllegalStateException("common.error.loginRequired");
        }
        boolean isAgency = "ROLE_AGENCY".equals(principal.getRoleName());
        // 솔로 아티스트 본인 또는 그 그룹의 멤버
        boolean isOwner = communityArtistResolver.isArtistOf(userResolver.requireAuthenticated(principal), communityArtistId);
        if (!isAgency && !isOwner) {
            throw new IllegalStateException("error.media.ownerOnly");
        }
    }
}
