package megane6.weplanet.controller;

import megane6.weplanet.domain.dto.ArtistCardView;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Comment;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 프로필·하이라이트 HTML에서 문자와 작성자 이미지가 함께 출력되는지 확인한다.
class CommunityPreviewRenderingTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        engine.setTemplateEngineMessageSource(messages);
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new PreviewController()).setViewResolvers(views).build();
    }

    @Test
    void profileHistoryPreservesTildeAndShowsAuthorsImage() throws Exception {
        for (String tab : List.of("posts", "comments", "likes", "bookmarks")) {
            var result = mvc.perform(get("/preview/" + tab))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("안녕~ 10~20 / 좋아요")))
                    .andExpect(content().string(not(containsString("안녕\\ 1020"))));
            if (tab.equals("comments")) {
                result.andExpect(content().string(containsString("/community/10/fan/100#comment-200")));
            }
            if (tab.equals("likes") || tab.equals("bookmarks")) {
                result.andExpect(content().string(containsString("src=\"/uploads/community-avatar.png\"")));
            }
        }
    }

    @Test
    void highlightShowsArtistAndFanAvatars() throws Exception {
        mvc.perform(get("/preview/highlight"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"/uploads/community-avatar.png\"")))
                .andExpect(content().string(containsString("src=\"/uploads/artist-logo.png\"")));
    }

    @Test
    void missingOrHiddenAvatarUsesPersonIcon() throws Exception {
        mvc.perform(get("/preview/likes").param("hidden", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("avatar__placeholder")))
                .andExpect(content().string(not(containsString("community-avatar.png"))));
    }

    @Controller
    static class PreviewController {
        @GetMapping("/preview/{tab}")
        String preview(@PathVariable String tab,
                       @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean hidden,
                       Model model) {
            User artist = user(10L, "아티스트");
            User fan = user(1L, "계정닉");
            Post fanPost = post(100L, artist, fan, BoardType.FAN);
            Post artistPost = post(101L, artist, artist, BoardType.ARTIST);
            Comment fanComment = Comment.builder().id(200L).post(fanPost).author(fan)
                    .content("댓글~ / 그대로").createdAt(LocalDateTime.now()).build();
            Comment artistComment = Comment.builder().id(201L).post(fanPost).author(artist)
                    .content("아티스트 댓글").createdAt(LocalDateTime.now()).build();
            model.addAttribute("artist", ArtistCardView.from(artist));
            model.addAttribute("isOwnProfile", true);
            model.addAttribute("myComments", List.of(fanComment));
            model.addAttribute("myPosts", List.of(fanPost));
            model.addAttribute("likedPosts", List.of(fanPost));
            model.addAttribute("bookmarkedPosts", List.of(fanPost));
            model.addAttribute("myPostCommentCounts", Map.of(100L, 1L));
            model.addAttribute("authorNicknames", Map.of("1", "커뮤니티닉", "10", "아티스트"));
            model.addAttribute("authorAvatarUrls", hidden ? Map.of() : Map.of(
                    "1", "/uploads/community-avatar.png", "10", "/uploads/artist-logo.png"));
            model.addAttribute("artistPosts", List.of(artistPost));
            model.addAttribute("fanPosts", List.of(fanPost));
            model.addAttribute("artistCommentsWidget", List.of(artistComment));
            model.addAttribute("artistPostThumbnails", Map.of());
            model.addAttribute("fanPostThumbnails", Map.of());
            model.addAttribute("fanPostCommentCounts", Map.of(100L, 1L));
            model.addAttribute("liveNow", false);
            model.addAttribute("highlightReplays", List.of());
            return tab.equals("highlight") ? "community/highlight :: .community-main/section"
                    : "community/profile :: [data-tab-panel='" + tab + "']";
        }

        private static User user(Long id, String nickname) {
            User user = mock(User.class);
            when(user.getId()).thenReturn(id);
            when(user.getNickname()).thenReturn(nickname);
            return user;
        }

        private static Post post(Long id, User artist, User author, BoardType board) {
            return Post.builder().id(id).artist(artist).author(author).boardType(board)
                    .title("안녕\\ 1020 / 좋아요").content("안녕\\~ 10~20 / 좋아요")
                    .createdAt(LocalDateTime.now()).build();
        }
    }
}
