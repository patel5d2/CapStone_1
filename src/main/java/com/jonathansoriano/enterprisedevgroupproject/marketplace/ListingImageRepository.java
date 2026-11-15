package com.jonathansoriano.enterprisedevgroupproject.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingImageRepository
        extends JpaRepository<ListingImage, String> {
}

