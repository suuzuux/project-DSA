package megane6.weplanet.domain.entity.enumfolder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PartnershipApplicantType {
	ARTIST("아티스트"),
	AGENCY("소속사");
	
	private final String displayName;
}
