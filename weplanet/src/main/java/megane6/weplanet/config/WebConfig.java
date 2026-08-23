package megane6.weplanet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * "/uploads/파일명" 이라는 주소로 브라우저가 접속하면,
 * 실제로는 프로젝트 폴더 바로 아래 있는 진짜 "uploads" 폴더 안의 파일을 찾아서 보여주도록 연결해주는 설정.
 * <p>
 * 우리가 지금까지 만든 화면(.html)들은 src/main/resources/templates 안에 있고,
 * 이미지 같은 정적 파일은 보통 src/main/resources/static 안에 미리 넣어둔 것만 보여줄 수 있음.
 * 근데 사용자가 "지금 막 업로드한 파일"은 미리 넣어둘 수 없으니(실행 중에 새로 생기는 파일이라서),
 * 이렇게 별도 설정으로 "uploads 폴더도 브라우저에서 접근 가능하게 열어줘"라고 스프링에게 알려줘야 함.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}
