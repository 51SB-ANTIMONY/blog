package kr.antimony.blog.repository;

import kr.antimony.blog.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlogRepository extends JpaRepository<Article, Long> {
    List<Article> findAllByOrderByIdDesc();
}
