package com.clanmanager.clanmanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "combat_power_update_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CombatPowerUpdateSetting {
    @Id
    private Long settingId;

    @Column(nullable = false)
    private Boolean enabled;
}
