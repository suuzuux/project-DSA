package megane6.weplanet.repository.portal;

import megane6.weplanet.domain.dto.ArtistCount;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.ArtistBlock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArtistBlockRepository extends JpaRepository<ArtistBlock, Long> {
    // 차단 회원 정보까지 한번에 가져와 추가 조회가 반복되는 것을 막아줌
    @EntityGraph(attributePaths = "blockedUser")
    List<ArtistBlock> findByArtistOrderByCreatedAtDesc(User artist);
    
    Optional<ArtistBlock> findByArtistAndBlockedUser(User artist, User blockedUser);
    Optional<ArtistBlock> findByIdAndArtist(Long id, User artist);
    boolean existsByArtistAndBlockedUser(User artist, User blockedUser);
    long countByArtist(User artist);
    
    @Query("""
        select new megane6.weplanet.domain.dto.ArtistCount(
            artistBlock.artist.id,
            count(artistBlock)
        )
        from ArtistBlock artistBlock
        where artistBlock.artist.id in :artistIds
        group by artistBlock.artist.id
        """)
    List<ArtistCount> countBlocksByArtistIds(
            @Param("artistIds") Collection<Long> artistIds
    );
}
