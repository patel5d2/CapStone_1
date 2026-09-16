package com.jonathansoriano.enterprisedevgroupproject;

import com.jonathansoriano.enterprisedevgroupproject.community.GroupService;
import com.jonathansoriano.enterprisedevgroupproject.community.GroupType;
import com.jonathansoriano.enterprisedevgroupproject.community.PostService;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.GroupRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.PostResponse;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingCategory;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingService;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.ListingType;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.MessagingService;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.ConversationResponse;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.SendMessageRequest;
import com.jonathansoriano.enterprisedevgroupproject.messages.dto.StartConversationRequest;
import com.jonathansoriano.enterprisedevgroupproject.support.SupportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises each feature area against a real (in-memory) database rather than
 * mocks. The toggles in particular — unfavorite, unlike, leave, unblock — run
 * Spring Data derived delete queries, which fail at runtime without an active
 * transaction, so a mocked test would not catch a regression there.
 */
@SpringBootTest
class FeatureFlowsIntegrationTest {

    private static final String SELLER = "seller@mail.uc.edu";
    private static final String BUYER = "buyer@xavier.edu";

    @Autowired
    private ListingService listingService;
    @Autowired
    private PostService postService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private MessagingService messagingService;
    @Autowired
    private SupportService supportService;

    @Test
    void listing_createSearchUpdateFavoriteAndDelete() {
        ListingResponse created = listingService.create(ListingRequest.builder()
                .title("Calculus textbook")
                .description("8th edition, light notes")
                .category(ListingCategory.BOOKS)
                .listingType(ListingType.SELL)
                .price(new java.math.BigDecimal("40.00"))
                .courseCode("MATH 1061")
                .build(), SELLER);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus().name()).isEqualTo("AVAILABLE");

        // Search by course code, the textbook path the plan calls for
        assertThat(listingService.search(null, null, null, null, "MATH 1061", null, BUYER))
                .extracting(ListingResponse::getId)
                .contains(created.getId());

