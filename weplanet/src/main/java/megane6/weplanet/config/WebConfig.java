package megane6.weplanet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** /uploads/** 주소를 프로젝트의 uploads 폴더로 연결해 업로드 파일을 보여준다. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

    /** 별도 로직 없이 화면만 띄우는 정적 페이지 주소를 연결한다. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/shop.html", "/shop");
        registry.addRedirectViewController("/shop-cart.html", "/shop/cart");
        registry.addRedirectViewController("/shop-detail.html", "/shop");
        registry.addViewController("/signup-wireframe").setViewName("signup-wireframe");
        registry.addViewController("/login-wireframe").setViewName("login-wireframe");
    }
}
