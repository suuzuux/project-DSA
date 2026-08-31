package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.CommentReport;
import megane6.weplanet.domain.entity.Report;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.calendar.ScheduleCategory;
import megane6.weplanet.domain.entity.portal.ArtistProfile;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.media.BoardMediaService;
import megane6.weplanet.service.portal.ArtistBlockService;
import megane6.weplanet.service.portal.PortalManagementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalController {

	private static final String SESSION_ARTIST = "portalArtistId";

	private final UserRepository userRepository;
	private final PortalManagementService portalManagementService;
	private final BoardMediaService boardMediaService;
	private final ReportRepository reportRepository;
	private final CommentReportRepository commentReportRepository;
	private final ArtistBlockService artistBlockService;
	private final megane6.weplanet.service.calendar.ArtistAttendanceService artistAttendanceService;
	private final CommunityJoinService communityJoinService;

	@GetMapping("/login")
	public String login(@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return "portal/login";
		}
		if (isPortalUser(principal)) {
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
		User actor = currentPortalUser(principal);
		if (actor != null && actor.getRole() == Role.ARTIST) {
			artistAttendanceService.recordVisitIfArtist(actor);
		}
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
						   @RequestParam(required = false) Long artistId,
						   @AuthenticationPrincipal AuthenticatedUser principal,
						   HttpSession session,
						   Model model) {
		String redirect = prepareArtistPage(principal, model, "schedule", artistId, session);
		if (redirect != null) {
			return redirect;
		}
		User artist = (User) model.getAttribute("artist");
		YearMonth selectedMonth = parseMonth(month);
		model.addAttribute("selectedMonth", selectedMonth);
		model.addAttribute("prevMonth", selectedMonth.minusMonths(1));
		model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
		model.addAttribute("schedules", portalManagementService.getSchedulesInMonth(artist, selectedMonth));
		model.addAttribute("calendarDays", portalManagementService.getMonthGrid(artist, selectedMonth));
		model.addAttribute("scheduleCategories", ScheduleCategory.values());
		model.addAttribute("currentMonth", YearMonth.now());
		return "portal/calendar/schedule";
	}

	@PostMapping("/schedules")
	public String createSchedule(@RequestParam String title,
								 @RequestParam(required = false) String description,
								 @RequestParam(required = false) String location,
								 @RequestParam(required = false) String ticketUrl,
								 @RequestParam(required = false) String category,
								 @RequestParam(required = false) Long artistId,
								 @RequestParam("scheduleAt") String scheduleAtRaw,
								 @AuthenticationPrincipal AuthenticatedUser principal,
								 HttpSession session,
								 RedirectAttributes redirectAttributes) {
		User actor = currentPortalUser(principal);
		if (actor == null) {
			return artistRedirect(principal);
		}
		User artist = resolveManagedArtist(actor, artistId, session);
		if (artist == null) {
			redirectAttributes.addFlashAttribute("error", "일정을 등록할 아티스트가 없습니다.");
			return "redirect:/portal/schedule";
		}
		try {
			LocalDateTime scheduleAt = parseScheduleAt(scheduleAtRaw);
			portalManagementService.createSchedule(
					artist,
					ScheduleCategory.from(category),
					title,
					description,
					location,
					ticketUrl,
					scheduleAt
			);
			redirectAttributes.addFlashAttribute("msg", "일정이 등록되었습니다.");
			return "redirect:/portal/schedule?month=" + YearMonth.from(scheduleAt) + "&artistId=" + artist.getId();
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", e.getMessage());
			return "redirect:/portal/schedule";
		}
	}

	@PostMapping("/schedules/{scheduleId}/delete")
	public String deleteSchedule(@PathVariable Long scheduleId,
								 @RequestParam(required = false) Long artistId,
								 @AuthenticationPrincipal AuthenticatedUser principal,
								 HttpSession session,
								 RedirectAttributes redirectAttributes) {
		User actor = currentPortalUser(principal);
		if (actor == null) {
			return artistRedirect(principal);
		}
		User artist = resolveManagedArtist(actor, artistId, session);
		if (artist == null) {
			return "redirect:/portal/schedule";
		}
		portalManagementService.deleteSchedule(artist, scheduleId);
		redirectAttributes.addFlashAttribute("msg", "일정이 삭제되었습니다.");
		return "redirect:/portal/schedule?artistId=" + artist.getId();
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
		List<Report> postReports = reportRepository.findByPost_ArtistOrderByCreatedAtDesc(artist);
		List<CommentReport> commentReports = commentReportRepository.findByComment_Post_ArtistOrderByCreatedAtDesc(artist);

		// [닉네임 관리] 신고 목록의 "팬 닉네임"은 커뮤니티 가입할 때의 닉네임과 연결한다.
		// (차단 목록의 닉네임은 ArtistBlock.blockedUser.nickname, 즉 회원가입할 때의 계정 닉네임을 그대로 쓰므로 변경하지 않음)
		List<User> reportedAuthors = new ArrayList<>();
		postReports.forEach(r -> reportedAuthors.add(r.getPost().getAuthor()));
		commentReports.forEach(r -> reportedAuthors.add(r.getComment().getAuthor()));

		model.addAttribute("postReports", postReports);
		model.addAttribute("commentReports", commentReports);
		model.addAttribute("authorNicknames", communityJoinService.displayNicknamesByAuthorId(reportedAuthors, artist.getId()));
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
		return prepareArtistPage(principal, model, activeMenu, null, null);
	}

	private String prepareArtistPage(AuthenticatedUser principal, Model model, String activeMenu, Long artistId, HttpSession session) {
		if (principal == null) {
			return "redirect:/portal/login";
		}
		if (!isPortalUser(principal)) {
			return "redirect:/";
		}
		User actor = currentPortalUser(principal);
		if (actor == null) {
			return "redirect:/portal/login";
		}
		HttpSession activeSession = session != null ? session : currentSession(true);
		User artist = resolveManagedArtist(actor, artistId, activeSession);
		if (artist == null) {
			artist = actor;
		}
		populateCommon(model, actor, artist, activeMenu);
		return null;
	}

	private User currentArtist(AuthenticatedUser principal) {
		User actor = currentPortalUser(principal);
		if (actor == null) {
			return null;
		}
		return resolveManagedArtist(actor, null, currentSession(false));
	}

	private HttpSession currentSession(boolean create) {
		ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		if (attrs == null) {
			return null;
		}
		return attrs.getRequest().getSession(create);
	}

	private User currentPortalUser(AuthenticatedUser principal) {
		if (principal == null || !isPortalUser(principal)) {
			return null;
		}
		return userRepository.findById(principal.getId())
				.filter(user -> user.getRole() == Role.ARTIST || user.getRole() == Role.AGENCY)
				.orElse(null);
	}

	private User resolveManagedArtist(User actor, Long artistId, HttpSession session) {
		if (actor == null) {
			return null;
		}
		if (actor.getRole() == Role.ARTIST) {
			return actor;
		}
		List<User> artists = userRepository.findByRole(Role.ARTIST);
		if (artists.isEmpty()) {
			return null;
		}
		Long selected = artistId;
		if (selected == null && session != null) {
			Object stored = session.getAttribute(SESSION_ARTIST);
			if (stored instanceof Long storedId) {
				selected = storedId;
			} else if (stored instanceof Number number) {
				selected = number.longValue();
			}
		}
		final Long selectedId = selected;
		User found = artists.stream()
				.filter(item -> selectedId != null && item.getId().equals(selectedId))
				.findFirst()
				.orElse(artists.get(0));
		if (session != null) {
			session.setAttribute(SESSION_ARTIST, found.getId());
		}
		return found;
	}

	private String artistRedirect(AuthenticatedUser principal) {
		return principal == null ? "redirect:/portal/login" : "redirect:/";
	}

	private void populateCommon(Model model, User actor, User artist, String activeMenu) {
		ArtistProfile profile = portalManagementService.getOrCreateProfile(artist);
		model.addAttribute("actor", actor);
		model.addAttribute("artist", artist);
		model.addAttribute("artistProfile", profile);
		model.addAttribute("activeMenu", activeMenu);
		model.addAttribute("isAgency", actor.getRole() == Role.AGENCY);
		model.addAttribute("managedArtists", actor.getRole() == Role.AGENCY
				? userRepository.findByRole(Role.ARTIST)
				: List.of(artist));
		model.addAttribute("scheduleCategories", ScheduleCategory.values());
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

	private LocalDateTime parseScheduleAt(String raw) {
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("일정 일시를 입력해주세요.");
		}
		String value = raw.trim();
		try {
			return LocalDateTime.parse(value);
		} catch (DateTimeParseException ignored) {
			try {
				return LocalDateTime.parse(value.length() == 16 ? value + ":00" : value);
			} catch (DateTimeParseException e) {
				throw new IllegalArgumentException("일정 일시 형식이 올바르지 않습니다.");
			}
		}
	}

	private boolean isPortalUser(AuthenticatedUser principal) {
		return "ROLE_ARTIST".equals(principal.getRoleName()) || "ROLE_AGENCY".equals(principal.getRoleName());
	}

	private boolean isArtist(AuthenticatedUser principal) {
		return "ROLE_ARTIST".equals(principal.getRoleName());
	}
}
