package megane6.weplanet.domain.entity.enumfolder;

public enum GroupGender {
	BOY, GIRL, MIXED, SOLO;
	
	public String label() {
		return switch (this) {
			case BOY -> "보이그룹";
			case GIRL -> "걸그룹";
			case MIXED -> "혼성";
			case SOLO -> "솔로";
		};
	}
}