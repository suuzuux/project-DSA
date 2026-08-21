package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.service.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping("/posts/{boardType}")
    public String list(
            @PathVariable String boardType,
            Model model
    ) {

        // URL의 소문자 문자열 ("fan")을 enum(FAN)으로 변환
        BoardType type = BoardType.valueOf(boardType.toUpperCase()); // 이문법 뭐지

        List<Post> posts = postService.getPostsByBoardType(type);

        log.debug("게시판 조회: {}, 게시글 수: {}", type, posts.size());

        model.addAttribute("posts", posts);
        model.addAttribute("boardType", type);

        return "postList";
    }
}
