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
import megane6.weplanet.repository.AgencyRepository;
import megane6.weplanet.repository.ArtistAccountProfileRepository;
import megane6.weplanet.repository.ArtistGroupRepository;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.AgencyActivationService;
import megane6.weplanet.service.community.CommunityUrls;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/** 소속사의 아티스트 등록 = 커뮤니티 생성 (계정·프로필·그룹·활성화 토큰을 한 트랜잭션으로 생성). */
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
		
		// 1) 그룹 계정 = 커뮤니티 주인 (닉네임이 커뮤니티 이름)
		User artist = User.createPendingArtist(email, groupName, groupName);
		artist.assignAgency(agency);
		ur.save(artist);
		
		// 2) 아티스트 계정 프로필 (group_members FK 대상)
		aapr.save(ArtistAccountProfile.create(artist, agency, groupName, command.debutDate()));
		
		// 3) 그룹 = 커뮤니티 (id 를 그룹 계정 users.id 와 같게, 탐색 정보 포함, 멤버 전까지 솔로).
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

		// 4) 활성화 링크 발급
		AgencyActivationService.IssuedActivation activation = aas.issueActivationToken(artist);
		
		log.info("아티스트 등록 완료: artistId={}, agencyId={}, name={}",
				artist.getId(), agency.getId(), groupName);
		
		return new RegisteredArtist(artist.getId(), email, groupName, agency.getName(), activation);
	}
	
	// 활성화 메일 재발송 (이전 링크는 무효)
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
	
	// 이메일은 모든 계정과, 그룹명은 다른 커뮤니티와만 겹치지 않으면 된다.
	private void assertAvailable(String groupName, String nameEn, String email) {
		if (ur.existsByUsername(email) || ur.existsByEmail(email)) {
			throw new LocalizedIllegalStateException("error.artistRegistration.emailTaken", email);
		}

		if (ur.existsByNicknameAndRole(groupName, Role.ARTIST) || agr.existsByName(groupName)) {
			throw new LocalizedIllegalStateException("error.artistRegistration.nameTaken", groupName);
		}

		// 영문명 = 커뮤니티 주소 (대소문자만 달라도 중복)
		if (agr.existsByNameEn(nameEn)) {
			throw new LocalizedIllegalStateException("error.artistRegistration.nameEnTaken", nameEn);
		}
	}

	// 영문명은 커뮤니티 주소라 필수이고 주소 형식이어야 한다.
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
	
	// 예외는 메시지 키로 던지고 글자 수 한도는 {0} 으로 넘긴다.
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
	
	// 카테고리 필터 검색을 위해 다른 언어 표기를 한국어 값으로 맞춘다.
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
	
	// 등록 폼 입력값 (빈 날짜·성별은 null)
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
	
	// 등록 결과 (커밋 후 초대 메일 발송용)
	public record RegisteredArtist(
			Long artistId,
			String username,
			String groupName,
			String agencyName,
			AgencyActivationService.IssuedActivation activation
	) {}
}