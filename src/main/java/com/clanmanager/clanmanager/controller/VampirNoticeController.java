package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.VampirNoticeResponseDto;
import com.clanmanager.clanmanager.repository.VampirNoticeRepository;
import com.clanmanager.clanmanager.service.VampirNoticeSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class VampirNoticeController {

    private final VampirNoticeRepository noticeRepository;
    private final VampirNoticeSseService sseService;

    @GetMapping
    public List<VampirNoticeResponseDto> getLatestNotices(@RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(20, Math.max(1, limit));
        return noticeRepository.findAllByOrderByRegDateDesc(PageRequest.of(0, safeLimit)).stream()
                .map(VampirNoticeResponseDto::from)
                .toList();
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return sseService.subscribe();
    }
}
