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

/** 그룹-멤버 소속 이력 (left_at 이 NULL 이면 활동 중, is_leader 는 사용하지 않음). */
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
	
	// 멤버 탈퇴 - 행을 남기고 탈퇴일만 기록한다.
	public void leave(LocalDate date) {
		if (!isActive()) {
			throw new IllegalStateException("error.groupMember.alreadyLeft");
		}
		
		this.leftAt = date;
	}
}