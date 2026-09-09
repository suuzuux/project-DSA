package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Agency;
import megane6.weplanet.domain.entity.ArtistAccountProfile;
import megane6.weplanet.domain.entity.enumfolder.AgencyStatus;
import megane6.weplanet.domain.entity.enumfolder.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArtistAccountProfileRepository
		extends JpaRepository<ArtistAccountProfile, Long> {
	
	Optional<ArtistAccountProfile> findByUser_Id(Long userId);
	
	List<ArtistAccountProfile> findByAgency(Agency agency);
	
	List<ArtistAccountProfile> findByAgency_Id(Long agencyId);

	@Query("""
        select profile
        from ArtistAccountProfile profile
        join fetch profile.user user
        join fetch profile.agency agency
        where user.id in :userIds
        """)
	List<ArtistAccountProfile> findAllByUserIds(
			@Param("userIds") List<Long> userIds
	);
	
	@Query("""
        select profile
        from ArtistAccountProfile profile
        join fetch profile.user user
        join fetch profile.agency agency
        where (
              :userStatus is null
              or user.status = :userStatus
          )
          and (
              :agencyStatus is null
              or agency.status = :agencyStatus
          )
          and (
              :keyword is null
              or lower(user.username)
                    like lower(concat('%', :keyword, '%'))
              or lower(user.nickname)
                    like lower(concat('%', :keyword, '%'))
              or lower(user.email)
                    like lower(concat('%', :keyword, '%'))
              or lower(profile.stageName)
                    like lower(concat('%', :keyword, '%'))
              or lower(coalesce(profile.position, ''))
                    like lower(concat('%', :keyword, '%'))
              or lower(agency.name)
                    like lower(concat('%', :keyword, '%'))
          )
        order by
          user.createdAt desc,
          user.id desc
        """)
	List<ArtistAccountProfile> searchForAdmin(
			@Param("userStatus") UserStatus userStatus,
			@Param("agencyStatus") AgencyStatus agencyStatus,
			@Param("keyword") String keyword
	);
}
