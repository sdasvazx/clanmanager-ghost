package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.dto.VampirNoticeResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class VampirNoticeSseService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("connected", true)));
        } catch (IOException exception) {
            emitters.remove(emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void broadcastNewNotices(List<VampirNoticeResponseDto> notices) {
        if (notices == null || notices.isEmpty()) {
            return;
        }
        // 단일 서버용 emitter 목록입니다. 서버를 확장할 때는 Redis Pub/Sub 등으로 인스턴스 간 이벤트를 공유해야 합니다.
        emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("newNotice").data(notices));
            } catch (IOException | IllegalStateException exception) {
                emitters.remove(emitter);
                emitter.complete();
            }
        });
    }
}
