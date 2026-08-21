package megane6.weplanet.domain.entity.enumfolder;

public enum Role {
	FAN, ARTIST, AGENCY, ADMIN;
	
	public String authority() {
		return "ROLE_" + name();
	}
}
