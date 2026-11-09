package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.InventoryRequestCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRequestCheckRepository extends JpaRepository<InventoryRequestCheck, Long> {
    List<InventoryRequestCheck> findAllByOrderByCreatedAtAsc();
    Optional<InventoryRequestCheck> findByInventoryItem_InventoryItemIdAndMember_MemberId(Long inventoryItemId, Long memberId);
    void deleteAllByInventoryItem_InventoryItemId(Long inventoryItemId);
}
