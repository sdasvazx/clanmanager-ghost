package com.clanmanager.clanmanager.dto;

public record LoginResponseDto(
        String accessToken,
        Long memberId,
        String characterName,
        String role,
        boolean mustChangePassword
) {
}

