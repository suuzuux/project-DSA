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
	
	@Column(nullable = false, length = 60)
	private String password;	// 암호화(BCrypt)된 비밀번호
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;		// 가입자 역할 (FAN/ARTIST/AGENCY/ADMIN)
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status; // 계정 상태 (ACTIVE/DORMANT/SUSPENDED/WITHDRAWN)
	
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
	
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;		// 가입(레코드 생성) 시각
	
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;		// 최종 수정 시각
	
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;		// 탈퇴(소프트 삭제) 처리 시각
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AuthProvider provider;		// 가입 경로 (LOCAL/GOOGLE/KAKAO/LINE)
	
	@Column(name = "provider_id", length = 255)
	private String providerId;		// 소셜 플랫폼 고유 ID (LOCAL 가입자는 null)
	
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
		this(username, password, realName, nickname, email, role, AuthProvider.LOCAL, null);
	}
	
	// 소셜 로그인(구글 등) 최초 로그인 시 자동 생성되는 팬 계정.
	// password는 실제 로그인에 쓰이지 않지만 컬럼이 NOT NULL이라, 호출부(OAuth2LoginSuccessHandler)에서
	// 무작위 값을 BCrypt로 인코딩해서 넘겨준다. realName은 소셜 플랫폼이 제공하는 이름값을 그대로 저장한다.
	public static User createSocialFan(String username, String encodedPassword, String realName, String nickname, String email, AuthProvider provider, String providerId) {
		return new User(username, encodedPassword, realName, nickname, email, Role.FAN, provider, providerId);
	}
	
	// 공개 회원가입에서 쓰는 팩토리 - 선택 항목(gender/phone/birthDate/주소)은 나중에 마이페이지에서 채움
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
		return this.status == UserStatus.ACTIVE || this.status == UserStatus.DORMANT;
	}
	
	public void recordLogin() {
		this.lastLoginAt = LocalDateTime.now();
	}

	public void markEmailVerified(LocalDateTime verifiedAt) {
		this.emailVerifiedAt = verifiedAt;
	}

	// 이메일이 같은 기존 계정(주로 아이디/비밀번호로 가입한 LOCAL 계정)에 소셜 로그인을 추가로 연동할 때 씀.
	// 기존 username/password는 그대로 유지되고 provider/provider_id만 채워져서,
	// 이후 이 소셜 계정으로도 같은 계정에 로그인할 수 있게 된다.
	public void linkSocialProvider(AuthProvider provider, String providerId) {
		this.provider = provider;
		this.providerId = providerId;
	}

	public void changePortalProfile(String nickname, String email) {
		this.nickname = nickname;
		this.email = email;
	}

	// 회원정보(마이페이지) 수정에서 이름을 바꿀 때 씀. realName은 암호화 컬럼이라
	// PlaintextBytesConverter가 저장/조회 시 알아서 변환해줌 - 여기선 평문 그대로 다루면 됨
	public void changeRealName(String realName) {
		this.realName = realName;
	}

	// 회원정보 수정 화면에서 새 비밀번호를 입력했을 때만 호출됨 (호출부에서 이미 인코딩된 값을 넘김)
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	// 카카오/LINE처럼 provider가 만들어주는 placeholder 이메일 형식을 아직 그대로 쓰고 있는지 확인.
	// DB에 별도 컬럼 없이 이메일 패턴만으로 판별해서, 가입 직후 한 번만 뜨는 입력 화면(social-complete-profile)을
	// 건너뛴 회원도 홈 화면 등에서 계속 감지해 안내할 수 있게 한다. (AuthProvider.placeholderEmailDomain 참고)
	public boolean hasPlaceholderSocialProfile() {
		String domain = provider.placeholderEmailDomain();
		return domain != null && email != null && email.endsWith("@" + domain);
	}

	// [회원탈퇴] 실제로 로우를 지우지 않고 상태만 WITHDRAWN으로 바꾸는 소프트 삭제.
	// 게시글/댓글/채팅/후원 내역 등 users.id를 참조하는 다른 테이블의 FK가 깨지지 않도록 하기 위함.
	public void withdraw() {
		this.status = UserStatus.WITHDRAWN;
		this.deletedAt = LocalDateTime.now();
	}
}
