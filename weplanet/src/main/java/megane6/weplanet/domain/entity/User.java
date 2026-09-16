package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.convert.PlaintextBytesConverter;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.Gender;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
	@Id
	@GeneratedValue(strategy =  GenerationType.IDENTITY)
	private Long id;			// 디비 내부 식별 id
	
	@Column(nullable = false, unique = true, length = 50)
	private String username;	// 로그인 아이디
	
	// AUTH-10: 소셜 전용 가입자는 비밀번호를 안 만들 수 있다. 이 컬럼의 NOT NULL 제약(및 DB 마이그레이션)은
	// 사용자가 직접 마지막에 정리하기로 해서, 이 어노테이션은 일부러 그대로 두었다 - 코드상으로는
	// password가 null일 수 있다는 전제로 모든 로직을 작성했다. hasPassword() 참고.
	@Column(nullable = true, length = 60)
	private String password;	// 암호화(BCrypt)된 비밀번호. 소셜 전용 가입자는 null일 수 있음.
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;		// 가입자 역할 (FAN/ARTIST/AGENCY/ADMIN)
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status; // 계정 상태 (ACTIVE/DORMANT/SUSPENDED/WITHDRAWN)

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "agency_id")
	private Agency agency;	// 소속사 (ARTIST/AGENCY 계정, 없으면 NULL)
	
	@Convert(converter = PlaintextBytesConverter.class)
	@Column(name = "real_name", nullable = false, columnDefinition = "VARBINARY(255)")	// 실명 (결제 명의 대조용)
	private String realName;
	
	@Column(nullable = false, length = 50)
	private String nickname;	// 가입자 닉네임
	
	@Column(nullable = false, length = 255)
	private String email;		// 가입자 이메일
	
	@Convert(converter = PlaintextBytesConverter.class)
	@Column(columnDefinition = "VARBINARY(255)")
	private String phone;		// 본인인증 - 결제 알림
	
	@Column(name = "phone_hash", length = 64)
	private String phoneHash;	// 지금은 평문 단계라 사용 안 함, 암호화 붙일 때 채움
	
	@Column(name = "birth_date")
	private LocalDate birthDate;	// 본인인증
	
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private Gender gender;		// 가입자 성별
	
	@Column(length = 10)
	private String zipcode;		// 우편번호
	
	@Column(length = 255)
	private String address1;	// 기본 주소 (도로명/지번)
	
	@Convert(converter = PlaintextBytesConverter.class)
	@Column(name = "address2", columnDefinition = "VARBINARY(512)")
	private String address2;		// 상세 주소 (동/호수 등)
	
	@Column(name = "email_verified_at")
	private LocalDateTime emailVerifiedAt;	 // 이메일 인증 완료 시각 (인증 전이면 null)
	
	@Column(name = "last_login_at")
	private LocalDateTime lastLoginAt;		// 마지막 로그인 시각
	
	@Column(name = "dormant_notice_sent_at")
	private LocalDateTime dormantNoticeSentAt;	// 휴면 전환 30일 전 사전 안내 메일 발송 시각
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;		// 가입(레코드 생성) 시각
	
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;		// 최종 수정 시각
	
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;		// 탈퇴(소프트 삭제) 처리 시각
	
	// AUTH-10: 더 이상 "가입 경로"가 아니라 "지금 이 계정에 연동된 소셜 provider"를 뜻한다.
	// 연동이 없는 계정(로컬 비밀번호만 있거나, 연동을 해제한 계정)은 null. 이 컬럼의 NOT NULL 제약(및 DB
	// 마이그레이션)도 password와 마찬가지로 사용자가 직접 마지막에 정리하기로 해서 어노테이션은 그대로 두었다.
	@Enumerated(EnumType.STRING)
	@Column(nullable = true, length = 20)
	private AuthProvider provider;		// 연동된 소셜 provider (GOOGLE/KAKAO/LINE), 연동 없으면 null
	
	@Column(name = "provider_id", length = 255)
	private String providerId;		// 소셜 플랫폼 고유 ID (연동 없으면 null)

	// [설정 - 이벤트·혜택 알림] 광고성 정보(이벤트/혜택/신상품 등) 수신 동의. 기본값 false(미동의).
	// 회원가입 화면의 "(선택) 광고 및 마케팅 활용 동의" 체크박스와 같은 값을 공유한다 - 가입 때 정한 값이
	// 곧바로 설정 화면에 반영되고, 설정 화면에서 바꾸면 그게 최종값이 된다.
	@Column(name = "marketing_consent", nullable = false)
	private boolean marketingConsent;

	// [설정 - 이벤트·혜택 알림] 내가 가입(CommunityMember)한 커뮤니티 아티스트의 새 게시글/공지/라이브 시작을
	// 이메일로 받을지. marketingConsent(광고 동의)와는 별개의 값 - 이건 광고가 아니라 가입한 아티스트의
	// 실제 활동 소식이라 독립적으로 켜고 끌 수 있게 했다. 기본값 false.
	// (팔로우(GroupFollow)는 About 위젯 전용 기능이라 여기 기준이 아님 - 게시판 접근 권한/기존 벨 알림과
	// 동일하게 "가입" 기준으로 맞춤)
	@Column(name = "community_activity_email_enabled", nullable = false)
	private boolean communityActivityEmailEnabled;

	// [설정 - 이벤트·혜택 알림] 오후 9시~오전 8시(KST)에도 알림을 받을지. 기본값 false.
	// CommunityActivityNotifier가 이메일 발송 직전에 이 값을 확인해서, 꺼져 있으면 야간 시간대엔 건너뛴다.
	@Column(name = "night_notification_allowed", nullable = false)
	private boolean nightNotificationAllowed;
	
	private User(String username, String password, String realName, String nickname, String email, Role role, AuthProvider provider, String providerId) {
		this.username = username;
		this.password = password;
		this.realName = realName;
		this.nickname = nickname;
		this.email	  = email;
		this.role = role;
		this.status = UserStatus.ACTIVE;
		this.provider = provider;
		this.providerId = providerId;
	}
	
	private User(String username, String password, String realName, String nickname, String email, Role role) {
		this(username, password, realName, nickname, email, role, null, null);
	}
	
	// AUTH-10: 소셜 신규 가입은 비밀번호를 요구하지 않는다 - encodedPassword가 null로 들어올 수 있다.
	public static User createSocialFan(String username, String encodedPassword, String realName, String nickname, String email, AuthProvider provider, String providerId) {
		return new User(username, encodedPassword, realName, nickname, email, Role.FAN, provider, providerId);
	}
	
	public static User createFan(String username, String encodedPassword, String realName, String nickname, String email) {
		return new User(username, encodedPassword, realName, nickname, email, Role.FAN);
	}
	
	public static User createArtist(String username, String encodedPassword, String realName, String nickname, String email) {
		return new User(username, encodedPassword, realName, nickname, email, Role.ARTIST);
	}
	
	public static User createAgencyStaff(String username, String encodedPassword, String realName, String nickname, String email) {
		return new User(username, encodedPassword, realName, nickname, email, Role.AGENCY);
	}
	
	public static User createAdmin(String username, String encodedPassword, String realName, String nickname, String email) {
		return new User(username, encodedPassword, realName, nickname, email, Role.ADMIN);
	}
	
	@PrePersist
	public void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}
	
	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
	
	public boolean isLoginable() {
		return this.status == UserStatus.ACTIVE;
	}
	
	public void recordLogin() {
		this.lastLoginAt = LocalDateTime.now();
		this.dormantNoticeSentAt = null;	// 다시 로그인했으니 다음 휴면 주기에 사전 안내를 다시 보낼 수 있도록 초기화
	}

	public void markEmailVerified(LocalDateTime verifiedAt) {
		this.emailVerifiedAt = verifiedAt;
	}
	
	// AUTH-10: 비밀번호는 건드리지 않는다 - 로컬 비밀번호와 소셜 연동은 이제 서로 독립적으로 공존한다.
	public void linkSocialProvider(AuthProvider provider, String providerId) {
		this.provider = provider;
		this.providerId = providerId;
	}

	// AUTH-10 신설: 설정 화면에서 "연결 해제"를 누르면 호출. 비밀번호는 손대지 않는다.
	public void unlinkSocialProvider() {
		this.provider = null;
		this.providerId = null;
	}

	// AUTH-10: 비밀번호가 설정돼 있는지 여부. provider(LOCAL) 대신 이 값으로 "로컬 로그인이 가능한 계정인지"를 판단한다.
	public boolean hasPassword() {
		return this.password != null;
	}

	public void changePortalProfile(String nickname, String email) {
		this.nickname = nickname;
		this.email = email;
	}

	public void changeGender(Gender gender) {
		this.gender = gender;
	}

	public void changeBirthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
	}
	
	public void changeRealName(String realName) {
		this.realName = realName;
	}
	
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}
	
	public void markDormantNoticeSent() {
		this.dormantNoticeSentAt = LocalDateTime.now();
	}
	
	public void markDormant() {
		this.status = UserStatus.DORMANT;
	}
	
	public void reactivate() {
		this.status = UserStatus.ACTIVE;
		this.dormantNoticeSentAt = null;
		this.lastLoginAt = LocalDateTime.now();
	}
	
	public void withdraw() {
		this.status = UserStatus.WITHDRAWN;
		this.deletedAt = LocalDateTime.now();
		anonymizePersonalInfo();
	}
	
	// id(PK)는 이미 전역 유일하므로 별도 타임스탬프 없이 이 값만으로 충돌 없는 고유 식별자를 만들 수 있다.
	// email/username/providerId는 재사용 가능하도록 다른 값으로 치환하고,
	// realName은 컬럼이 NOT NULL이라 null 대신 고정 문구로 치환, phone/address2는 nullable이라 null로 지운다.
	// 탈퇴는 영구 처리라 이 값들은 되돌리지 않는다 (복구 기능 없음).
	private void anonymizePersonalInfo() {
		String suffix = "withdrawn_" + this.id;
		this.username = suffix;
		this.email = suffix + "@withdrawn.weplanet.local";
		if (this.providerId != null) {
			this.providerId = suffix;
		}
		this.realName = "탈퇴한 회원";
		this.phone = null;
		this.address2 = null;
	}

	// [관리자 제재] 신고 누적 등으로 관리자가 계정을 정지시킬 때 씀.
	// isLoginable()이 ACTIVE/DORMANT만 허용하므로, 정지 즉시 로그인이 막힘.
	public void suspend() {
		this.status = UserStatus.SUSPENDED;
	}

	// [관리자 제재 해제] 정지된 계정을 다시 활성 상태로 되돌림
	public void reinstate() {
		this.status = UserStatus.ACTIVE;
	}

	public void assignAgency(Agency agency) {
		this.agency = agency;
	}

	public Long agencyId() {
		return agency == null ? null : agency.getId();
	}

	// [설정 - 이벤트·혜택 알림] 토글 클릭 시 서버가 최종값을 확정한다 (화면 상태를 그대로 믿지 않음).
	public void changeMarketingConsent(boolean consent) {
		this.marketingConsent = consent;
	}

	public void changeCommunityActivityEmailEnabled(boolean enabled) {
		this.communityActivityEmailEnabled = enabled;
	}

	public void changeNightNotificationAllowed(boolean allowed) {
		this.nightNotificationAllowed = allowed;
	}
}
