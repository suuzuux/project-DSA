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
	
	// 같은 글이 두 번 기록되지 않게 (post_id UNIQUE 에 걸리기 전에 미리 확인)
	boolean existsByPost_Id(Long postId);
	
	// 이 팬이 [from, to) 동안 이 참여팀에서 특정 상태로 기록된 글 수 → "1인 1일 3건" 판정에 사용
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
	
	// 이벤트의 참여팀별 참여 인원 → [참여팀 id, 인원]
	// count(distinct 팬) : 같은 팬이 여러 번 써도 1명. exists : 지금도 그 커뮤니티 가입자인 팬만 센다
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
	
	// 일자별 그래프용: 인정된 글들의 작성 시각 (날짜별 묶기는 자바에서 한다)
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