        // Editing is restricted to the seller
        ListingRequest edit = ListingRequest.builder()
                .title("Calculus textbook (price drop)")
                .category(ListingCategory.BOOKS)
                .listingType(ListingType.SELL)
                .price(new java.math.BigDecimal("25.00"))
                .build();
        assertThatThrownBy(() -> listingService.update(created.getId(), edit, BUYER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThat(listingService.update(created.getId(), edit, SELLER).getTitle())
                .isEqualTo("Calculus textbook (price drop)");

        // Favorite toggles both ways — unfavorite is the derived delete query
        listingService.favorite(created.getId(), BUYER);
        assertThat(listingService.get(created.getId(), BUYER).isFavorited()).isTrue();
        assertThat(listingService.myFavorites(BUYER)).extracting(ListingResponse::getId).contains(created.getId());

        listingService.unfavorite(created.getId(), BUYER);
        assertThat(listingService.get(created.getId(), BUYER).isFavorited()).isFalse();
        assertThat(listingService.myFavorites(BUYER)).extracting(ListingResponse::getId).doesNotContain(created.getId());

        listingService.markSold(created.getId(), SELLER);
        assertThat(listingService.get(created.getId(), SELLER).getStatus().name()).isEqualTo("SOLD");

        listingService.delete(created.getId(), SELLER);
        assertThatThrownBy(() -> listingService.get(created.getId(), SELLER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void post_likeUnlikeCommentAndPin() {
        PostResponse post = postService.create(PostRequest.builder().content("Anyone else in CS 2021?").build(), SELLER);

        postService.like(post.getId(), BUYER);
        assertThat(findPost(post.getId(), BUYER).getLikeCount()).isEqualTo(1);
        assertThat(findPost(post.getId(), BUYER).isLikedByMe()).isTrue();

        // Derived delete query: fails without a transaction
        postService.unlike(post.getId(), BUYER);
        assertThat(findPost(post.getId(), BUYER).getLikeCount()).isZero();
        assertThat(findPost(post.getId(), BUYER).isLikedByMe()).isFalse();

        postService.comment(post.getId(), new com.jonathansoriano.enterprisedevgroupproject.community.dto.CommentRequest("Me!"), BUYER);
        assertThat(postService.listComments(post.getId())).hasSize(1);
        assertThat(findPost(post.getId(), BUYER).getCommentCount()).isEqualTo(1);

        // Pinning is author-only
        assertThatThrownBy(() -> postService.togglePin(post.getId(), BUYER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        postService.togglePin(post.getId(), SELLER);
        assertThat(findPost(post.getId(), SELLER).isPinned()).isTrue();

        postService.delete(post.getId(), SELLER);
    }

    @Test
    void group_joinLeaveAndMyGroups() {
        var group = groupService.create(GroupRequest.builder()
                .name("CS 2021 Study Group")
                .type(GroupType.COURSE_STUDY)
                .relatedValue("CS 2021")
                .build(), SELLER);

        // The creator is enrolled automatically
        assertThat(groupService.myGroups(SELLER)).extracting("id").contains(group.getId());

        groupService.join(group.getId(), BUYER);
        assertThat(groupService.myGroups(BUYER)).extracting("id").contains(group.getId());
        assertThat(groupService.search(null, null, null, BUYER))
                .filteredOn(g -> g.getId().equals(group.getId()))
                .allMatch(g -> g.isJoined() && g.getMemberCount() == 2);

        // Derived delete query: fails without a transaction
        groupService.leave(group.getId(), BUYER);
        assertThat(groupService.myGroups(BUYER)).extracting("id").doesNotContain(group.getId());
    }

    @Test
    void messaging_startFromListingSendReadBlockAndUnblock() {
        ListingResponse listing = listingService.create(ListingRequest.builder()
                .title("Mini fridge")
                .category(ListingCategory.FURNITURE)
                .listingType(ListingType.SELL)
                .price(new java.math.BigDecimal("50.00"))
                .build(), SELLER);

        ConversationResponse conversation = messagingService.startConversation(
                StartConversationRequest.builder().listingId(listing.getId()).build(), BUYER);

        assertThat(conversation.getParticipantEmails()).containsExactlyInAnyOrder(SELLER, BUYER);
        assertThat(conversation.getListingId()).isEqualTo(listing.getId());

        // Starting again reuses the same thread instead of piling up duplicates
        ConversationResponse again = messagingService.startConversation(
                StartConversationRequest.builder().listingId(listing.getId()).build(), BUYER);
        assertThat(again.getId()).isEqualTo(conversation.getId());

        messagingService.sendMessage(conversation.getId(), new SendMessageRequest("Is this still available?", null), BUYER);
        assertThat(messagingService.listMessages(conversation.getId(), SELLER)).hasSize(1);

        // The seller sees it as unread until they open the thread
        assertThat(unreadFor(SELLER, conversation.getId())).isEqualTo(1);
        messagingService.markRead(conversation.getId(), SELLER);
        assertThat(unreadFor(SELLER, conversation.getId())).isZero();

        // Outsiders cannot read someone else's conversation
        assertThatThrownBy(() -> messagingService.listMessages(conversation.getId(), "stranger@nku.edu"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        // Blocking stops further messages, and unblock (derived delete) restores them
        messagingService.block(SELLER, BUYER);
        assertThat(messagingService.listBlocked(SELLER)).contains(BUYER);
        assertThatThrownBy(() ->
                messagingService.sendMessage(conversation.getId(), new SendMessageRequest("Hello?", null), BUYER))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        messagingService.unblock(SELLER, BUYER);
        assertThat(messagingService.listBlocked(SELLER)).doesNotContain(BUYER);
        assertThat(messagingService.sendMessage(conversation.getId(), new SendMessageRequest("Still there?", null), BUYER))
                .isNotNull();

        listingService.delete(listing.getId(), SELLER);
    }

    @Test
    void support_resourcesAreSeededAndRequestsStayAnonymous() {
        // The seeder fills in each school's pantry, emergency aid, and counseling
        assertThat(supportService.listResources(null)).isNotEmpty();

        var created = supportService.createRequest(
                com.jonathansoriano.enterprisedevgroupproject.support.dto.AnonymousRequestRequest.builder()
                        .category(com.jonathansoriano.enterprisedevgroupproject.support.SupportCategory.FOOD_PANTRY)
                        .description("Could use groceries this week")
                        .build(), BUYER);

        assertThat(created.getStatus().name()).isEqualTo("OPEN");

        // The public list carries no requester identity — that is what makes it anonymous
        List<?> publicRequests = supportService.listPublicRequests(null,
                com.jonathansoriano.enterprisedevgroupproject.support.RequestStatus.OPEN);
        assertThat(publicRequests).isNotEmpty();
        assertThat(publicRequests.get(0).getClass().getDeclaredFields())
                .noneMatch(field -> field.getName().toLowerCase().contains("email"));

        // Only the requester sees it under "my requests"
        assertThat(supportService.listMine(BUYER)).extracting("id").contains(created.getId());
        assertThat(supportService.listMine(SELLER)).extracting("id").doesNotContain(created.getId());

        supportService.fulfill(created.getId());
        assertThat(supportService.listMine(BUYER))
                .filteredOn(r -> r.getId().equals(created.getId()))
                .allMatch(r -> r.getStatus().name().equals("FULFILLED"));
    }

    private PostResponse findPost(Long postId, String viewerEmail) {
        return postService.list(null, viewerEmail).stream()
                .filter(p -> p.getId().equals(postId))
                .findFirst()
                .orElseThrow();
    }

    private int unreadFor(String userEmail, Long conversationId) {
        return messagingService.listConversations(userEmail).stream()
                .filter(c -> c.getId().equals(conversationId))
                .findFirst()
                .map(ConversationResponse::getUnreadCount)
                .orElseThrow();
    }
}
