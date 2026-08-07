package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.ClanSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClanSettingRepository extends JpaRepository<ClanSetting, Long> {
    List<ClanSetting> findAllByOrderByDisplayOrderAscClanSettingIdAsc();
}
