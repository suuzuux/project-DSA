package megane6.weplanet.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 그룹(커뮤니티) - 멤버 소속 이력. (group_members)
 * group_id  = artist_groups.id = 그룹 계정 users.id = 커뮤니티 id
 * artist_id = 멤버 계정 users.id (DB FK는 artist_profiles.user_id)
 * left_at 이 NULL 이면 활동 중인 멤버.
 * is_leader 컬럼은 리더 역할을 쓰지 않기로 해서 매핑하지 않는다(DB 기본값 0).
 */
@Entity
@Table(name = "group_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(name = "group_id", nullable = false)
	private Long groupId;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private User member;
	
	@Column(name = "joined_at", nullable = false)
	private LocalDate joinedAt;
	
	@Column(name = "left_at")
	private LocalDate leftAt;
	
	private GroupMember(Long groupId, User member, LocalDate joinedAt) {
		this.groupId = groupId;
		this.member = member;
		this.joinedAt = joinedAt;
	}
	
	public static GroupMember join(Long groupId, User member) {
		if (groupId == null || member == null) {
			throw new IllegalArgumentException("그룹과 멤버가 필요합니다.");
		}
		
		return new GroupMember(groupId, member, LocalDate.now());
	}
	
	public boolean isActive() {
		return this.leftAt == null;
	}
	
	// 7단계 멤버 탈퇴 처리에서 사용. 행을 지우지 않고 탈퇴일만 남긴다(이력 보존).
	public void leave(LocalDate date) {
		if (!isActive()) {
			throw new IllegalStateException("이미 탈퇴 처리된 멤버입니다.");
		}
		
		this.leftAt = date;
	}
}