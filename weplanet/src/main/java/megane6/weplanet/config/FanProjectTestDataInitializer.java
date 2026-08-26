package megane6.weplanet.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.FanBadgeOwnership;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.FanBadgeType;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.repository.FanBadgeOwnershipRepository;
import megane6.weplanet.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 팬 프로젝트 등록을 실제로 테스트할 수 있는 팬 계정을 만들어둔다.
 * <p>
 * ProjectService.createProject()는 등록 전에 두 가지를 확인하는데,
 * 지금은 둘 다 채워주는 코드가 서비스 어디에도 없어서 등록이 항상 실패한다.
 * ① 휴대폰 본인인증 완료 (User.phoneVerifiedAt)
 * ② 해당 아티스트 기준 기본 뱃지 5개 이상 + 스페셜 뱃지 1개 이상
 * <p>
 * 실제 본인인증(PASS 등)과 뱃지 지급 정책이 붙기 전까지, 개발/시연용으로
 * 이 두 조건을 미리 충족시킨 팬 계정을 하나 넣어두는 것이 이 클래스의 역할이다.
 * <p>
 * 실행 순서 : TestArtistDataInitializer(@Order(1))가 아티스트를 만든 뒤여야
 * 뱃지를 지급할 대상이 존재하므로 @Order(3)으로 가장 마지막에 돈다.
 * <p>
 * 주의 : 개발용 시드다. 실제 서비스에 올릴 때는 이 클래스를 제거하거나
 * @Profile("!prod") 등으로 막아야 한다.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class FanProjectTestDataInitializer implements ApplicationRunner {

	// 팬 프로젝트 등록 테스트 계정 (로그인: fan_project_tester / Test1234)
	private static final String TEST_FAN_USERNAME = "fan_project_tester";
	private static final String TEST_FAN_PASSWORD = "Test1234";
	private static final String TEST_FAN_NICKNAME = "프로젝트테스터";
	private static final String TEST_FAN_REAL_NAME = "정휘원";
	private static final String TEST_FAN_EMAIL = "fan_project_tester@weplanet.test";
	private static final String TEST_FAN_PHONE = "01012345678";

	// ProjectService의 등록 조건과 같은 값 - 조건이 바뀌면 여기도 같이 맞춰야 함
	private static final int BASIC_BADGE_COUNT = 5;
	private static final int SPECIAL_BADGE_COUNT = 1;

	private final UserRepository ur;
	private final FanBadgeOwnershipRepository fbr;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		User testFan = ensureTestFan();
		ensurePhoneVerified(testFan);

		// 아티스트마다 따로 조건을 봐야 하므로(뱃지는 아티스트별로 셈),
		// 어느 커뮤니티에서 등록을 시도하든 되도록 전체 아티스트에게 지급해둔다.
		List<User> artists = ur.findByRole(Role.ARTIST);
		if (artists.isEmpty()) {
			log.warn("아티스트가 없어 팬 프로젝트 테스트 뱃지를 지급하지 못했습니다.");
			return;
		}

		for (User artist : artists) {
			ensureBadges(testFan, artist);
		}
	}

	// 테스트 팬 계정이 없으면 만들고, 있으면 그대로 재사용
	private User ensureTestFan() {
		return ur.findByUsername(TEST_FAN_USERNAME)
				.orElseGet(() -> {
					User fan = User.createFan(
							TEST_FAN_USERNAME,
							passwordEncoder.encode(TEST_FAN_PASSWORD),
							TEST_FAN_REAL_NAME,
							TEST_FAN_NICKNAME,
							TEST_FAN_EMAIL
					);
					User saved = ur.save(fan);
					log.info("팬 프로젝트 테스트 계정 생성: {} / {}", TEST_FAN_USERNAME, TEST_FAN_PASSWORD);
					return saved;
				});
	}

	// 이미 인증된 계정이면 시각을 새로 덮어쓰지 않는다 (매 기동마다 값이 바뀌면 헷갈림)
	private void ensurePhoneVerified(User fan) {
		if (fan.isPhoneVerified()) {
			return;
		}
		fan.verifyPhone(TEST_FAN_PHONE);
		log.info("팬 프로젝트 테스트 계정 휴대폰 본인인증 처리: {}", TEST_FAN_USERNAME);
	}

	/**
	 * 이 팬이 이 아티스트에게 가진 유효 뱃지 수를 세어, 모자란 만큼만 지급한다.
	 * <p>
	 * 세는 조건(revokedAt is null)을 ProjectService와 똑같이 맞췄기 때문에,
	 * 여기서 채워두면 등록 시 뱃지 관문은 반드시 통과한다.
	 * 뱃지 코드는 (fan_id, artist_id, badge_code)가 유니크라 번호를 붙여 구분한다.
	 */
	private void ensureBadges(User fan, User artist) {
		awardMissing(fan, artist, FanBadgeType.BASIC, BASIC_BADGE_COUNT, "BASIC", "기본 뱃지");
		awardMissing(fan, artist, FanBadgeType.SPECIAL, SPECIAL_BADGE_COUNT, "SPECIAL", "스페셜 뱃지");
	}

	private void awardMissing(
			User fan,
			User artist,
			FanBadgeType badgeType,
			int requiredCount,
			String codePrefix,
			String namePrefix
	) {
		long owned = fbr.countByFan_IdAndArtist_IdAndBadgeTypeAndRevokedAtIsNull(
				fan.getId(),
				artist.getId(),
				badgeType
		);
		if (owned >= requiredCount) {
			return;
		}

		// 이미 가진 개수 다음 번호부터 채워서 badge_code 중복을 피함
		for (int no = (int) owned + 1; no <= requiredCount; no++) {
			FanBadgeOwnership badge = FanBadgeOwnership.award(
					fan,
					artist,
					codePrefix + "_" + String.format("%02d", no),
					namePrefix + " " + no,
					badgeType,
					null // 시스템 자동 지급
			);
			fbr.save(badge);
		}

		log.info("테스트 뱃지 지급: fan={} artist={} type={} {}개 -> {}개",
				TEST_FAN_USERNAME, artist.getNickname(), badgeType, owned, requiredCount);
	}
}
