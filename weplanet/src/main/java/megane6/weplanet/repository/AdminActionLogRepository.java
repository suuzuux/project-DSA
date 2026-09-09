package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.AdminActionLog;
import megane6.weplanet.domain.entity.enumfolder.AdminActionType;
import megane6.weplanet.domain.entity.enumfolder.AdminTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AdminActionLogRepository
		extends JpaRepository<AdminActionLog, Long> {
	
	@Query(
			value = """
            select adminLog
            from AdminActionLog adminLog
            join fetch adminLog.actor actor
            where (
                :action is null
                or adminLog.action = :action
            )
            and (
                :targetType is null
                or adminLog.targetType = :targetType
            )
            and (
                :actorId is null
                or actor.id = :actorId
            )
            and (
                :targetId is null
                or adminLog.targetId = :targetId
            )
            and (
                :fromDateTime is null
                or adminLog.createdAt >= :fromDateTime
            )
            and (
                :toDateTime is null
                or adminLog.createdAt < :toDateTime
            )
            and (
                :keyword is null
                or lower(actor.username)
                    like lower(concat('%', :keyword, '%'))
                or lower(actor.nickname)
                    like lower(concat('%', :keyword, '%'))
                or lower(coalesce(adminLog.reason, ''))
                    like lower(concat('%', :keyword, '%'))
            )
            order by
                adminLog.createdAt desc,
                adminLog.id desc
            """,
			countQuery = """
            select count(adminLog)
            from AdminActionLog adminLog
            join adminLog.actor actor
            where (
                :action is null
                or adminLog.action = :action
            )
            and (
                :targetType is null
                or adminLog.targetType = :targetType
            )
            and (
                :actorId is null
                or actor.id = :actorId
            )
            and (
                :targetId is null
                or adminLog.targetId = :targetId
            )
            and (
                :fromDateTime is null
                or adminLog.createdAt >= :fromDateTime
            )
            and (
                :toDateTime is null
                or adminLog.createdAt < :toDateTime
            )
            and (
                :keyword is null
                or lower(actor.username)
                    like lower(concat('%', :keyword, '%'))
                or lower(actor.nickname)
                    like lower(concat('%', :keyword, '%'))
                or lower(coalesce(adminLog.reason, ''))
                    like lower(concat('%', :keyword, '%'))
            )
            """
	)
	Page<AdminActionLog> searchForAdmin(
			@Param("action")
			AdminActionType action,
			
			@Param("targetType")
			AdminTargetType targetType,
			
			@Param("actorId")
			Long actorId,
			
			@Param("targetId")
			Long targetId,
			
			@Param("fromDateTime")
			LocalDateTime fromDateTime,
			
			@Param("toDateTime")
			LocalDateTime toDateTime,
			
			@Param("keyword")
			String keyword,
			
			Pageable pageable
	);
	
	long countByCreatedAtGreaterThanEqual(
			LocalDateTime createdAt
	);
	
	long countByTargetType(
			AdminTargetType targetType
	);
}