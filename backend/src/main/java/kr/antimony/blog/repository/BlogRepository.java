package kr.antimony.blog.repository;

import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Category;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogRepository extends JpaRepository<Article, Long> {

    // 작성자 닉네임을 함께 보여 주므로 작성자까지 한 번에 읽는다.
    @EntityGraph(attributePaths = "author")
    List<Article> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = "author")
    List<Article> findAllByCategoryOrderByIdDesc(Category category);

    @EntityGraph(attributePaths = "author")
    @Override
    Optional<Article> findById(Long id);
}
