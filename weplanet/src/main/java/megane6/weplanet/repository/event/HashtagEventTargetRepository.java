package megane6.weplanet.repository.event;

import megane6.weplanet.domain.dto.event.HashtagArtistOption;
import megane6.weplanet.domain.entity.enumfolder.Role;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import megane6.weplanet.domain.entity.event.HashtagEventTarget;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HashtagEventTargetRepository extends JpaRepository<HashtagEventTarget, Long> {
	
	// 이벤트의 참여팀 목록 (순위표용). 아티스트 이름을 바로 쓰므로 artist 를 같이 가져온다
	@EntityGraph(attributePaths = "artist")
	List<HashtagEventTarget> findByEvent_IdOrderByIdAsc(Long eventId);
	
	// 글 작성 시각(at)에 진행 중인 이벤트에서, 이 커뮤니티(artistId)의 참여 정보
	// 동시 진행은 1개뿐이라 결과도 최대 1개. 집계가 확정된 이벤트는 더 이상 기록하지 않는다
	@Query("""
			select t
			from HashtagEventTarget t
			join fetch t.event e
			where t.artist.id = :artistId
			  and e.startAt <= :at
			  and e.endAt >= :at
			  and e.finalizedAt is null
			""")
	Optional<HashtagEventTarget> findOngoingTarget(
			@Param("artistId") Long artistId,
			@Param("at") LocalDateTime at
	);
	
	
	// 이벤트 폼의 "참여 아티스트 검색". 활동 중인 아티스트(그룹) 계정만, 이름·영문명·소속사명으로 찾는다.
	// ArtistGroup / ArtistAccountProfile 은 User 와 연관관계(@ManyToOne 등)가 없어서 "on 조건"으로 직접 조인한다
	// (둘 다 id 가 아티스트 users.id 와 같은 값)
	// select new ... : 엔티티 대신 화면에 필요한 값만 골라 DTO(record)로 바로 만든다
	@Query("""
			select new megane6.weplanet.domain.dto.event.HashtagArtistOption(
			    u.id, u.nickname, g.nameEn, a.name, p.profileImg)
			from User u
			left join u.agency a
			left join ArtistGroup g on g.id = u.id
			left join ArtistAccountProfile p on p.userId = u.id
			where u.role = :role
			  and u.status = :status
			  and (
			      lower(u.nickname) like lower(concat('%', :keyword, '%'))
			      or lower(coalesce(g.nameEn, '')) like lower(concat('%', :keyword, '%'))
			      or lower(coalesce(a.name, '')) like lower(concat('%', :keyword, '%'))
			  )
			order by u.nickname asc
			""")
	List<HashtagArtistOption> searchArtistOptions(
			@Param("role") Role role,
			@Param("status") UserStatus status,
			@Param("keyword") String keyword,
			Pageable pageable
	);
	
	// 폼을 다시 그릴 때: 이미 고른 아티스트 id 들의 표시 정보 (검색과 같은 모양)
	@Query("""
			select new megane6.weplanet.domain.dto.event.HashtagArtistOption(
			    u.id, u.nickname, g.nameEn, a.name, p.profileImg)
			from User u
			left join u.agency a
			left join ArtistGroup g on g.id = u.id
			left join ArtistAccountProfile p on p.userId = u.id
			where u.id in :ids
			""")
	List<HashtagArtistOption> findArtistOptionsByIds(@Param("ids") Collection<Long> ids);
}