package kr.antimony.blog.service;

import kr.antimony.blog.domain.Article;
import kr.antimony.blog.domain.Comment;
import kr.antimony.blog.domain.User;
import kr.antimony.blog.dto.AddCommentRequest;
import kr.antimony.blog.dto.CommentResponse;
import kr.antimony.blog.dto.UpdateCommentRequest;
import kr.antimony.blog.exception.CommentForbiddenException;
import kr.antimony.blog.exception.CommentNotFoundException;
import kr.antimony.blog.exception.InvalidCommentException;
import kr.antimony.blog.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final BlogService blogService;
    private final UserService userService;

    // 댓글 목록 조회 - 첫 댓글이 위로 오도록 id 오름차순
    // 응답을 만들 때 부모 댓글의 작성자를 읽으므로, 그동안 DB 연결(영속성 컨텍스트)을 열어 둔다
    @Transactional(readOnly = true)
    public List<CommentResponse> findAll(Long articleId, String accountsSub) {
        Article article = blogService.findById(articleId);
        List<Comment> comments = commentRepository.findAllByArticleIdOrderByIdAsc(articleId);

        // 부모 댓글 id -> 대댓글 목록
        Map<Long, List<CommentResponse>> repliesByParentId = new HashMap<>();
        for(Comment comment : comments) {
            if(comment.getParent() == null) { continue; } // 부모 댓글의 경우 건너뜀
            Long parentId = comment.getParent().getId();

            List<CommentResponse> replies = repliesByParentId.get(parentId);
            if(replies == null) {
                replies = new ArrayList<>();
                repliesByParentId.put(parentId, replies);
            }
            replies.add(new CommentResponse(comment, article, accountsSub, List.of()));
        }

        // 대댓글 있는 경우는 앞에서 만든 CommentResponse 그대로, 대댓글 없는 경우는 빈 리스트로
        List<CommentResponse> result = new ArrayList<>();
        for(Comment comment : comments) {
            if(comment.getParent() != null) { continue; } // 대댓글
            List<CommentResponse> replies = repliesByParentId.getOrDefault(comment.getId(), List.of());
            result.add(new CommentResponse(comment, article, accountsSub, replies));
        }

        return result;
    }

    // 댓글 추가
    public CommentResponse save(AddCommentRequest request, Long articleId, String accountsSub) {
        User author = userService.findByAccountsSub(accountsSub);
        Article article = blogService.findById(articleId);
        Comment parent = null;
        if(request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new CommentNotFoundException(request.getParentId()));
            // 다른 글의 댓글에 답글을 달면, 어느 글의 목록에도 보이지 않는 답글이 생긴다
            if (!parent.getArticle().getId().equals(articleId)) {
                throw new InvalidCommentException("이 글의 댓글에만 답글을 달 수 있습니다.");
            }
            if (parent.isDeleted()) {
                throw new InvalidCommentException("삭제된 댓글에는 답글을 달 수 없습니다.");
            }
        }
        Comment savedComment = commentRepository.save(request.toEntity(article, parent, author));
        return new CommentResponse(savedComment, article, accountsSub, List.of());
    }

    // 댓글 수정
    @Transactional
    public CommentResponse update(UpdateCommentRequest request, Long commentId, String accountsSub) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        authorizeCommentAuthor(comment, accountsSub);
        comment.update(request.getContent(), request.isLocked());

        Article article = comment.getArticle();
        // 응답의 replies는 비어 있다. 대댓글은 목록 조회에서 붙인다
        return new CommentResponse(comment, article, accountsSub, List.of());
    }

    // 댓글 삭제
    @Transactional
    public void delete(Long commentId, String accountsSub) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        authorizeCommentAuthor(comment, accountsSub);
        comment.markDeleted();
    }

    // 댓글 작성자 본인인지 확인한다. 아니면 403 (Forbidden)
    private static void authorizeCommentAuthor(Comment comment, String accountsSub) {
        if (!comment.getAuthor().getAccountsSub().equals(accountsSub)) {
            throw new CommentForbiddenException();
        }
    }
}
