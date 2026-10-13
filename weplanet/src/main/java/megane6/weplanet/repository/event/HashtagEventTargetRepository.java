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
	
	// 순위표용 참여팀 목록 (artist 함께 조회)
	@EntityGraph(attributePaths = "artist")
	List<HashtagEventTarget> findByEvent_IdOrderByIdAsc(Long eventId);
	
	// 글 작성 시각에 진행 중인 이벤트의 이 커뮤니티 참여 정보 (확정된 이벤트 제외).
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
	
	
	// 참여 아티스트 검색 - 활동 중인 그룹을 이름·영문명·소속사명으로 찾아 DTO 로 반환.
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
	
	// 이미 고른 아티스트들의 표시 정보
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