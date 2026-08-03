package com.clanmanager.clanmanager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VampirForumArticleListDto(
        Integer code,
        String msg,
        List<Article> articleList,
        Long totalCount
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Article(
            Long id,
            String title,
            String content,
            Long regDate,
            String type,
            String thumbnailUrl,
            Long viewCount
    ) {
    }
}
