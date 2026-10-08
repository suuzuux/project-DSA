package megane6.weplanet.repository.event;

import megane6.weplanet.domain.dto.event.HashtagCount;
import megane6.weplanet.domain.entity.enumfolder.events.HashtagEntryStatus;
import megane6.weplanet.domain.entity.event.HashtagEventEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HashtagEventEntryRepository extends JpaRepository<HashtagEventEntry, Long> {
	
	// 같은 글이 두 번 기록되지 않게 미리 확인
	boolean existsByPost_Id(Long postId);
	
	// 팬의 [from, to) 동안 특정 상태 글 수 (하루 3건 판정)
	@Query("""
			select count(e)
			from HashtagEventEntry e
			where e.target.id = :targetId
			  and e.fanId = :fanId
			  and e.status = :status
			  and e.createdAt >= :from
			  and e.createdAt < :to
			""")
	long countFanEntries(
			@Param("targetId") Long targetId,
			@Param("fanId") Long fanId,
			@Param("status") HashtagEntryStatus status,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to
	);
	
	// 이벤트의 참여팀별 인정 글 수 → [참여팀 id, 글 수]
	@Query("""
			select new megane6.weplanet.domain.dto.event.HashtagCount(e.target.id, count(e))
			from HashtagEventEntry e
			where e.target.event.id = :eventId
			  and e.status = :status
			group by e.target.id
			""")
	List<HashtagCount<Long>> countPostsByTarget(
			@Param("eventId") Long eventId,
			@Param("status") HashtagEntryStatus status
	);
	
	// 참여팀별 참여 인원 (같은 팬은 1명, 현재 가입자만)
	@Query("""
			select new megane6.weplanet.domain.dto.event.HashtagCount(e.target.id, count(distinct e.fanId))
			from HashtagEventEntry e
			where e.target.event.id = :eventId
			  and e.status = :status
			  and exists (
			      select m.id
			      from CommunityMember m
			      where m.fanId = e.fanId
			        and m.artistId = e.target.artist.id
			  )
			group by e.target.id
			""")
	List<HashtagCount<Long>> countParticipantsByTarget(
			@Param("eventId") Long eventId,
			@Param("status") HashtagEntryStatus status
	);
	
	// 이벤트의 상태(인정/제외 사유)별 글 수 → [상태, 글 수]
	@Query("""
			select new megane6.weplanet.domain.dto.event.HashtagCount(e.status, count(e))
			from HashtagEventEntry e
			where e.target.event.id = :eventId
			group by e.status
			""")
	List<HashtagCount<HashtagEntryStatus>> countByStatus(@Param("eventId") Long eventId);
	
	// 일자별 그래프용 인정 글 작성 시각
	@Query("""
			select e.createdAt
			from HashtagEventEntry e
			where e.target.event.id = :eventId
			  and e.status = :status
			""")
	List<LocalDateTime> findCreatedAts(
			@Param("eventId") Long eventId,
			@Param("status") HashtagEntryStatus status
	);
}