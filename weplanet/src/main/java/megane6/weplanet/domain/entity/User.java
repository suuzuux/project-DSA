package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.convert.PlaintextBytesConverter;
import megane6.weplanet.domain.entity.enumfolder.AuthProvider;
import megane6.weplanet.domain.entity.enumfolder.Gender;
import megane6.weplanet.domain.entity.enumfolder.Language;
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
	private Long id;			// 내부 식별 id
	
	@Column(nullable = false, unique = true, length = 50)
	private String username;	// 로그인 아이디
	
	// 소셜 전용 가입자는 비밀번호가 없을 수 있다 (hasPassword 로 판단).
	@Column(nullable = true, length = 60)
	private String password;	// BCrypt 비밀번호 (소셜 전용이면 null)
	
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
	
	// DB 에 UNIQUE KEY 가 있다 (엔티티에도 표시)
	@Column(nullable = false, length = 255, unique = true)
	private String email;		// 가입자 이메일
	
	@Convert(converter = PlaintextBytesConverter.class)
	@Column(columnDefinition = "VARBINARY(255)")
	private String phone;		// 본인인증 - 결제 알림
	
	@Column(name = "phone_hash", length = 64)
	private String phoneHash;	// 암호화 적용 시 사용 예정
	
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
	
	// 연동된 소셜 provider (없으면 null)
	@Enumerated(EnumType.STRING)
	@Column(nullable = true, length = 20)
	private AuthProvider provider;
	
	@Column(name = "provider_id", length = 255)
	private String providerId;		// 소셜 플랫폼 고유 ID (연동 없으면 null)

	// 광고성 정보 수신 동의 (가입 화면과 설정 토글이 공유).
	@Column(name = "marketing_consent", nullable = false)
	private boolean marketingConsent;

	// 아티스트 새 글·공지·라이브 시작 이메일 수신 여부 (광고 동의와 별개).
	@Column(name = "community_activity_email_enabled", nullable = false)
	private boolean communityActivityEmailEnabled;

	// 야간(21시~8시) 알림 수신 여부 (꺼져 있으면 야간 발송 건너뜀).
	@Column(name = "night_notification_allowed", nullable = false)
	private boolean nightNotificationAllowed;

	// 기본 서비스 언어 (화면 언어이자 AI 번역 대상 언어).
	@Enumerated(EnumType.STRING)
	@Column(name = "preferred_language", nullable = false, length = 10)
	private Language preferredLanguage = Language.KO;
	
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
	
	// 소셜 가입은 비밀번호 없이 만든다.
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
	
	// 입점 승인 시 만드는 소속사 대표 계정 (비밀번호는 초대 링크에서 본인이 설정).
	public static User createPendingAgencyOwner(String username, String realName, String nickname, String email) {
		User user = new User(username, null, realName, nickname, email, Role.AGENCY);
		user.status = UserStatus.PENDING_ACTIVATION;
		
		return user;
	}
	
	// 소속사가 등록한 아티스트 계정 (아이디는 그룹 이메일, 비밀번호는 초대 링크에서 설정).
	public static User createPendingArtist(String email, String groupName, String nickname) {
		User user = new User(email, null, groupName, nickname, email, Role.ARTIST);
		user.status = UserStatus.PENDING_ACTIVATION;
		
		return user;
	}
	
	// 그룹 멤버 계정 (프로필 선택으로만 로그인, 비밀번호는 첫 선택 때 설정).
	public static User createArtistMember(String username, String memberName, String nickname, String email) {
		return new User(username, null, memberName, nickname, email, Role.ARTIST_MEMBER);
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
		this.dormantNoticeSentAt = null;	// 다음 휴면 주기에 사전 안내를 다시 보내도록 초기화
	}

	public void markEmailVerified(LocalDateTime verifiedAt) {
		this.emailVerifiedAt = verifiedAt;
	}
	
	// 비밀번호는 그대로 두고 소셜을 연동한다.
	public void linkSocialProvider(AuthProvider provider, String providerId) {
		this.provider = provider;
		this.providerId = providerId;
	}

	// 소셜 연동 해제 (비밀번호는 그대로).
	public void unlinkSocialProvider() {
		this.provider = null;
		this.providerId = null;
	}

	// 아이디·비밀번호 로그인이 가능한 계정인지
	public boolean hasPassword() {
		return this.password != null;
	}

	// 메일을 받을 수 없는 시스템 주소인지 (*.weplanet.local)
	public boolean hasPlaceholderEmail() {
		return this.email != null && this.email.toLowerCase().endsWith(".weplanet.local");
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

	// 전화번호 변경 (비우면 null, 주문서 연락처 기본값).
	public void changePhone(String phone) {
		this.phone = phone;
	}
	
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}
	
	// 초대 링크로 비밀번호를 설정하면 이메일 인증도 완료 처리한다.
	public void activateWithPassword(String encodedPassword) {
		if (this.status != UserStatus.PENDING_ACTIVATION) {
			throw new IllegalStateException("error.activation.notPending");
		}
		
		if (encodedPassword == null || encodedPassword.isBlank()) {
			throw new IllegalArgumentException("signup.validation.passwordRequired");
		}
		
		this.password = encodedPassword;
		this.status = UserStatus.ACTIVE;
		this.emailVerifiedAt = LocalDateTime.now();
	}
	
	// 멤버가 프로필을 처음 선택할 때 비밀번호를 저장한다 (이미 있으면 막음).
	public void setInitialMemberPassword(String encodedPassword) {
		if (this.role != Role.ARTIST_MEMBER) {
			throw new IllegalStateException("error.member.notGroupMember");
		}
		
		if (hasPassword()) {
			throw new IllegalStateException("error.member.passwordAlreadySet");
		}
		
		if (encodedPassword == null || encodedPassword.isBlank()) {
			throw new IllegalArgumentException("signup.validation.passwordRequired");
		}
		
		this.password = encodedPassword;
	}
	
	// 소속사가 멤버 비밀번호를 초기화한다 (다음 선택 때 새로 설정).
	public void resetMemberPassword() {
		if (this.role != Role.ARTIST_MEMBER) {
			throw new IllegalStateException("error.member.notGroupMember");
		}
		
		this.password = null;
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
	
	// 탈퇴 시 개인정보를 익명 처리한다 (되돌릴 수 없음).
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
		// 개인정보처리방침에 맞춰 주소·생년월일·성별도 지운다.
		this.phoneHash = null;
		this.zipcode = null;
		this.address1 = null;
		this.birthDate = null;
		this.gender = null;
	}

	// 관리자 제재 - 계정 정지 (즉시 로그인 불가).
	public void suspend() {
		this.status = UserStatus.SUSPENDED;
	}

	// 관리자 제재 해제
	public void reinstate() {
		this.status = UserStatus.ACTIVE;
	}

	public void assignAgency(Agency agency) {
		this.agency = agency;
	}

	public Long agencyId() {
		return agency == null ? null : agency.getId();
	}
	
	// 아티스트 쪽 계정인지 (그룹·솔로 + 멤버, 템플릿에서 ${user.artistSide}).
	public boolean isArtistSide() {
		return this.role != null && this.role.isArtistSide();
	}
	
	// 커뮤니티 참여(가입·글쓰기·멤버십·팔로우)가 가능한 계정인지.
	public boolean canParticipateInCommunity() {
		return this.role == Role.FAN || isArtistSide();
	}

	// 광고성 정보 수신 동의 변경 (서버가 최종값 확정).
	public void changeMarketingConsent(boolean consent) {
		this.marketingConsent = consent;
	}

	public void changeCommunityActivityEmailEnabled(boolean enabled) {
		this.communityActivityEmailEnabled = enabled;
	}

	public void changeNightNotificationAllowed(boolean allowed) {
		this.nightNotificationAllowed = allowed;
	}

	// 기본 서비스 언어 변경 (AI 번역 대상 언어로도 사용).
	public void changePreferredLanguage(Language language) {
		this.preferredLanguage = language;
	}
}
