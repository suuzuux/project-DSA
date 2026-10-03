package megane6.weplanet.service.portal;

import lombok.RequiredArgsConstructor;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.portal.ArtistBlock;
import megane6.weplanet.repository.portal.ArtistBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ArtistBlockService {

    private final ArtistBlockRepository artistBlockRepository;

    @Transactional(readOnly = true)
    public List<ArtistBlock> getBlocks(User artist) {
        return artistBlockRepository.findByArtistOrderByCreatedAtDesc(artist);
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(User artist, User blockedUser) {
        return artistBlockRepository.existsByArtistAndBlockedUser(artist, blockedUser);
    }

    public void requireNotBlocked(User artist, User blockedUser) {
        if (isBlocked(artist, blockedUser)) {
            throw new IllegalStateException("error.community.blocked");
        }
    }

    public void block(User artist, User blockedUser, String reason) {
        artistBlockRepository.findByArtistAndBlockedUser(artist, blockedUser)
                .orElseGet(() -> artistBlockRepository.save(ArtistBlock.create(artist, blockedUser, reason)));
    }

    public void unblock(User artist, Long blockId) {
        ArtistBlock block = artistBlockRepository.findByIdAndArtist(blockId, artist)
                .orElseThrow(() -> new IllegalArgumentException("error.block.notFound"));
        artistBlockRepository.delete(block);
    }
}
