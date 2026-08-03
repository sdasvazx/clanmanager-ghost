package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.dto.VampirNoticeResponseDto;
import com.clanmanager.clanmanager.entity.VampirNotice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VampirNoticeScheduler {

    private final VampirNoticeCrawlerService crawlerService;
    private final VampirNoticeSseService sseService;

    @Scheduled(
            fixedDelayString = "${app.vampir-notice.poll-interval-ms:600000}",
            initialDelayString = "${app.vampir-notice.initial-delay-ms:15000}"
    )
    public void refreshNotices() {
        try {
            List<VampirNotice> newNotices = crawlerService.crawlNewNotices();
            if (!newNotices.isEmpty()) {
                sseService.broadcastNewNotices(newNotices.stream().map(VampirNoticeResponseDto::from).toList());
                log.info("Saved and broadcast {} new Vampir notices", newNotices.size());
            }
        } catch (Exception exception) {
            log.warn("Failed to refresh Vampir notices: {}", exception.getMessage());
        }
    }
}
