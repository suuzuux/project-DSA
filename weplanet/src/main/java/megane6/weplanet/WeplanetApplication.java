package megane6.weplanet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// @ConfigurationProperties 클래스를 찾아 등록
@ConfigurationPropertiesScan
public class WeplanetApplication {

	public static void main(String[] args) {
		SpringApplication.run(WeplanetApplication.class, args);
	}

}
