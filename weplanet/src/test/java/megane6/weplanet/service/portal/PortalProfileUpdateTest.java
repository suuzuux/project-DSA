package megane6.weplanet.service.portal;

import megane6.weplanet.domain.entity.User;
import megane6.weplanet.domain.entity.enumfolder.Gender;
import megane6.weplanet.domain.entity.portal.ArtistProfile;
import megane6.weplanet.repository.main.UserRepository;
import megane6.weplanet.repository.portal.ArtistProfileRepository;
import megane6.weplanet.repository.calendar.ArtistScheduleRepository;
import megane6.weplanet.service.community.CommunityUrls;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalProfileUpdateTest {
    @Test
    void selectedArtistsGenderIsSavedEvenWhenControllerPassesDetachedAccount() {
        UserRepository users = mock(UserRepository.class);
        ArtistProfileRepository profiles = mock(ArtistProfileRepository.class);
        ArtistScheduleRepository schedules = mock(ArtistScheduleRepository.class);
        PortalManagementService service = new PortalManagementService(null, schedules, profiles,
                null, null, null, null, null, null, null, mock(MessageSource.class),
                mock(CommunityUrls.class), users);
        User detached = artist();
        User managed = artist();
        detached.changeGender(Gender.MALE);
        managed.changeGender(Gender.MALE);
        ArtistProfile profile = ArtistProfile.create(managed);
        when(users.findById(10L)).thenReturn(Optional.of(managed));
        when(profiles.findByArtist(managed)).thenReturn(Optional.of(profile));

        service.updateProfile(detached, "아티스트", "artist@test.com", null, "FEMALE",
                null, "소개", null, null, false, false);

        assertEquals(Gender.FEMALE, managed.getGender());
        assertEquals(Gender.MALE, detached.getGender());
        verify(users).save(managed);
        assertEquals("소개", profile.getIntro());
        verify(profiles).save(profile);
    }

    @Test
    void teamNameAndDescriptionRespectNewLimits() {
        UserRepository users = mock(UserRepository.class);
        ArtistProfileRepository profiles = mock(ArtistProfileRepository.class);
        PortalManagementService service = new PortalManagementService(null, mock(ArtistScheduleRepository.class), profiles,
                null, null, null, null, null, null, null, mock(MessageSource.class),
                mock(CommunityUrls.class), users);
        User artist = artist();
        ArtistProfile profile = ArtistProfile.create(artist);
        when(users.findById(10L)).thenReturn(Optional.of(artist));
        when(profiles.findByArtist(artist)).thenReturn(Optional.of(profile));
        service.updateProfile(artist, "팀".repeat(20), "artist@test.com", null, "OTHER", null,
                "설".repeat(100), null, null, false, false);
        assertEquals(20, artist.getNickname().length());
        assertEquals(100, profile.getIntro().length());
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(artist,
                "팀".repeat(21), "artist@test.com", null, "OTHER", null, "설명", null, null, false, false));
        assertThrows(IllegalArgumentException.class, () -> service.updateProfile(artist,
                "팀", "artist@test.com", null, "OTHER", null, "설".repeat(101), null, null, false, false));
    }

    private User artist() {
        User artist = User.createArtist("artist", "encoded", "아티스트", "아티스트", "artist@test.com");
        ReflectionTestUtils.setField(artist, "id", 10L);
        return artist;
    }
}
