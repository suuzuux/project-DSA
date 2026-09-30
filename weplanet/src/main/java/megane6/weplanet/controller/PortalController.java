package megane6.weplanet.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.CommentReport;
import megane6.weplanet.domain.entity.GroupMember;
import megane6.weplanet.domain.entity.Report;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GoodsCategoryType;
import megane6.weplanet.domain.entity.enumfolder.GoodsShopCategory;
import megane6.weplanet.domain.entity.enumfolder.GoodsStatus;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.calendar.ScheduleCategory;
import megane6.weplanet.domain.entity.live.LiveCommentReport;
import megane6.weplanet.repository.AgencyProfileRepository;
import megane6.weplanet.repository.CommentReportRepository;
import megane6.weplanet.repository.ReportRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.repository.live.LiveCommentReportRepository;
import megane6.weplanet.security.AuthenticatedUser;
import megane6.weplanet.security.RoleHomeRedirects;
import megane6.weplanet.service.ArtistMemberService;
import megane6.weplanet.service.CommentService;
import megane6.weplanet.service.PostService;
import megane6.weplanet.service.community.CommunityArtistResolver;
import megane6.weplanet.service.community.CommunityJoinService;
import megane6.weplanet.service.email.ArtistInvitationMailService;
import megane6.weplanet.service.live.LiveBroadcastService;
import megane6.weplanet.service.media.BoardMediaService;
import megane6.weplanet.service.portal.AgencyEnrollmentService;
import megane6.weplanet.service.portal.ArtistBlockService;
import megane6.weplanet.service.portal.ArtistRegistrationService;
import megane6.weplanet.service.portal.PortalManagementService;
import megane6.weplanet.service.shop.GoodsCategoryOptionsPayload;
import megane6.weplanet.service.shop.GoodsService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
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
	private final LiveCommentReportRepository liveCommentReportRepository;
	private final ArtistBlockService artistBlockService;
	private final CommunityJoinService communityJoinService;
	private final AgencyProfileRepository agencyProfileRepository;
	private final AgencyEnrollmentService agencyEnrollmentService;
	private final PostService postService;
	private final CommentService commentService;
	private final LiveBroadcastService liveBroadcastService;
	private final GoodsService goodsService;
	private final MessageSource messageSource;
	// SETTINGS-03 커밋3: 서비스 예외가 메시지 키로 바뀌어서, 화면에 내보낼 때 현재 로케일 문구로 해석한다
	private final megane6.weplanet.i18n.Messages messages;
	private final ArtistRegistrationService artistRegistrationService;
	private final ArtistInvitationMailService artistInvitationMailService;
	private final ArtistMemberService artistMemberService;
	private final CommunityArtistResolver communityArtistResolver;

	// SETTINGS-03: 화면 언어에 맞춘 에러 메시지를 뽑아오는 공통 헬퍼
	private String msg(String code) {
		return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
	}

	@GetMapping("/login")
	public String login(@AuthenticationPrincipal AuthenticatedUser principal) {
		if (principal == null) {
			return "portal/login";
		}
		if (isPortalUser(principal) && !hasApprovedAgencyPermission(
				principal.getId())
		) {
			return "portal/approval-pending";
		}
		
		return RoleHomeRedirects.redirectFor(principal);
	}

	@GetMapping("/select-artist")
	public String selectArtist(@RequestParam Long artistId,
							   @RequestParam(defaultValue = "dashboard") String next,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   HttpSession session) {
		User actor = currentPortalUser(principal);
		if (actor == null) {
			return artistRedirect(principal);
		}
		User artist = resolveManagedArtist(actor, artistId, session);
		if (artist == null) {
			return "redirect:/portal/dashboard";
		}
		return switch (next) {
			case "notices" -> "redirect:/portal/notices";
			case "schedule" -> "redirect:/portal/schedule";
			case "media" -> "redirect:/portal/media";
			case "goods" -> "redirect:/portal/goods";
			case "live" -> "redirect:/portal/live";
			case "profile" -> "redirect:/portal/profile";
			case "reports" -> "redirect:/portal/reports";
			case "members" -> "redirect:/portal/members";
			default -> "redirect:/portal/dashboard";
		};
	}

	@GetMapping("/dashboard")
	public String dashboard(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "dashboard");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		if (artist == null) {
			return "portal/dashboard";
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
	
	// 아티스트(그룹/솔로) 등록 화면. 특정 아티스트가 아니라 소속사 단위 메뉴라서 선택 여부와 상관없이 폼을 보여준다.
	@GetMapping("/artists/new")
	public String artistRegisterForm(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "artistNew");
		if (redirect != null) {
			return redirect;
		}
		return "portal/artist-form";
	}
	
	@PostMapping("/artists")
	public String registerArtist(@AuthenticationPrincipal AuthenticatedUser principal,
								 @ModelAttribute ArtistRegistrationService.RegisterCommand command,
								 HttpSession session,
								 RedirectAttributes redirectAttributes) {
		User actor = currentPortalUser(principal);
		if (actor == null) {
			return artistRedirect(principal);
		}
		
		ArtistRegistrationService.RegisteredArtist registered;
		try {
			registered = artistRegistrationService.register(actor, command);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// 입력값을 돌려줘서 다시 처음부터 치지 않게 한다
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			redirectAttributes.addFlashAttribute("form", command);
			return "redirect:/portal/artists/new";
		}
		
		// 메일은 등록 트랜잭션이 커밋된 뒤에 보낸다. 실패해도 계정은 남아 있어 재발송(7단계)으로 복구 가능
		try {
			artistInvitationMailService.sendActivationMail(
					registered.username(),
					registered.groupName(),
					registered.agencyName(),
					registered.activation()
			);
			redirectAttributes.addFlashAttribute("msg",
					messages.get("portalArtistForm.flash.registered", registered.groupName(), registered.username()));
		} catch (Exception mailException) {
			log.warn("아티스트 활성화 메일 발송 실패: artistId={}", registered.artistId(), mailException);
			redirectAttributes.addFlashAttribute("msg",
					messages.get("portalArtistForm.flash.registeredMailFailed", registered.groupName()));
		}
		
		// 방금 만든 아티스트를 선택된 상태로 대시보드에 보낸다
		resolveManagedArtist(actor, registered.artistId(), session);
		return "redirect:/portal/dashboard";
	}
	
	// 선택한 아티스트(그룹)의 멤버 관리 화면
	@GetMapping("/members")
	public String members(@AuthenticationPrincipal AuthenticatedUser principal,
						  Model model) {
		String redirect = prepareArtistPage(principal, model, "members");
		if (redirect != null) {
			return redirect;
		}
		
		User artist = artistFromModel(model);
		if (artist != null) {
			model.addAttribute("members", artistMemberService.activeMembers(artist.getId()));
		}
		
		return "portal/members";
	}
	
	@PostMapping("/members")
	public String addMember(@AuthenticationPrincipal AuthenticatedUser principal,
							@RequestParam String memberName,
							@RequestParam(required = false) Long artistId,
							RedirectAttributes redirectAttributes) {
		User actor = currentPortalUser(principal);
		User artist = currentArtist(principal);
		
		if (actor == null || artist == null) {
			return artistRedirect(principal);
		}
		
		// AUTH-11: 폼이 보내 준 그룹(artistId)에 추가한다. 예전에는 세션의 "현재 선택 아티스트"를 썼기 때문에,
		// 탭 A 에서 그룹 X 화면을 띄워 둔 채 탭 B 에서 그룹 Y 를 고르면 탭 A 에서 추가한 멤버가 그룹 Y 에 생겼다.
		// 이 그룹을 실제로 관리하는지는 ArtistMemberService.addMember(requireManagedGroup)가 다시 확인한다.
		Long targetGroupId = artistId != null ? artistId : artist.getId();
		
		try {
			GroupMember added = artistMemberService.addMember(actor, targetGroupId, memberName);
			redirectAttributes.addFlashAttribute("msg",
					messages.get("portalMembers.flash.added", added.getMember().getNickname()));
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
		}
		
		return "redirect:/portal/members";
	}
	
	@PostMapping("/members/{memberId}/leave")
	public String removeMember(@AuthenticationPrincipal AuthenticatedUser principal,
							   @PathVariable Long memberId,
							   RedirectAttributes redirectAttributes) {
		User actor = currentPortalUser(principal);
		User artist = currentArtist(principal);
		if (actor == null || artist == null) {
			return artistRedirect(principal);
		}
		try {
			String name = artistMemberService.removeMember(actor, artist.getId(), memberId);
			redirectAttributes.addFlashAttribute("msg", messages.get("portalMembers.flash.removed", name));
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
		}
		return "redirect:/portal/members";
	}
	
	@PostMapping("/members/{memberId}/reset-password")
	public String resetMemberPassword(@AuthenticationPrincipal AuthenticatedUser principal,
									  @PathVariable Long memberId,
									  RedirectAttributes redirectAttributes) {
		User actor = currentPortalUser(principal);
		User artist = currentArtist(principal);
		if (actor == null || artist == null) {
			return artistRedirect(principal);
		}
		try {
			String name = artistMemberService.resetMemberPassword(actor, artist.getId(), memberId);
			redirectAttributes.addFlashAttribute("msg",
					messages.get("portalMembers.flash.passwordReset", name));
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
		}
		return "redirect:/portal/members";
	}
	
	// 선택된 아티스트(그룹)의 계정 활성화 메일 재발송
	@PostMapping("/artists/resend-activation")
	public String resendArtistActivation(@AuthenticationPrincipal AuthenticatedUser principal,
										 RedirectAttributes redirectAttributes) {
		User actor = currentPortalUser(principal);
		User artist = currentArtist(principal);
		if (actor == null || artist == null) {
			return artistRedirect(principal);
		}
		
		ArtistRegistrationService.RegisteredArtist reissued;
		try {
			reissued = artistRegistrationService.reissueActivation(actor, artist.getId());
		} catch (IllegalArgumentException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/portal/members";
		}
		
		// 토큰 재발급 트랜잭션이 커밋된 뒤에 메일을 보낸다 (등록 때와 같은 이유)
		try {
			artistInvitationMailService.sendActivationMail(
					reissued.username(),
					reissued.groupName(),
					reissued.agencyName(),
					reissued.activation()
			);
			redirectAttributes.addFlashAttribute("msg",
					messages.get("portalMembers.flash.activationResent", reissued.username()));
		} catch (Exception mailException) {
			log.warn("아티스트 활성화 메일 재발송 실패: artistId={}", reissued.artistId(), mailException);
			redirectAttributes.addFlashAttribute("error", messages.get("portalMembers.error.activationMailFailed"));
		}
		return "redirect:/portal/members";
	}

	@GetMapping("/live")
	public String live(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		if (principal == null) {
			return "redirect:/portal/login";
		}
		// 아티스트(솔로 본인/그룹 멤버)는 "내 커뮤니티" 방송 페이지만 진입 가능 (커뮤니티 Live 탭의 '방송하기'에서 이동)
		if ("ROLE_ARTIST".equals(principal.getRoleName()) || "ROLE_ARTIST_MEMBER".equals(principal.getRoleName())) {
			User me = userRepository.findById(principal.getId()).orElse(null);
			Long ownCommunityId = communityArtistResolver.ownCommunityId(me);
			User artist = ownCommunityId == null ? null : userRepository.findById(ownCommunityId)
					.filter(user -> user.getRole() == Role.ARTIST)
					.orElse(null);
			if (artist == null) {
				return "redirect:/portal/login";
			}
			populateCommon(model, me, artist, "live");
			model.addAttribute("isAgency", false);
			model.addAttribute("managedArtists", List.of());
			model.addAttribute("liveStatus", liveBroadcastService.status(artist.getId()));
			return "portal/live";
		}
		String redirect = prepareArtistPage(principal, model, "live");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		if (artist == null) {
			return "portal/live";
		}
		model.addAttribute("liveStatus", liveBroadcastService.status(artist.getId()));
		return "portal/live";
	}

	@GetMapping("/notices")
	public String notices(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "notices");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		model.addAttribute("notices", artist != null
				? portalManagementService.getNotices(artist)
				: List.of());
		return "portal/notices";
	}

	@GetMapping("/notices/new")
	public String newNotice(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "notices");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		if (artist == null) {
			return "redirect:/portal/dashboard";
		}
		model.addAttribute("pinnedCount", portalManagementService.countPinned(artist));
		model.addAttribute("maxPinned", PortalManagementService.MAX_PINNED);
		return "portal/notice-form";
	}

	@GetMapping("/notices/{noticeId:\\d+}/edit")
	public String editNotice(@PathVariable Long noticeId,
							 @AuthenticationPrincipal AuthenticatedUser principal,
							 Model model) {
		String redirect = prepareArtistPage(principal, model, "notices");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		if (artist == null) {
			return "redirect:/portal/dashboard";
		}
		model.addAttribute("notice", portalManagementService.getNotice(artist, noticeId));
		model.addAttribute("pinnedCount", portalManagementService.countPinned(artist));
		model.addAttribute("maxPinned", PortalManagementService.MAX_PINNED);
		return "portal/notice-form";
	}

	@PostMapping("/notices")
	public String createNotice(@RequestParam String title,
							   @RequestParam String content,
							   @RequestParam(defaultValue = "false") boolean published,
							   @RequestParam(defaultValue = "false") boolean pinned,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			portalManagementService.saveNotice(artist, null, title, content, published, pinned);
			redirectAttributes.addFlashAttribute("msg", msg("noticeForm.msg.created"));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/portal/notices/new";
		}
		return "redirect:/portal/notices";
	}

	@PostMapping("/notices/reorder")
	@ResponseBody
	public Map<String, Object> reorderNotices(@RequestParam List<Long> ids,
											  @AuthenticationPrincipal AuthenticatedUser principal) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return Map.of("ok", false, "message", msg("common.error.loginRequired"));
		}
		try {
			portalManagementService.reorderPinned(artist, ids);
			return Map.of("ok", true);
		} catch (IllegalArgumentException e) {
			return Map.of("ok", false, "message", messages.resolve(e));
		}
	}

	@PostMapping("/notices/{noticeId:\\d+}")
	public String updateNotice(@PathVariable Long noticeId,
							   @RequestParam String title,
							   @RequestParam String content,
							   @RequestParam(defaultValue = "false") boolean published,
							   @RequestParam(defaultValue = "false") boolean pinned,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			portalManagementService.saveNotice(artist, noticeId, title, content, published, pinned);
			redirectAttributes.addFlashAttribute("msg", msg("noticeForm.msg.updated"));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/portal/notices/" + noticeId + "/edit";
		}
		return "redirect:/portal/notices";
	}

	@PostMapping("/notices/{noticeId:\\d+}/delete")
	public String deleteNotice(@PathVariable Long noticeId,
							   @AuthenticationPrincipal AuthenticatedUser principal,
							   RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		portalManagementService.deleteNotice(artist, noticeId);
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.noticeDeleted"));
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
		User artist = artistFromModel(model);
		YearMonth selectedMonth = parseMonth(month);
		model.addAttribute("selectedMonth", selectedMonth);
		model.addAttribute("prevMonth", selectedMonth.minusMonths(1));
		model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
		model.addAttribute("scheduleCategories", ScheduleCategory.values());
		model.addAttribute("currentMonth", YearMonth.now());
		model.addAttribute("calendarDays", artist != null
				? portalManagementService.getMonthGrid(artist, selectedMonth)
				: List.of());
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
			redirectAttributes.addFlashAttribute("error", msg("portal.error.noArtistForSchedule"));
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
			redirectAttributes.addFlashAttribute("msg", msg("portal.msg.scheduleCreated"));
			return "redirect:/portal/schedule?month=" + YearMonth.from(scheduleAt) + "&artistId=" + artist.getId();
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/portal/schedule";
		}
	}

	@PostMapping("/schedules/{scheduleId:\\d+}/reschedule")
	@ResponseBody
	public Map<String, Object> rescheduleSchedule(@PathVariable Long scheduleId,
												  @RequestParam String date,
												  @RequestParam(required = false) Long artistId,
												  @AuthenticationPrincipal AuthenticatedUser principal,
												  HttpSession session) {
		User actor = currentPortalUser(principal);
		if (actor == null) {
			return Map.of("ok", false, "message", msg("common.error.loginRequired"));
		}
		User artist = resolveManagedArtist(actor, artistId, session);
		if (artist == null) {
			return Map.of("ok", false, "message", msg("portal.error.noArtistForMove"));
		}
		try {
			LocalDate targetDate = LocalDate.parse(date.trim());
			portalManagementService.rescheduleSchedule(artist, scheduleId, targetDate);
			return Map.of("ok", true);
		} catch (Exception e) {
			if (e instanceof IllegalArgumentException ex) {
				return Map.of("ok", false, "message", messages.resolve(ex));
			}
			return Map.of("ok", false, "message", msg("schedule.rescheduleFailed"));
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
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.scheduleDeleted"));
		return "redirect:/portal/schedule?artistId=" + artist.getId();
	}

	@GetMapping("/media")
	public String media(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "media");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		model.addAttribute("mediaList", artist != null
				? boardMediaService.list(artist.getId())
				: List.of());
		return "portal/media";
	}

	@GetMapping("/media/new")
	public String newMedia(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "media");
		if (redirect != null) {
			return redirect;
		}
		if (artistFromModel(model) == null) {
			return "redirect:/portal/dashboard";
		}
		return "portal/media-form";
	}

	@PostMapping("/media")
	public String createMedia(@RequestParam String title,
							  @RequestParam(required = false) String content,
							  @RequestParam(value = "files", required = false) List<MultipartFile> files,
							  @RequestParam(defaultValue = "false") boolean membershipOnly,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			boardMediaService.create(artist.getId(), artist.getId(), title, content, files, membershipOnly);
			redirectAttributes.addFlashAttribute("msg", msg("mediaForm.msg.created"));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
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
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.mediaDeleted"));
		return "redirect:/portal/media";
	}

	@GetMapping("/goods")
	public String goods(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "goods");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		model.addAttribute("goodsList", artist != null ? goodsService.listForArtist(artist) : List.of());
		return "portal/goods";
	}

	@GetMapping("/goods/new")
	public String newGoods(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "goods");
		if (redirect != null) {
			return redirect;
		}
		if (artistFromModel(model) == null) {
			return "redirect:/portal/dashboard";
		}
		model.addAttribute("goods", null);
		model.addAttribute("goodsStatuses", GoodsStatus.values());
		model.addAttribute("goodsCategoryTypes", GoodsCategoryType.values());
		model.addAttribute("goodsShopCategories", GoodsShopCategory.values());
		model.addAttribute("categoryOptionsJson", "{}");
		return "portal/goods-form";
	}

	@GetMapping("/goods/{goodsId}/edit")
	public String editGoods(@PathVariable Long goodsId,
							@AuthenticationPrincipal AuthenticatedUser principal,
							Model model,
							RedirectAttributes redirectAttributes) {
		String redirect = prepareArtistPage(principal, model, "goods");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		if (artist == null) {
			return "redirect:/portal/dashboard";
		}
		try {
			var goods = goodsService.getOwned(artist, goodsId);
			model.addAttribute("goods", goods);
			model.addAttribute("goodsStatuses", GoodsStatus.values());
			model.addAttribute("goodsCategoryTypes", GoodsCategoryType.values());
			model.addAttribute("goodsShopCategories", GoodsShopCategory.values());
			model.addAttribute("categoryOptionsJson", goodsService.categoryOptionsPayload(goods).toJson());
			return "portal/goods-form";
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/portal/goods";
		}
	}

	@PostMapping("/goods")
	public String createGoods(@RequestParam String name,
							  @RequestParam(required = false) String description,
							  @RequestParam int price,
							  @RequestParam(required = false) String officialUrl,
							  @RequestParam(defaultValue = "ON_SALE") GoodsStatus status,
							  @RequestParam(defaultValue = "MD") GoodsShopCategory shopCategory,
							  @RequestParam("thumbnail") MultipartFile thumbnail,
							  @RequestParam(name = "categoryOptionsJson", defaultValue = "{}") String categoryOptionsJson,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			if (price < 0) {
				throw new IllegalArgumentException(msg("goodsForm.error.invalidPrice"));
			}
			GoodsCategoryOptionsPayload categoryOptions = GoodsCategoryOptionsPayload.parse(categoryOptionsJson);
			goodsService.create(artist, name, description, price, officialUrl, status,
					shopCategory, thumbnail, categoryOptions);
			redirectAttributes.addFlashAttribute("msg", msg("goodsForm.msg.created"));
			return "redirect:/portal/goods";
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/portal/goods/new";
		}
	}

	@PostMapping("/goods/{goodsId}")
	public String updateGoods(@PathVariable Long goodsId,
							  @RequestParam String name,
							  @RequestParam(required = false) String description,
							  @RequestParam int price,
							  @RequestParam(required = false) String officialUrl,
							  @RequestParam(defaultValue = "ON_SALE") GoodsStatus status,
							  @RequestParam(defaultValue = "MD") GoodsShopCategory shopCategory,
							  @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
							  @RequestParam(name = "categoryOptionsJson", defaultValue = "{}") String categoryOptionsJson,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			if (price < 0) {
				throw new IllegalArgumentException(msg("goodsForm.error.invalidPrice"));
			}
			GoodsCategoryOptionsPayload categoryOptions = GoodsCategoryOptionsPayload.parse(categoryOptionsJson);
			goodsService.update(artist, goodsId, name, description, price, officialUrl, status,
					shopCategory, thumbnail, categoryOptions);
			redirectAttributes.addFlashAttribute("msg", msg("goodsForm.msg.updated"));
			return "redirect:/portal/goods";
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
			return "redirect:/portal/goods/" + goodsId + "/edit";
		}
	}

	@PostMapping("/goods/{goodsId}/delete")
	public String deleteGoods(@PathVariable Long goodsId,
							  @AuthenticationPrincipal AuthenticatedUser principal,
							  RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			goodsService.softDelete(artist, goodsId);
			redirectAttributes.addFlashAttribute("msg", msg("portal.msg.goodsDeleted"));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
		}
		return "redirect:/portal/goods";
	}

	@PostMapping("/goods/reorder")
	@ResponseBody
	public Map<String, Object> reorderGoods(@RequestParam("orderedIds") List<Long> orderedIds,
											@AuthenticationPrincipal AuthenticatedUser principal) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return Map.of("ok", false, "message", msg("goodsForm.error.artistRequired"));
		}
		try {
			goodsService.reorder(artist, orderedIds);
			return Map.of("ok", true);
		} catch (IllegalArgumentException e) {
			return Map.of("ok", false, "message", messages.resolve(e));
		}
	}

	@PostMapping("/goods/editor-image")
	@ResponseBody
	public Map<String, String> goodsEditorImage(@RequestParam("image") MultipartFile image,
												@AuthenticationPrincipal AuthenticatedUser principal) {
		if (currentArtist(principal) == null) {
			return Map.of("message", msg("goodsForm.error.noPermission"));
		}
		String stored = goodsService.storeEditorImage(image);
		return Map.of("url", "/uploads/" + stored);
	}

	@GetMapping("/profile")
	public String profile(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "profile");
		if (redirect != null) {
			return redirect;
		}
		return "portal/profile";
	}

	@PostMapping("/profile")
	public String updateProfile(@RequestParam String nickname,
								@RequestParam String email,
								@RequestParam(required = false) String realName,
								@RequestParam(required = false) String gender,
								@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate,
								@RequestParam(required = false) String intro,
								@RequestParam(value = "avatar", required = false) MultipartFile avatar,
								@RequestParam(value = "background", required = false) MultipartFile background,
								@RequestParam(defaultValue = "false") boolean removeAvatar,
								@RequestParam(defaultValue = "false") boolean removeBackground,
								@AuthenticationPrincipal AuthenticatedUser principal,
								RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		try {
			portalManagementService.updateProfile(
					artist, nickname, email, realName, gender, birthDate, intro,
					avatar, background, removeAvatar, removeBackground);
			redirectAttributes.addFlashAttribute("msg", msg("portalProfile.msg.saved"));
		} catch (IllegalArgumentException e) {
			redirectAttributes.addFlashAttribute("error", messages.resolve(e));
		}
		return "redirect:/portal/profile";
	}

	@GetMapping("/reports")
	public String reports(@AuthenticationPrincipal AuthenticatedUser principal, Model model) {
		String redirect = prepareArtistPage(principal, model, "reports");
		if (redirect != null) {
			return redirect;
		}
		User artist = artistFromModel(model);
		if (artist == null) {
			model.addAttribute("postReports", List.of());
			model.addAttribute("commentReports", List.of());
			model.addAttribute("liveCommentReports", List.of());
			model.addAttribute("authorNicknames", Map.of());
			model.addAttribute("blocks", List.of());
			return "portal/reports";
		}
		List<Report> postReports = reportRepository.findByPost_ArtistOrderByCreatedAtDesc(artist);
		List<CommentReport> commentReports = commentReportRepository.findByComment_Post_ArtistOrderByCreatedAtDesc(artist);
		List<LiveCommentReport> liveCommentReports = liveCommentReportRepository.findByArtistOrderByCreatedAtDesc(artist);

		// [닉네임 관리] 신고 목록의 "팬 닉네임"은 커뮤니티 가입할 때의 닉네임과 연결한다.
		// (차단 목록의 닉네임은 ArtistBlock.blockedUser.nickname, 즉 회원가입할 때의 계정 닉네임을 그대로 쓰므로 변경하지 않음)
		List<User> reportedAuthors = new ArrayList<>();
		postReports.forEach(r -> reportedAuthors.add(r.getPost().getAuthor()));
		commentReports.forEach(r -> reportedAuthors.add(r.getComment().getAuthor()));
		liveCommentReports.forEach(r -> reportedAuthors.add(r.getComment().getAuthor()));

		model.addAttribute("postReports", postReports);
		model.addAttribute("commentReports", commentReports);
		model.addAttribute("liveCommentReports", liveCommentReports);
		model.addAttribute("authorNicknames", communityJoinService.displayNicknamesByAuthorIdKey(reportedAuthors, artist.getId()));
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
				.orElseThrow(() -> new IllegalArgumentException("error.report.notFound"));
		artistBlockService.block(artist, report.getPost().getAuthor(), report.getReason().name());
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.fanBlocked"));
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
				.orElseThrow(() -> new IllegalArgumentException("error.report.notFound"));
		artistBlockService.block(artist, report.getComment().getAuthor(), report.getReason().name());
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.fanBlocked"));
		return "redirect:/portal/reports";
	}

	@PostMapping("/reports/live-comment/{reportId}/block")
	public String blockLiveCommentAuthor(@PathVariable Long reportId,
										 @AuthenticationPrincipal AuthenticatedUser principal,
										 RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		LiveCommentReport report = liveCommentReportRepository.findById(reportId)
				.filter(item -> item.getComment().getSession().getArtist() != null
						&& item.getComment().getSession().getArtist().getId().equals(artist.getId()))
				.orElseThrow(() -> new IllegalArgumentException("error.report.notFound"));
		artistBlockService.block(artist, report.getComment().getAuthor(), report.getReason().name());
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.fanBlocked"));
		return "redirect:/portal/reports";
	}

	@PostMapping("/reports/post/{reportId}/delete")
	public String deleteReportedPost(@PathVariable Long reportId,
									 @AuthenticationPrincipal AuthenticatedUser principal,
									 RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		Report report = reportRepository.findById(reportId)
				.filter(item -> item.getPost().getArtist() != null && item.getPost().getArtist().getId().equals(artist.getId()))
				.orElseThrow(() -> new IllegalArgumentException("error.report.notFound"));
		postService.deletePostForArtistCommunity(report.getPost(), artist);
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.reportedPostDeleted"));
		return "redirect:/portal/reports";
	}

	@PostMapping("/reports/comment/{reportId}/delete")
	public String deleteReportedComment(@PathVariable Long reportId,
										@AuthenticationPrincipal AuthenticatedUser principal,
										RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		CommentReport report = commentReportRepository.findById(reportId)
				.filter(item -> item.getComment().getPost().getArtist() != null
						&& item.getComment().getPost().getArtist().getId().equals(artist.getId()))
				.orElseThrow(() -> new IllegalArgumentException("error.report.notFound"));
		commentService.deleteCommentForArtistCommunity(report.getComment().getId(), artist);
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.reportedCommentDeleted"));
		return "redirect:/portal/reports";
	}

	@PostMapping("/reports/live-comment/{reportId}/delete")
	public String deleteReportedLiveComment(@PathVariable Long reportId,
											@AuthenticationPrincipal AuthenticatedUser principal,
											RedirectAttributes redirectAttributes) {
		User artist = currentArtist(principal);
		if (artist == null) {
			return artistRedirect(principal);
		}
		LiveCommentReport report = liveCommentReportRepository.findById(reportId)
				.filter(item -> item.getComment().getSession().getArtist() != null
						&& item.getComment().getSession().getArtist().getId().equals(artist.getId()))
				.orElseThrow(() -> new IllegalArgumentException("error.report.notFound"));
		liveBroadcastService.deleteCommentForArtistCommunity(report.getComment().getId(), artist);
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.reportedChatDeleted"));
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
		redirectAttributes.addFlashAttribute("msg", msg("portal.msg.unblocked"));
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

	private User artistFromModel(Model model) {
		Object artist = model.getAttribute("artist");
		return artist instanceof User user ? user : null;
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

		User actor = userRepository
				.findOneById(principal.getId())
				.filter(user -> user.getRole() == Role.AGENCY)
				.orElse(null);
		
		if (actor == null) {
			return null;
		}
		
		if (!hasApprovedAgencyPermission(actor.getId())) {
			return null;
		}
		
		return actor;
	}
	
	private boolean hasApprovedAgencyPermission(Long userId) {
		return agencyProfileRepository
				.findByUser_Id(userId)
				.map(profile -> profile.isApproved())
				.orElse(false);
	}

	/** 에이전시 계정에 연결된 소속사 소속 아티스트만 반환. */
	private List<User> managedArtistsOf(User actor) {
		if (actor == null || actor.getRole() != Role.AGENCY) {
			return List.of();
		}
		Long agencyId = actor.agencyId();
		if (agencyId == null) {
			return List.of();
		}
		return userRepository.findByRoleAndAgency_Id(Role.ARTIST, agencyId);
	}

	private User resolveManagedArtist(User actor, Long artistId, HttpSession session) {
		if (actor == null) {
			return null;
		}
		List<User> artists = managedArtistsOf(actor);
		if (artists.isEmpty()) {
			if (session != null) {
				session.removeAttribute(SESSION_ARTIST);
			}
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
		User found = selectedId == null ? null : artists.stream()
				.filter(item -> item.getId().equals(selectedId))
				.findFirst()
				.orElse(null);
		if (found == null) {
			found = artists.get(0);
		}
		if (session != null) {
			session.setAttribute(SESSION_ARTIST, found.getId());
		}
		return found;
	}

	private String artistRedirect(AuthenticatedUser principal) {
		return principal == null ? "redirect:/portal/login" : "redirect:/";
	}

	private void populateCommon(Model model, User actor, User artist, String activeMenu) {
		if (actor != null && actor.getRole() == Role.AGENCY) {
			agencyEnrollmentService.enrollManagedArtists(actor);
		}
		List<User> managedArtists = managedArtistsOf(actor);
		model.addAttribute("actor", actor);
		model.addAttribute("artist", artist);
		model.addAttribute("artistSelected", artist != null);
		model.addAttribute("activeMenu", activeMenu);
		model.addAttribute("isAgency", true);
		model.addAttribute("managedArtists", managedArtists);
		model.addAttribute("scheduleCategories", ScheduleCategory.values());
		if (artist != null) {
			var profile = portalManagementService.getOrCreateProfile(artist);
			model.addAttribute("artistProfile", profile);
			model.addAttribute("avatarPublicUrl", portalManagementService.toPublicImageUrl(profile.getLogoImageUrl()));
			model.addAttribute("backgroundPublicUrl", portalManagementService.toPublicImageUrl(profile.getHeaderImageUrl()));
			model.addAttribute("hasAvatarImage", profile.getLogoImageUrl() != null && !profile.getLogoImageUrl().isBlank());
			model.addAttribute("hasBackgroundImage", profile.getHeaderImageUrl() != null && !profile.getHeaderImageUrl().isBlank());
		} else {
			model.addAttribute("artistProfile", null);
			model.addAttribute("avatarPublicUrl", null);
			model.addAttribute("backgroundPublicUrl", null);
			model.addAttribute("hasAvatarImage", false);
			model.addAttribute("hasBackgroundImage", false);
		}
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
			throw new IllegalArgumentException("error.schedule.dateTimeRequired");
		}
		String value = raw.trim();
		if (value.length() == 10) {
			return LocalDate.parse(value).atStartOfDay();
		}
		try {
			return LocalDateTime.parse(value);
		} catch (DateTimeParseException ignored) {
			try {
				return LocalDateTime.parse(value.length() == 16 ? value + ":00" : value);
			} catch (DateTimeParseException e) {
				throw new IllegalArgumentException("error.schedule.dateTimeInvalid");
			}
		}
	}

	/** 포털(관리) 화면은 에이전시 전용. 아티스트는 메인 홈만 사용. */
	private boolean isPortalUser(AuthenticatedUser principal) {
		return "ROLE_AGENCY".equals(principal.getRoleName());
	}
}
