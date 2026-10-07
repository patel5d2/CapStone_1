package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.EventRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupResponse;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostResponse;
import com.jonathansoriano.enterprisedevgroupproject.identity.CallerIdentity;
import com.jonathansoriano.enterprisedevgroupproject.identity.Party;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-012, community slice: post authorship, group membership and likes follow the Clerk
 * subject, through the real resolution path ({@link CallerIdentity#caller}).
 */
@SpringBootTest
@Transactional
class CommunityIdentityTest {

    @Autowired private CallerIdentity identity;
    @Autowired private PostService posts;
    @Autowired private GroupService groups;
    @Autowired private EventService events;
    @Autowired private PostRepository postRows;
    @Autowired private GroupRepository groupRows;
    @Autowired private GroupMembershipRepository memberships;
    @Autowired private PostLikeRepository likes;
    @Autowired private CommentRepository comments;
    @Autowired private EventRepository eventRows;

    private static Jwt token(String subject, String email) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .subject(subject).claim("email", email)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    private PostResponse post(Party author) {
        return posts.create(PostRequest.builder().content("hello").build(), author);
    }

    private GroupResponse group(Party creator) {
        return groups.create(GroupRequest.builder().name("Study").type(GroupType.GENERAL).build(), creator);
    }

    private PostResponse seen(Long postId, Party viewer) {
        return posts.list(null, viewer).stream().filter(p -> p.getId().equals(postId)).findFirst().orElseThrow();
    }

    private boolean joined(Long groupId, Party viewer) {
        return groups.search(null, null, null, viewer).stream()
                .filter(g -> g.getId().equals(groupId)).findFirst().orElseThrow().isJoined();
    }

    @Test
    @DisplayName("an address change keeps post authorship, likes and group membership")
    void addressChangeKeepsOwnership() {
        Party old = identity.caller(token("user_alice_cm_ac", "alice.cm@old.edu"));
        PostResponse post = post(old);
        posts.like(post.getId(), old);
        GroupResponse group = group(old);
        Long eventId = events.create(EventRequest.builder().title("Night").startsAt(Instant.now().plusSeconds(3600))
                .build(), old).getId();
        assertThat(eventRows.findById(eventId).orElseThrow())
                .satisfies(e -> assertThat(e.getCreatedBySubject()).isEqualTo("user_alice_cm_ac"))
                .satisfies(e -> assertThat(e.getCreatedByEmail()).isEqualTo("alice.cm@old.edu"));

        Party renamed = identity.caller(token("user_alice_cm_ac", "alice.cm@new.edu"));
        assertThat(seen(post.getId(), renamed).isLikedByMe()).isTrue();
        assertThat(groups.myGroups(renamed)).extracting(GroupResponse::getId).containsExactly(group.getId());
        assertThat(joined(group.getId(), renamed)).isTrue();

        posts.togglePin(post.getId(), renamed);
        assertThat(seen(post.getId(), renamed).isPinned()).isTrue();
        posts.unlike(post.getId(), renamed);
        assertThat(seen(post.getId(), renamed).getLikeCount()).isZero();
        groups.leave(group.getId(), renamed);
        assertThat(groups.myGroups(renamed)).isEmpty();
        posts.delete(post.getId(), renamed);
        assertThat(postRows.findById(post.getId())).isEmpty();
    }

    @Test
    @DisplayName("another student gets 403 on the author's post; a missing post or group is 404")
    void otherSubjectIsForbiddenAndMissingIsNotFound() {
        Party alice = identity.caller(token("user_alice_cm_fb", "alice.fb@school.edu"));
        Party bob = identity.caller(token("user_bob_cm_fb", "bob.fb@school.edu"));
        Long postId = post(alice).getId();

        assertThatThrownBy(() -> posts.delete(postId, bob)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThatThrownBy(() -> posts.togglePin(postId, bob)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThatThrownBy(() -> posts.delete(Long.MAX_VALUE, bob)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> posts.togglePin(Long.MAX_VALUE, bob)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> posts.like(Long.MAX_VALUE, bob)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> groups.join(Long.MAX_VALUE, bob)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThat(postRows.findById(postId)).isPresent();
    }

    @Test
    @DisplayName("a new account on a recycled address inherits no post, like or membership")
    void recycledAddressDoesNotInheritOwnership() {
        Party first = identity.caller(token("user_first_cm_rc", "shared.cm@school.edu"));
        Long postId = post(first).getId();
        posts.like(postId, first);
        Long groupId = group(first).getId();

        Party second = identity.caller(token("user_second_cm_rc", "shared.cm@school.edu"));
        assertThatThrownBy(() -> posts.delete(postId, second)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThatThrownBy(() -> posts.togglePin(postId, second)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(seen(postId, second).isLikedByMe()).isFalse();
        assertThat(groups.myGroups(second)).isEmpty();
        assertThat(joined(groupId, second)).isFalse();
    }

    @Test
    @DisplayName("legacy address-only community rows are claimed by the caller and then owned")
    void legacyAddressOnlyRowsAreClaimed() {
        String address = "Legacy.CM@School.edu"; // stored with different case than the token
        Group group = groupRows.save(Group.builder().name("Old").type(GroupType.GENERAL).createdByEmail(address).build());
        memberships.save(GroupMembership.builder().groupId(group.getId()).userEmail(address).build());
        Post post = postRows.save(Post.builder().authorEmail(address).content("old post").build());
        likes.save(PostLike.builder().postId(post.getId()).userEmail(address).build());
        Comment comment = comments.save(Comment.builder().postId(post.getId()).authorEmail(address).content("hi").build());
        Event event = eventRows.save(Event.builder().title("Old").startsAt(Instant.now()).createdByEmail(address).build());

        Party legacy = identity.caller(token("user_legacy_cm", "legacy.cm@school.edu"));

        assertThat(groupRows.findById(group.getId()).orElseThrow().getCreatedBySubject()).isEqualTo("user_legacy_cm");
        assertThat(comments.findById(comment.getId()).orElseThrow().getAuthorSubject()).isEqualTo("user_legacy_cm");
        assertThat(eventRows.findById(event.getId()).orElseThrow().getCreatedBySubject()).isEqualTo("user_legacy_cm");
        assertThat(groups.myGroups(legacy)).extracting(GroupResponse::getId).containsExactly(group.getId());
        assertThat(seen(post.getId(), legacy).isLikedByMe()).isTrue();
        posts.togglePin(post.getId(), legacy);
        posts.delete(post.getId(), legacy);
        assertThat(postRows.findById(post.getId())).isEmpty();
    }

    @Test
    @DisplayName("claiming binds one membership and one like per parent instead of tripping the subject index")
    void claimBindsOneRowPerParent() {
        Group group = groupRows.save(Group.builder().name("Dup").type(GroupType.GENERAL)
                .createdByEmail("dup.cm@school.edu").build());
        Post post = postRows.save(Post.builder().authorEmail("dup.cm@school.edu").content("dup").build());
        // Two legacy rows per parent under case variants of one address (uk on email is case-sensitive).
        for (String address : new String[]{"dup.cm@school.edu", "DUP.cm@school.edu"}) {
            memberships.save(GroupMembership.builder().groupId(group.getId()).userEmail(address).build());
            likes.save(PostLike.builder().postId(post.getId()).userEmail(address).build());
        }

        Party dup = identity.caller(token("user_dup_cm", "dup.cm@school.edu"));
        assertThat(memberships.findAll()).filteredOn(m -> "user_dup_cm".equals(m.getUserSubject())).hasSize(1);
        assertThat(likes.findAll()).filteredOn(l -> "user_dup_cm".equals(l.getUserSubject())).hasSize(1);
        assertThat(groups.myGroups(dup)).hasSize(1);
    }

    @Test
    @DisplayName("the (group_id, user_subject) index rejects a second membership for one subject")
    void membershipSubjectIndexRejectsDuplicate() {
        Group group = groupRows.save(Group.builder().name("Uk").type(GroupType.GENERAL)
                .createdByEmail("uk.cm@school.edu").build());
        memberships.saveAndFlush(GroupMembership.builder().groupId(group.getId())
                .userEmail("uk1.cm@school.edu").userSubject("user_uk_cm").build());
        assertThatThrownBy(() -> memberships.saveAndFlush(GroupMembership.builder().groupId(group.getId())
                .userEmail("uk2.cm@school.edu").userSubject("user_uk_cm").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("the (post_id, user_subject) index rejects a second like for one subject")
    void likeSubjectIndexRejectsDuplicate() {
        Post post = postRows.save(Post.builder().authorEmail("uk.cm@school.edu").content("uk").build());
        likes.saveAndFlush(PostLike.builder().postId(post.getId())
                .userEmail("uk1.cm@school.edu").userSubject("user_uk_cm").build());
        assertThatThrownBy(() -> likes.saveAndFlush(PostLike.builder().postId(post.getId())
                .userEmail("uk2.cm@school.edu").userSubject("user_uk_cm").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("comments record the author's subject beside the address")
    void commentRecordsSubject() {
        Party alice = identity.caller(token("user_alice_cm_cc", "alice.cc@school.edu"));
        Long postId = post(alice).getId();
        Long commentId = posts.comment(postId, new CommentRequest("hi"), alice).getId();
        assertThat(comments.findById(commentId).orElseThrow().getAuthorSubject()).isEqualTo("user_alice_cm_cc");
    }
}
