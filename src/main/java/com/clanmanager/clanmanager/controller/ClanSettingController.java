package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.entity.ClanSetting;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.repository.ClanSettingRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/settings/clans")
@RequiredArgsConstructor
public class ClanSettingController {
    private static final List<ClanSettingRow> DEFAULT_CLANS = List.of(
            new ClanSettingRow(null, "귀신", "#dc2626", 1),
            new ClanSettingRow(null, "귀신Z", "#10b981", 2),
            new ClanSettingRow(null, "감각", "#3b82f6", 3)
    );

    private final ClanSettingRepository clanSettingRepository;
    private final MemberRepository memberRepository;

    @GetMapping
    @Transactional
    public List<ClanSettingRow> getClans() {
        if (clanSettingRepository.count() == 0) {
            saveRows(DEFAULT_CLANS);
        }
        return clanSettingRepository.findAllByOrderByDisplayOrderAscClanSettingIdAsc().stream()
                .map(ClanSettingRow::from)
                .toList();
    }

    @PutMapping
    @Transactional
    public List<ClanSettingRow> saveClans(@Valid @RequestBody ClanSettingSaveRequest request) {
        Member admin = memberRepository.findById(request.getAdminMemberId())
                .orElseThrow(() -> new IllegalArgumentException("운영자를 찾을 수 없습니다."));
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 클랜 목록을 변경할 수 있습니다.");
        }
        validateRows(request.getClans());
        clanSettingRepository.deleteAllInBatch();
        saveRows(request.getClans());
        return clanSettingRepository.findAllByOrderByDisplayOrderAscClanSettingIdAsc().stream()
                .map(ClanSettingRow::from)
                .toList();
    }

    private void validateRows(List<ClanSettingRow> rows) {
        Set<String> names = new HashSet<>();
        for (ClanSettingRow row : rows) {
            String name = row.name() == null ? "" : row.name().trim();
            if (name.isBlank()) throw new IllegalArgumentException("클랜명은 비워둘 수 없습니다.");
            if (!names.add(name.toLowerCase())) throw new IllegalArgumentException("중복 클랜명은 저장할 수 없습니다: " + name);
            if ("총합".equals(name)) throw new IllegalArgumentException("총합은 클랜명으로 사용할 수 없습니다.");
        }
    }

    private void saveRows(List<ClanSettingRow> rows) {
        List<ClanSetting> entities = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            ClanSettingRow row = rows.get(index);
            String color = row.color() != null && row.color().matches("#[0-9a-fA-F]{6}") ? row.color() : "#3b82f6";
            entities.add(ClanSetting.builder()
                    .name(row.name().trim())
                    .color(color)
                    .displayOrder(index + 1)
                    .build());
        }
        clanSettingRepository.saveAll(entities);
    }

    public record ClanSettingRow(Long id, @NotBlank String name, String color, Integer displayOrder) {
        static ClanSettingRow from(ClanSetting setting) {
            return new ClanSettingRow(setting.getClanSettingId(), setting.getName(), setting.getColor(), setting.getDisplayOrder());
        }
    }

    @Getter
    @Setter
    public static class ClanSettingSaveRequest {
        @NotNull
        private Long adminMemberId;

        @NotEmpty
        @Valid
        private List<ClanSettingRow> clans;
    }
}
