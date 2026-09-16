package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentResponse;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

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

    public List<PostResponse> list(Long groupId, String requesterEmail) {
        List<Post> posts = groupId == null
                ? postRepository.findByGroupIdIsNullOrderByPinnedDescCreatedAtDesc()
                : postRepository.findByGroupIdOrderByPinnedDescCreatedAtDesc(groupId);
        return posts.stream().map(post -> toResponse(post, requesterEmail)).collect(Collectors.toList());
    }

    public PostResponse create(PostRequest request, String authorEmail) {
        Post post = postRepository.save(Post.builder()
                .authorEmail(authorEmail)
                .groupId(request.getGroupId())
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .pinned(false)
                .build());
        return toResponse(post, authorEmail);
    }

    public void delete(Long postId, String requesterEmail) {
        Post post = findOrThrow(postId);
        if (!post.getAuthorEmail().equalsIgnoreCase(requesterEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can delete this post");
        }
        postRepository.delete(post);
    }

    public void togglePin(Long postId, String requesterEmail) {
        Post post = findOrThrow(postId);
        if (!post.getAuthorEmail().equalsIgnoreCase(requesterEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can pin this post");
        }
        post.setPinned(!post.isPinned());
        postRepository.save(post);
    }

    public void like(Long postId, String userEmail) {
        findOrThrow(postId);
        if (postLikeRepository.findByPostIdAndUserEmail(postId, userEmail).isEmpty()) {
            postLikeRepository.save(PostLike.builder().postId(postId).userEmail(userEmail).build());
        }
    }

    public void unlike(Long postId, String userEmail) {
        postLikeRepository.deleteByPostIdAndUserEmail(postId, userEmail);
    }

    public CommentResponse comment(Long postId, CommentRequest request, String authorEmail) {
        findOrThrow(postId);
        Comment comment = commentRepository.save(Comment.builder()
                .postId(postId)
                .authorEmail(authorEmail)
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

    private PostResponse toResponse(Post post, String requesterEmail) {
        boolean likedByMe = requesterEmail != null
                && postLikeRepository.findByPostIdAndUserEmail(post.getId(), requesterEmail).isPresent();
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
