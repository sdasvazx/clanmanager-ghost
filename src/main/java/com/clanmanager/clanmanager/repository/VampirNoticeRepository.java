package com.clanmanager.clanmanager.repository;

import com.clanmanager.clanmanager.entity.VampirNotice;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VampirNoticeRepository extends JpaRepository<VampirNotice, Long> {
    List<VampirNotice> findAllByOrderByRegDateDesc(Pageable pageable);
}
