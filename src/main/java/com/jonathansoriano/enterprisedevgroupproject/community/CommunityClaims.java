package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.identity.IdentityClaim;
import org.springframework.stereotype.Component;

/**
 * Binds community rows (groups, memberships, posts, comments, likes, events) written under a
 * caller's verified address to their subject (ADR-012). Once per subject: these rows are only
 * ever written by their owner, who now always carries a subject.
 */
@Component
class CommunityClaims implements IdentityClaim {

    private final GroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final PostLikeRepository likes;
    private final EventRepository events;

    CommunityClaims(GroupRepository groups, GroupMembershipRepository memberships, PostRepository posts,
                    CommentRepository comments, PostLikeRepository likes, EventRepository events) {
        this.groups = groups;
        this.memberships = memberships;
        this.posts = posts;
        this.comments = comments;
        this.likes = likes;
        this.events = events;
    }

    @Override
    public void claim(String email, String subject) {
        groups.claim(email, subject);
        memberships.claim(email, subject);
        posts.claim(email, subject);
        comments.claim(email, subject);
        likes.claim(email, subject);
        events.claim(email, subject);
    }
}
