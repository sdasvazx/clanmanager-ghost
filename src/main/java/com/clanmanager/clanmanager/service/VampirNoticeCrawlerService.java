package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.dto.VampirForumArticleListDto;
import com.clanmanager.clanmanager.entity.VampirNotice;
import com.clanmanager.clanmanager.repository.VampirNoticeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class VampirNoticeCrawlerService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final String USER_AGENT = "clanmanager-ghost/1.0 (Vampir notice monitor)";

    private final VampirNoticeRepository noticeRepository;
    private final RestTemplate restTemplate;
    private final String noticeApiUrl;

    public VampirNoticeCrawlerService(
            VampirNoticeRepository noticeRepository,
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.vampir-notice.api-url:https://forum.netmarble.com/api/game/thered/official/forum/vampir/article/list?rows=20&start=0&viewType=pv&menuSeq=2&sort=NEW}") String noticeApiUrl
    ) {
        this.noticeRepository = noticeRepository;
        this.noticeApiUrl = noticeApiUrl;
        this.restTemplate = restTemplateBuilder
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .build();
    }

    public List<VampirNotice> crawlNewNotices() {
        VampirForumArticleListDto response = restTemplate.getForObject(noticeApiUrl, VampirForumArticleListDto.class);
        if (response == null || response.code() == null || response.code() != 0 || response.articleList() == null) {
            throw new IllegalStateException("뱀피르 공지 API 응답이 올바르지 않습니다.");
        }

        List<VampirForumArticleListDto.Article> articles = response.articleList().stream()
                .filter(Objects::nonNull)
                .filter(article -> article.id() != null && article.regDate() != null)
                .toList();
        List<Long> articleIds = articles.stream().map(VampirForumArticleListDto.Article::id).toList();
        Set<Long> existingIds = new HashSet<>();
        noticeRepository.findAllById(articleIds).forEach(notice -> existingIds.add(notice.getArticleId()));

        List<VampirNotice> newNotices = articles.stream()
                .filter(article -> !existingIds.contains(article.id()))
                .map(this::toEntity)
                .toList();
        if (newNotices.isEmpty()) {
            return List.of();
        }
        return noticeRepository.saveAll(newNotices).stream()
                .sorted((left, right) -> right.getRegDate().compareTo(left.getRegDate()))
                .toList();
    }

    private VampirNotice toEntity(VampirForumArticleListDto.Article article) {
        return VampirNotice.builder()
                .articleId(article.id())
                .title(article.title() == null || article.title().isBlank() ? "제목 없는 공지" : article.title().trim())
                .content(article.content())
                .regDate(LocalDateTime.ofInstant(Instant.ofEpochMilli(article.regDate()), SEOUL_ZONE))
                .type(article.type())
                .thumbnailUrl(article.thumbnailUrl())
                .crawledAt(LocalDateTime.now())
                .build();
    }
}
