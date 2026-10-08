package megane6.weplanet.service.portal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.ArtistGroup;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.GroupGender;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.exception.LocalizedIllegalArgumentException;
import megane6.weplanet.exception.LocalizedIllegalStateException;
import megane6.weplanet.repository.agency.AgencyRepository;
import megane6.weplanet.repository.artist.ArtistAccountProfileRepository;
import megane6.weplanet.repository.artist.ArtistGroupRepository;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.service.agency.AgencyActivationService;
import megane6.weplanet.service.community.CommunityUrls;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 소속사가 포털에서 아티스트(그룹/솔로)를 등록한다.
 * 등록 = 커뮤니티 생성. 한 트랜잭션에서 아래를 모두 만들고, 하나라도 실패하면 전부 롤백한다.
 *   users(ARTIST, 활성화 대기) / artist_profiles / artist_groups(탐색 필터 포함) / 활성화 토큰
 * 멤버는 4단계에서 따로 추가한다. 멤버가 없으면 솔로로 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistRegistrationService {
	
	private static final int NAME_MAX_LENGTH = 50;		// users.nickname, artist_profiles.stage_name
	private static final int EMAIL_MAX_LENGTH = 50;		// users.username (이메일이 곧 로그인 아이디)
	private static final int NAME_EN_MAX_LENGTH = 100;	// artist_groups.name_en
	private static final int SHORT_TEXT_MAX_LENGTH = 50;	// fandom_name, nationality, category
	private static final Pattern EMAIL_PATTERN
			= Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	
	private final UserRepository ur;
	private final AgencyRepository ar;
	private final ArtistAccountProfileRepository aapr;
	private final ArtistGroupRepository agr;
	private final AgencyActivationService aas;
	
	@Transactional
	public RegisteredArtist register(User agencyUser, RegisterCommand command) {
		Agency agency = requireAgency(agencyUser);
		
		String groupName = requireText(command.groupName(), NAME_MAX_LENGTH,
				"error.artistRegistration.groupNameRequired", "error.artistRegistration.groupNameTooLong");
		String nameEn = requireNameEn(command.nameEn());
		String email = requireEmail(command.email());

		assertAvailable(groupName, nameEn, email);
		
		// 1) 그룹 계정 = 커뮤니티의 주인. 닉네임이 곧 커뮤니티 이름으로 보인다.
		User artist = User.createPendingArtist(email, groupName, groupName);
		artist.assignAgency(agency);
		ur.save(artist);
		
		// 2) 아티스트 계정 프로필 (group_members FK가 이 테이블을 본다)
		aapr.save(ArtistAccountProfile.create(artist, agency, groupName, command.debutDate()));
		
		// 3) 그룹 = 커뮤니티. id를 그룹 계정 users.id 와 같게 맞춘다.
		//    미디어(board_media.group_id)와 커뮤니티 URL(/community/{id})이 같은 번호를 쓰기 때문
		//    커뮤니티 탐색(검색/필터)용 정보도 같은 행에 넣는다. 멤버를 추가하기 전까지는 솔로(1명)로 본다.
		LocalDateTime now = LocalDateTime.now();
		agr.save(ArtistGroup.builder()
				.id(artist.getId())
				.agencyId(agency.getId())
				.name(groupName)
				.nameEn(nameEn)
				.fandomName(optionalText(command.fandomName(), SHORT_TEXT_MAX_LENGTH, "error.artistRegistration.fandomNameTooLong"))
				.debutDate(command.debutDate())
				.status("ACTIVE")
				.gender(command.gender())
				.memberCount(1)
				.nationality(optionalText(command.nationality(), SHORT_TEXT_MAX_LENGTH, "error.artistRegistration.nationalityTooLong"))
				.category(normalizeCategory(optionalText(command.category(), SHORT_TEXT_MAX_LENGTH, "error.artistRegistration.categoryTooLong")))
				.createdAt(now)
				.updatedAt(now)
				.build());

		// 4) 그룹 이메일로 보낼 활성화 링크
		AgencyActivationService.IssuedActivation activation = aas.issueActivationToken(artist);
		
		log.info("아티스트 등록 완료: artistId={}, agencyId={}, name={}",
				artist.getId(), agency.getId(), groupName);
		
		return new RegisteredArtist(artist.getId(), email, groupName, agency.getName(), activation);
	}
	
	// 그룹 계정 활성화 메일 재발송. 이전 링크는 모두 무효가 된다(AgencyActivationService.reissue 참고).
	@Transactional
	public RegisteredArtist reissueActivation(User agencyUser, Long artistId) {
		Agency agency = requireAgency(agencyUser);
		
		User artist = ur.findOneById(artistId)
				.filter(user -> user.getRole() == Role.ARTIST)
				.filter(user -> agency.getId().equals(user.agencyId()))
				.orElseThrow(() -> new IllegalStateException("error.portalArtist.notManaged"));
		
		if (artist.getStatus() != UserStatus.PENDING_ACTIVATION) {
			throw new IllegalStateException("error.artistRegistration.alreadyActivated");
		}
		
		AgencyActivationService.IssuedActivation activation = aas.reissueActivationToken(artist);
		
		return new RegisteredArtist(artist.getId(), artist.getUsername(), artist.getNickname(), agency.getName(), activation);
	}
	
	private Agency requireAgency(User agencyUser) {
		Long agencyId = agencyUser == null ? null : agencyUser.agencyId();
		
		if (agencyId == null) {
			throw new IllegalStateException("error.portalArtist.agencyMissing");
		}
		
		return ar.findById(agencyId)
				.orElseThrow(() -> new IllegalStateException("error.portalArtist.agencyNotFound"));
	}
	
	// 이메일(로그인 아이디)은 모든 계정과 겹치면 안 된다.
	// 그룹명(커뮤니티 이름)은 다른 커뮤니티와만 겹치지 않으면 된다 - 팬 닉네임과는 겹쳐도 됨(체크 표시로 구분)
	private void assertAvailable(String groupName, String nameEn, String email) {
		if (ur.existsByUsername(email) || ur.existsByEmail(email)) {
			throw new LocalizedIllegalStateException("error.artistRegistration.emailTaken", email);
		}

		if (ur.existsByNicknameAndRole(groupName, Role.ARTIST) || agr.existsByName(groupName)) {
			throw new LocalizedIllegalStateException("error.artistRegistration.nameTaken", groupName);
		}

		// 영문명 = 커뮤니티 주소(/kiikii). 대소문자만 다른 것도 같은 주소라 중복으로 본다(컬럼 콜레이션이 대소문자 무시)
		if (agr.existsByNameEn(nameEn)) {
			throw new LocalizedIllegalStateException("error.artistRegistration.nameEnTaken", nameEn);
		}
	}

	// 영문명은 커뮤니티 주소(localhost:9999/{영문명})로 쓰이므로 필수 + 주소로 쓸 수 있는 모양이어야 한다
	private String requireNameEn(String value) {
		String nameEn = requireText(value, NAME_EN_MAX_LENGTH,
				"error.artistRegistration.nameEnRequired", "error.artistRegistration.nameEnTooLong");

		if (!CommunityUrls.SLUG_PATTERN.matcher(nameEn).matches()) {
			throw new IllegalArgumentException("error.artistRegistration.nameEnInvalid");
		}

		if (!CommunityUrls.isUsableSlug(nameEn)) {
			throw new LocalizedIllegalArgumentException("error.artistRegistration.nameEnReserved", nameEn);
		}

		return nameEn;
	}
	
	private String requireEmail(String value) {
		String email = requireText(value, EMAIL_MAX_LENGTH,
				"error.artistRegistration.emailRequired", "error.artistRegistration.emailTooLong");
		
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new IllegalArgumentException("error.artistRegistration.emailInvalid");
		}
		
		return email.toLowerCase();
	}
	
	// 예외 메시지는 메시지 키로 던지고 화면(컨트롤러)에서 Messages.resolve(e)로 번역한다.
	// 글자 수 제한은 *_MAX_LENGTH 상수를 {0}으로 넘기므로, 상수만 바꾸면 3개 언어 문구가 같이 바뀐다.
	private String requireText(String value, int maxLength, String blankKey, String tooLongKey) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(blankKey);
		}
		
		String trimmed = value.trim();
		
		if (trimmed.length() > maxLength) {
			throw new LocalizedIllegalArgumentException(tooLongKey, maxLength);
		}
		
		return trimmed;
	}
	
	// 커뮤니티 검색의 카테고리 필터(아이돌/배우)는 DB의 한국어 값으로 검색하므로, 다른 언어 표기로 들어와도 한국어 값으로 맞춘다.
	// (그 밖의 값은 그대로 저장)
	private static String normalizeCategory(String category) {
		if (category == null) {
			return null;
		}
		return switch (category.toLowerCase(Locale.ROOT)) {
			case "아이돌", "idol", "アイドル" -> "아이돌";
			case "배우", "actor", "actress", "俳優", "女優" -> "배우";
			default -> category;
		};
	}

	private String optionalText(String value, int maxLength, String tooLongKey) {
		if (value == null || value.isBlank()) {
			return null;
		}
		
		String trimmed = value.trim();
		
		if (trimmed.length() > maxLength) {
			throw new LocalizedIllegalArgumentException(tooLongKey, maxLength);
		}
		
		return trimmed;
	}
	
	// 등록 폼 입력값. @ModelAttribute 로 폼 필드 이름과 같은 이름에 바로 담긴다.
	// 빈 날짜/빈 성별은 null 로 들어온다.
	public record RegisterCommand(
			String groupName,
			String nameEn,
			String email,
			String fandomName,
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debutDate,
			GroupGender gender,
			String nationality,
			String category
	) {}
	
	// 등록 결과. 컨트롤러가 트랜잭션 커밋 후 이 정보로 초대 메일을 보낸다.
	public record RegisteredArtist(
			Long artistId,
			String username,
			String groupName,
			String agencyName,
			AgencyActivationService.IssuedActivation activation
	) {}
}