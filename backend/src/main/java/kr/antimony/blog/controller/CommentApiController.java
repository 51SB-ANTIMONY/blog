package kr.antimony.blog.controller;

import jakarta.validation.Valid;
import kr.antimony.blog.dto.AddCommentRequest;
import kr.antimony.blog.dto.CommentResponse;
import kr.antimony.blog.dto.UpdateCommentRequest;
import kr.antimony.blog.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class CommentApiController {
    private final CommentService commentService;

    // 댓글 전체 조회. 로그인하지 않아도 볼 수 있다
    @GetMapping("/api/articles/{articleId}/comments")
    public ResponseEntity<List<CommentResponse>> findAllComments(@PathVariable Long articleId,
                                                                 Principal principal) {
        List<CommentResponse> result = commentService.findAll(articleId, nameOf(principal));
        return ResponseEntity.ok()
                .body(result);
    }

    // 댓글 추가
    @PostMapping("/api/articles/{articleId}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long articleId, @Valid @RequestBody AddCommentRequest request, Principal principal) {
        CommentResponse result = commentService.save(request, articleId, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(result);
    }

    @PutMapping("/api/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(@PathVariable Long commentId, @Valid @RequestBody UpdateCommentRequest request, Principal principal) {
        CommentResponse result = commentService.update(request, commentId, principal.getName());
        return ResponseEntity.ok()
                .body(result);
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId, Principal principal) {
        commentService.delete(commentId, principal.getName());
        return ResponseEntity.noContent()
                .build();
    }

    // 로그인한 사용자의 이름(= ID 토큰의 sub). 로그인하지 않았으면 null
    private static String nameOf(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
