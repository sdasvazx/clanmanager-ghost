package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inventoryItemId;

    @Column(nullable = false, length = 80)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 80)
    private String location;

    @Column(length = 255)
    private String memo;

    private Long requestedByMemberId;

    @Column(length = 50)
    private String requestedByName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.quantity = this.quantity == null ? 0 : this.quantity;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
