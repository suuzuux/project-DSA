package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
	
	Optional<GroupMember> findFirstByArtist_IdAndLeftAtIsNull(Long artistId);
}