package com.clanmanager.clanmanager.dto;

import com.clanmanager.clanmanager.entity.VampirNotice;

import java.time.LocalDateTime;

public record VampirNoticeResponseDto(
        Long articleId,
        String title,
        String content,
        LocalDateTime regDate,
        String type,
        String thumbnailUrl,
        LocalDateTime crawledAt,
        String articleUrl
) {
    private static final String ARTICLE_URL_PREFIX = "https://forum.netmarble.com/vampir/view/";

    public static VampirNoticeResponseDto from(VampirNotice notice) {
        return new VampirNoticeResponseDto(
                notice.getArticleId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getRegDate(),
                notice.getType(),
                notice.getThumbnailUrl(),
                notice.getCrawledAt(),
                ARTICLE_URL_PREFIX + notice.getArticleId()
        );
    }
}
