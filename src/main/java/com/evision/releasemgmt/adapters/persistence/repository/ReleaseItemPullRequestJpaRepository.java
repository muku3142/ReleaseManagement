package com.evision.releasemgmt.adapters.persistence.repository;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseItemPullRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReleaseItemPullRequestJpaRepository extends JpaRepository<ReleaseItemPullRequestEntity, UUID> {

    List<ReleaseItemPullRequestEntity> findByItemIdIn(Collection<UUID> itemIds);

    // Bulk JPQL so a re-save never loads rows one by one.
    @Modifying(flushAutomatically = true)
    @Query("delete from ReleaseItemPullRequestEntity e where e.itemId = :itemId")
    void deleteByItemId(@Param("itemId") UUID itemId);
}
