package kr.antimony.blog.repository;

import kr.antimony.blog.domain.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 글 아래 댓글 목록. 댓글 작성자 닉네임을 보여주므로 작성자까지 한 번에 읽는다.
    // 대댓글을 부모 아래로 묶는 일은 service에서 한다.
    @EntityGraph(attributePaths = "author")
    List<Comment> findAllByArticleIdOrderByIdAsc(Long articleId);

    // 수정, 삭제할 때 작성자가 본인인지 확인하므로 작성자까지 읽는다.
    @Override
    @EntityGraph(attributePaths = "author")
    Optional<Comment> findById(Long id);
}
