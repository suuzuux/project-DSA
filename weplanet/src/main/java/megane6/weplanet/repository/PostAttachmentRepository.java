package megane6.weplanet.repository;

import megane6.weplanet.domain.entity.Post;
import megane6.weplanet.domain.entity.PostAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {

    // 게시글에 달린 첨부파일들을 등록 순서대로 조회
    List<PostAttachment> findByPostOrderByIdAsc(Post post);

    // 게시글 삭제 시 첨부파일 기록도 같이 지우기 위함
    void deleteByPost(Post post);
}
