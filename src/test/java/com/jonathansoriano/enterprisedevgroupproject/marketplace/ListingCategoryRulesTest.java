package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import com.jonathansoriano.enterprisedevgroupproject.marketplace.dto.ListingRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ListingCategoryRulesTest {

    @Test
    void onlyBooksElectronicsAndOtherCanBeRented() {
        assertTrue(ListingCategory.BOOKS.allows(ListingType.RENT));
        assertTrue(ListingCategory.ELECTRONICS.allows(ListingType.RENT));
        assertTrue(ListingCategory.OTHER.allows(ListingType.RENT));
        assertFalse(ListingCategory.CLOTHES.allows(ListingType.RENT));
        assertFalse(ListingCategory.EVENT_TICKETS.allows(ListingType.RENT));
        assertFalse(ListingCategory.FURNITURE.allows(ListingType.RENT));
    }

    @Test
    void everyCategoryAllowsSellFreeAndLookingFor() {
        for (ListingCategory category : ListingCategory.values()) {
            assertTrue(category.allows(ListingType.SELL), category.name());
            assertTrue(category.allows(ListingType.FREE), category.name());
            assertTrue(category.allows(ListingType.LOOKING_FOR), category.name());
        }
    }

    @Test
    void create_RejectsFurnitureForRent_Http400_WithoutSaving() {
        ListingRepository repository = mock(ListingRepository.class);
        ListingService service = new ListingService(repository,
                mock(ListingFavoriteRepository.class), mock(ListingReportRepository.class));
        ListingRequest request = new ListingRequest();
        request.setTitle("Couch");
        request.setCategory(ListingCategory.FURNITURE);
        request.setListingType(ListingType.RENT);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> service.create(request, null));
        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        verifyNoInteractions(repository);
    }
}
