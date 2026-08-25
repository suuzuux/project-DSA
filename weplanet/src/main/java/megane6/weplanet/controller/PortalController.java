package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.CommentReport;
import megane6.weplanet.domain.entity.Report;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.portal.ArtistProfile;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.media.BoardMediaService;
import megane6.weplanet.service.portal.ArtistBlockService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalController {

	private final UserRepository userRepository;
	private final PortalManagementService portalManagementService;
	private final BoardMediaService boardMediaService;
	private final ReportRepository reportRepository;
	private final CommentReportRepository commentReportRepository;
	private final ArtistBlockService artistBlockService;

	@GetMapping("/login")
	public String login(@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return "portal/login";
		}
		if (isArtist(principal)) {
			return "redirect:/portal/dashboard";
		}
		return "redirect:/";
	}

	@GetMapping("/dashboard")
	public String dashboard(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "dashboard");
		if (redirect != null) {
			return redirect;
		}
		User artist = currentArtist(principal);
		model.addAttribute("membershipCount", portalManagementService.countMemberships(artist));
		model.addAttribute("noticeCount", portalManagementService.countNotices(artist));
		model.addAttribute("scheduleCount", portalManagementService.countUpcomingSchedules(artist));
		model.addAttribute("mediaCount", portalManagementService.countMedia(artist));
		model.addAttribute("reportCount", portalManagementService.countPendingReports(artist));
		model.addAttribute("latestNotices", portalManagementService.getNotices(artist).stream().limit(5).toList());
		model.addAttribute("upcomingSchedules", portalManagementService.getSchedules(artist).stream().limit(5).toList());
		return "portal/dashboard";
	}

	@GetMapping("/notices")
	public String notices(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "notices");
		if (redirect != null) {
			return redirect;
		}
		User artist = currentArtist(principal);
		model.addAttribute("notices", portalManagementService.getNotices(artist));
		return "portal/notices";
	}

	@GetMapping("/notices/new")
	public String newNotice(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "notices");
		return redirect != null ? redirect : "portal/notice-form";
	}

	@GetMapping("/notices/{noticeId}/edit")
	public String editNotice(@PathVariable Long noticeId,
							 @AuthenticationPrincipal AuthenticatedUser principal,
							 Model model) {
		String redirect = prepareArtistPage(principal, model, "notices");
		if (redirect != null) {
			return redirect;
		}
		User artist = currentArtist(principal);
		model.addAttribute("notice", portalManagementService.getNotice(artist, noticeId));
		return "portal/notice-form";
	}

	@PostMapping("/notices")
	public String createNotice(@RequestParam String title,
							   @RequestParam String content,
							   @RequestParam(defaultValue = "false") boolean published,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			portalManagementService.saveNotice(artist, null, title, content, published);
			redirectAttributes.addFlashAttribute("msg", "공지가 등록되었습니다.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/portal/notices/new";
		}
		return "redirect:/portal/notices";
	}

	@PostMapping("/notices/{noticeId}")
	public String updateNotice(@PathVariable Long noticeId,
							   @RequestParam String title,
							   @RequestParam String content,
							   @RequestParam(defaultValue = "false") boolean published,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			portalManagementService.saveNotice(artist, noticeId, title, content, published);
			redirectAttributes.addFlashAttribute("msg", "공지가 수정되었습니다.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/portal/notices/" + noticeId + "/edit";
		}
		return "redirect:/portal/notices";
	}

	@PostMapping("/notices/{noticeId}/delete")
	public String deleteNotice(@PathVariable Long noticeId,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		portalManagementService.deleteNotice(artist, noticeId);
		redirectAttributes.addFlashAttribute("msg", "공지가 삭제되었습니다.");
		return "redirect:/portal/notices";
	}

	@GetMapping("/schedule")
	public String schedule(@RequestParam(required = false) String month,
						   @AuthenticationPrincipal AuthenticatedUser principal,
						   Model model) {
		String redirect = prepareArtistPage(principal, model, "schedule");
		if (redirect != null) {
			return redirect;
		}
		User artist = currentArtist(principal);
		YearMonth selectedMonth = parseMonth(month);
		model.addAttribute("selectedMonth", selectedMonth);
		model.addAttribute("prevMonth", selectedMonth.minusMonths(1));
		model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
		model.addAttribute("schedules", portalManagementService.getSchedulesInMonth(artist, selectedMonth));
		return "portal/schedule";
	}

	@PostMapping("/schedules")
	public String createSchedule(@RequestParam String title,
								 @RequestParam(required = false) String description,
								 @RequestParam(required = false) String location,
								 @RequestParam("scheduleAt") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime scheduleAt,
								 @AuthenticationPrincipal AuthenticatedUser principal,
								 RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			portalManagementService.createSchedule(artist, title, description, location, scheduleAt);
			redirectAttributes.addFlashAttribute("msg", "일정이 등록되었습니다.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/portal/schedule";
	}

	@PostMapping("/schedules/{scheduleId}/delete")
	public String deleteSchedule(@PathVariable Long scheduleId,
								 @AuthenticationPrincipal AuthenticatedUser principal,
								 RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		portalManagementService.deleteSchedule(artist, scheduleId);
		redirectAttributes.addFlashAttribute("msg", "일정이 삭제되었습니다.");
		return "redirect:/portal/schedule";
	}

	@GetMapping("/media")
	public String media(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "media");
		if (redirect != null) {
			return redirect;
		}
		User artist = currentArtist(principal);
		model.addAttribute("mediaList", boardMediaService.list(artist.getId()));
		return "portal/media";
	}

	@GetMapping("/media/new")
	public String newMedia(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "media");
		return redirect != null ? redirect : "portal/media-form";
	}

	@PostMapping("/media")
	public String createMedia(@RequestParam String title,
							  @RequestParam(required = false) String content,
							  @RequestParam(value = "files", required = false) List<MultipartFile> files,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			boardMediaService.create(artist.getId(), artist.getId(), title, content, files);
			redirectAttributes.addFlashAttribute("msg", "미디어가 등록되었습니다.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/portal/media/new";
		}
		return "redirect:/portal/media";
	}

	@PostMapping("/media/{mediaId}/delete")
	public String deleteMedia(@PathVariable Long mediaId,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		boardMediaService.softDelete(mediaId, artist.getId());
		redirectAttributes.addFlashAttribute("msg", "미디어가 삭제되었습니다.");
		return "redirect:/portal/media";
	}

	@GetMapping("/profile")
	public String profile(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "profile");
		return redirect != null ? redirect : "portal/profile";
	}

	@PostMapping("/profile")
	public String updateProfile(@RequestParam String nickname,
								@RequestParam String email,
								@RequestParam(required = false) String intro,
								@RequestParam(required = false) String headerImageUrl,
								@RequestParam(required = false) String logoImageUrl,
								@AuthenticationPrincipal AuthenticatedUser principal,
								RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			portalManagementService.updateProfile(artist, nickname, email, intro, headerImageUrl, logoImageUrl);
			redirectAttributes.addFlashAttribute("msg", "프로필이 저장되었습니다.");
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/portal/profile";
	}

	@GetMapping("/reports")
	public String reports(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "reports");
		if (redirect != null) {
			return redirect;
		}
		User artist = currentArtist(principal);
		model.addAttribute("postReports", reportRepository.findByPost_ArtistOrderByCreatedAtDesc(artist));
		model.addAttribute("commentReports", commentReportRepository.findByComment_Post_ArtistOrderByCreatedAtDesc(artist));
		model.addAttribute("blocks", artistBlockService.getBlocks(artist));
		return "portal/reports";
	}

	@PostMapping("/reports/post/{reportId}/block")
	public String blockPostAuthor(@PathVariable Long reportId,
								  @AuthenticationPrincipal AuthenticatedUser principal,
								  RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		Report report = reportRepository.findById(reportId)
				.filter(item -> item.getPost().getArtist() != null && item.getPost().getArtist().getId().equals(artist.getId()))
				.orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다."));
		artistBlockService.block(artist, report.getPost().getAuthor(), report.getReason().name());
		redirectAttributes.addFlashAttribute("msg", "해당 팬 계정을 차단했습니다.");
		return "redirect:/portal/reports";
	}

	@PostMapping("/reports/comment/{reportId}/block")
	public String blockCommentAuthor(@PathVariable Long reportId,
									 @AuthenticationPrincipal AuthenticatedUser principal,
									 RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		CommentReport report = commentReportRepository.findById(reportId)
				.filter(item -> item.getComment().getPost().getArtist() != null
						&& item.getComment().getPost().getArtist().getId().equals(artist.getId()))
				.orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없습니다."));
		artistBlockService.block(artist, report.getComment().getAuthor(), report.getReason().name());
		redirectAttributes.addFlashAttribute("msg", "해당 팬 계정을 차단했습니다.");
		return "redirect:/portal/reports";
	}

	@PostMapping("/blocks/{blockId}/delete")
	public String unblock(@PathVariable Long blockId,
						  @AuthenticationPrincipal AuthenticatedUser principal,
						  RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		artistBlockService.unblock(artist, blockId);
		redirectAttributes.addFlashAttribute("msg", "차단을 해제했습니다.");
		return "redirect:/portal/reports";
	}

	private String prepareArtistPage(AuthenticatedUser principal, Model model, String activeMenu) {
		if (principal == null) {
			return "redirect:/portal/login";
		}
		if (!isArtist(principal)) {
			return "redirect:/";
		}
		User artist = currentArtist(principal);
		if (artist == null) {
			return "redirect:/portal/login";
		}
		populateCommon(model, artist, activeMenu);
		return null;
	}

	private User currentArtist(AuthenticatedUser principal) {
		if (principal == null || !isArtist(principal)) {
			return null;
		}
		return userRepository.findById(principal.getId())
				.filter(user -> user.getRole() == Role.ARTIST)
				.orElse(null);
	}

	private String artistRedirect(AuthenticatedUser principal) {
		return principal == null ? "redirect:/portal/login" : "redirect:/";
	}

	private void populateCommon(Model model, User artist, String activeMenu) {
		ArtistProfile profile = portalManagementService.getOrCreateProfile(artist);
		model.addAttribute("artist", artist);
		model.addAttribute("artistProfile", profile);
		model.addAttribute("activeMenu", activeMenu);
	}

	private YearMonth parseMonth(String month) {
		if (month == null || month.isBlank()) {
			return YearMonth.now();
		}
		try {
			return YearMonth.parse(month);
		} catch (DateTimeParseException e) {
			return YearMonth.now();
		}
	}

	private boolean isArtist(AuthenticatedUser principal) {
		return "ROLE_ARTIST".equals(principal.getRoleName());
	}
}
