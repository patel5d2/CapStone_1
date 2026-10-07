package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentResponse;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostResponse;
import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/** Authorship and likes key on the Clerk subject, never the address (ADR-012). */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;

    public PostService(PostRepository postRepository, CommentRepository commentRepository, PostLikeRepository postLikeRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
    }

    /** {@code viewer} may be null (no session): then nothing shows as liked. */
    public List<PostResponse> list(Long groupId, Party viewer) {
        List<Post> posts = groupId == null
                ? postRepository.findByGroupIdIsNullOrderByPinnedDescCreatedAtDesc()
                : postRepository.findByGroupIdOrderByPinnedDescCreatedAtDesc(groupId);
        return posts.stream().map(post -> toResponse(post, viewer)).collect(Collectors.toList());
    }

    public PostResponse create(PostRequest request, Party author) {
        Post post = postRepository.save(Post.builder()
                .authorEmail(author.email())
                .authorSubject(author.subject())
                .groupId(request.getGroupId())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .pinned(false)
                .build());
        return toResponse(post, author);
    }

    public void delete(Long postId, Party caller) {
        Post post = findOrThrow(postId);
        if (!caller.owns(post.getAuthorSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can delete this post");
        }
        postRepository.delete(post);
    }

    public void togglePin(Long postId, Party caller) {
        Post post = findOrThrow(postId);
        if (!caller.owns(post.getAuthorSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can pin this post");
        }
        post.setPinned(!post.isPinned());
        postRepository.save(post);
    }

    public void like(Long postId, Party caller) {
        findOrThrow(postId);
        if (!postLikeRepository.existsByPostIdAndUserSubject(postId, caller.subject())) {
            postLikeRepository.save(PostLike.builder().postId(postId)
                    .userEmail(caller.email()).userSubject(caller.subject()).build());
        }
    }

    @Transactional
    public void unlike(Long postId, Party caller) {
        postLikeRepository.deleteByPostIdAndUserSubject(postId, caller.subject());
    }

    public CommentResponse comment(Long postId, CommentRequest request, Party author) {
        findOrThrow(postId);
        Comment comment = commentRepository.save(Comment.builder()
                .postId(postId)
                .authorEmail(author.email())
                .authorSubject(author.subject())
                .content(request.getContent())
                .build());
        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .authorEmail(comment.getAuthorEmail())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    public List<CommentResponse> listComments(Long postId) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(comment -> CommentResponse.builder()
                        .id(comment.getId())
                        .postId(comment.getPostId())
                        .authorEmail(comment.getAuthorEmail())
                        .content(comment.getContent())
                        .createdAt(comment.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private Post findOrThrow(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found: " + id));
    }

    private PostResponse toResponse(Post post, Party viewer) {
        boolean likedByMe = viewer != null
                && postLikeRepository.existsByPostIdAndUserSubject(post.getId(), viewer.subject());
        return PostResponse.builder()
                .id(post.getId())
                .authorEmail(post.getAuthorEmail())
                .groupId(post.getGroupId())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .pinned(post.isPinned())
                .likeCount(postLikeRepository.countByPostId(post.getId()))
                .commentCount(commentRepository.countByPostId(post.getId()))
                .likedByMe(likedByMe)
                .createdAt(post.getCreatedAt())
                .build();
    }
}
