package com.jonathansoriano.enterprisedevgroupproject.image;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {
    Optional<ImageAsset> findByUrl(String url);
    List<ImageAsset> findByStudentId(Long studentId);
    List<ImageAsset> findTop50ByDeleteAfterLessThanEqualOrderByDeleteAfterAsc(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ImageAsset a where a.id = :id")
    Optional<ImageAsset> lockById(String id);
}
