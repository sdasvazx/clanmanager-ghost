# 뱀피르 공식 공지 SSE 운영 메모

- 공지 목록은 넷마블 포럼 JSON API를 10분마다 한 번 조회합니다. 내부 API이므로 폴링 주기를 더 짧게 낮추지 않습니다.
- 백엔드 구독 주소는 `/api/notice/subscribe`, 이벤트 이름은 `newNotice`입니다.
- 현재 `CopyOnWriteArrayList<SseEmitter>` 방식은 백엔드 서버 1대 기준입니다. 서버를 여러 대로 늘리면 Redis Pub/Sub 등으로 이벤트를 공유해야 합니다.
- EventSource는 연결이 끊기면 브라우저가 자동으로 재연결합니다.

Nginx 같은 리버스 프록시를 앞에 둘 경우 SSE 응답이 버퍼에 쌓이지 않도록 아래처럼 버퍼링을 꺼야 합니다.

```nginx
location /api/notice/subscribe {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 1h;
}
```
