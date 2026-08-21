package megane6.weplanet.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import megane6.weplanet.domain.entity.convert.PlaintextBytesConverter;
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
	
	private User(String username, String password, String realName, String nickname, String email) {
		this.username = username;
		this.password = password;
		this.realName = realName;
		this.nickname = nickname;
		this.email	  = email;
		this.role = Role.FAN;
		this.status = UserStatus.ACTIVE;
	}
	
	// 공개 회원가입에서 쓰는 팩토리 - 선택 항목(gender/phone/birthDate/주소)은 나중에 마이페이지에서 채움
	public static User createFan(String username, String encodedPassword, String realName, String nickname, String email) {
		return new User(username, encodedPassword, realName, nickname, email);
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
}
