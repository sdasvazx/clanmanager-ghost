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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping
    @Transactional
    public ClanSettingRow addClan(@Valid @RequestBody ClanSettingCreateRequest request) {
        requireAdmin(request.getAdminMemberId());
        String name = request.getName().trim();
        validateName(name);
        if (clanSettingRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("이미 등록된 클랜명입니다: " + name);
        }
        int nextOrder = clanSettingRepository.findAllByOrderByDisplayOrderAscClanSettingIdAsc().stream()
                .map(ClanSetting::getDisplayOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        String color = normalizeColor(request.getColor());
        return ClanSettingRow.from(clanSettingRepository.save(ClanSetting.builder()
                .name(name)
                .color(color)
                .displayOrder(nextOrder)
                .build()));
    }

    @DeleteMapping("/{clanSettingId}")
    @Transactional
    public List<ClanSettingRow> deleteClan(
            @PathVariable Long clanSettingId,
            @RequestParam Long adminMemberId
    ) {
        requireAdmin(adminMemberId);
        ClanSetting target = clanSettingRepository.findById(clanSettingId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 클랜을 찾을 수 없습니다."));
        if (clanSettingRepository.count() <= 1) {
            throw new IllegalArgumentException("클랜 목록은 최소 1개 이상 필요합니다.");
        }
        clanSettingRepository.delete(target);
        clanSettingRepository.flush();
        List<ClanSetting> remaining = clanSettingRepository.findAllByOrderByDisplayOrderAscClanSettingIdAsc();
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setDisplayOrder(index + 1);
        }
        return clanSettingRepository.saveAll(remaining).stream().map(ClanSettingRow::from).toList();
    }

    @PutMapping
    @Transactional
    public List<ClanSettingRow> saveClans(@Valid @RequestBody ClanSettingSaveRequest request) {
        requireAdmin(request.getAdminMemberId());
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
            validateName(name);
            if (!names.add(name.toLowerCase())) throw new IllegalArgumentException("중복 클랜명은 저장할 수 없습니다: " + name);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("클랜명은 비워둘 수 없습니다.");
        if ("총합".equals(name)) throw new IllegalArgumentException("총합은 클랜명으로 사용할 수 없습니다.");
    }

    private Member requireAdmin(Long adminMemberId) {
        Member admin = memberRepository.findById(adminMemberId)
                .orElseThrow(() -> new IllegalArgumentException("운영자를 찾을 수 없습니다."));
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 클랜 목록을 변경할 수 있습니다.");
        }
        return admin;
    }

    private String normalizeColor(String color) {
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : "#3b82f6";
    }

    private void saveRows(List<ClanSettingRow> rows) {
        List<ClanSetting> entities = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            ClanSettingRow row = rows.get(index);
            String color = normalizeColor(row.color());
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

    @Getter
    @Setter
    public static class ClanSettingCreateRequest {
        @NotNull
        private Long adminMemberId;

        @NotBlank
        private String name;

        private String color;
    }
}
