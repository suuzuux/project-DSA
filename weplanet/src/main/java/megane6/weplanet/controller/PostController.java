package megane6.weplanet.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import megane6.weplanet.domain.entity.BoardType;
import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.User;
import megane6.weplanet.repository.UserRepository;
import megane6.weplanet.service.PostService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final UserRepository userRepository;

    @GetMapping("/posts/{boardType}")
    public String list(
            @PathVariable String boardType,
            Model model
    ) {

        // URL의 소문자 문자열 ("fan")을 enum(FAN)으로 변환
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

        List<Post> posts = postService.getPostsByBoardType(type);

        log.debug("게시판 조회: {}, 게시글 수: {}", type, posts.size());

        model.addAttribute("posts", posts);
        model.addAttribute("boardType", type);

        return "postList";
    }

    // 글쓰기 폼 화면 이동
    @GetMapping("/posts/{boardType}/new")
    public String newForm(
            @PathVariable String boardType,
            Model model
    ) {
        BoardType type = BoardType.valueOf(boardType.toUpperCase());
        model.addAttribute("boardType", type);

        return "postForm";
    }

    // 글쓰기 저장 처리
    @PostMapping("/posts/{boardType}/new")
    public String create(
            @PathVariable String boardType,
            @RequestParam String title,
            @RequestParam String content
    ) {
        BoardType type = BoardType.valueOf(boardType.toUpperCase());

// 임시 - 로그인 기능 완성 전까지는 DB에 미리 넣어둔 테스트용 유저 1번을 작성자로 사용
// 형준님 회원가입/로그인 완성되면 이 부분을 실제 로그인한 User로 교체 예정
        User tempAuthor = userRepository.findById(1L)
                .orElseThrow(() -> new IllegalArgumentException("테스트용 유저(id=1)가 없습니다. DB에 먼저 넣어주세요."));

        postService.createPost(type, title, content, tempAuthor);

        log.debug("게시글 작성 완료 : boardType={}, title={}", type, title);

        return "redirect:/posts/" + boardType;
    }
}
