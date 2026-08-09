package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.LoginRequestDto;
import com.clanmanager.clanmanager.dto.LoginResponseDto;
import com.clanmanager.clanmanager.dto.RegisterRequestDto;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.entity.RefreshToken;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.repository.RefreshTokenRepository;
import com.clanmanager.clanmanager.security.JwtTokenProvider;
import com.clanmanager.clanmanager.security.PasswordSupport;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REGISTRATION_PENDING_STATUS = "가입승인대기";

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${auth.cookie.secure:true}")
    private boolean secureCookie;

    @Value("${auth.cookie.same-site:None}")
    private String cookieSameSite;

    @Value("${auth.cookie.max-age:P30D}")
    private Duration refreshCookieMaxAge;

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequestDto request) {
        if (memberRepository.existsByCharacterName(request.getCharacterName())) {
            throw new IllegalArgumentException("이미 등록된 캐릭터 이름입니다.");
        }

        boolean bootstrapAdmin = memberRepository.count() == 0;
        Member savedMember = memberRepository.save(Member.builder()
                .characterName(request.getCharacterName())
                .password(PasswordSupport.encode(PasswordSupport.DEFAULT_INITIAL_PASSWORD))
                .mustChangePassword(true)
                .combatPower(request.getCombatPower())
                .status(bootstrapAdmin ? "활동중" : REGISTRATION_PENDING_STATUS)
                .role(bootstrapAdmin ? MemberRole.ADMIN : MemberRole.MEMBER)
                .active(bootstrapAdmin)
                .build());

        return Map.of(
                "message", bootstrapAdmin ? "관리자 계정 생성 완료" : "회원가입 신청이 접수되었습니다. 운영자 승인 후 로그인할 수 있습니다.",
                "memberId", savedMember.getMemberId(),
                "characterName", savedMember.getCharacterName(),
                "role", savedMember.getRole().name(),
                "mustChangePassword", Boolean.TRUE.equals(savedMember.getMustChangePassword()),
                "approvalPending", !bootstrapAdmin
        );
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<LoginResponseDto> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        Member member = memberRepository.findByCharacterName(request.getCharacterName())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 캐릭터입니다."));

        if (!PasswordSupport.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (Boolean.FALSE.equals(member.getActive())) {
            if (REGISTRATION_PENDING_STATUS.equals(member.getStatus())) {
                throw new IllegalArgumentException("회원가입 승인 대기 중입니다. 운영자 승인 후 로그인해 주세요.");
            }
            throw new IllegalArgumentException("비활성화된 계정입니다. 운영진에게 문의해 주세요.");
        }

        boolean usesInitialPassword = PasswordSupport.matches(PasswordSupport.DEFAULT_INITIAL_PASSWORD, member.getPassword());
        boolean memberChanged = false;
        if (!PasswordSupport.isEncoded(member.getPassword())) {
            member.setPassword(PasswordSupport.encode(request.getPassword()));
            memberChanged = true;
        }
        if (usesInitialPassword && !Boolean.TRUE.equals(member.getMustChangePassword())) {
            member.setMustChangePassword(true);
            memberChanged = true;
        }
        if (memberChanged) memberRepository.save(member);

        if (Boolean.TRUE.equals(request.getRememberMe())) {
            String refreshToken = issueRefreshToken(member);
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, refreshCookieMaxAge).toString());
        } else {
            revokeCookieToken(httpRequest);
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        }
        return ResponseEntity.ok(loginResponse(member));
    }

    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<LoginResponseDto> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = readRefreshCookie(request);
        if (rawToken == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            Long memberId = jwtTokenProvider.validateAndGetMemberId(rawToken, "refresh");
            RefreshToken saved = refreshTokenRepository.findByTokenHash(jwtTokenProvider.hash(rawToken)).orElseThrow();
            if (saved.isRevoked() || saved.getExpiryDate().isBefore(Instant.now()) || !saved.getMemberId().equals(memberId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Member member = memberRepository.findById(memberId).orElseThrow();
            if (!Boolean.TRUE.equals(member.getActive())) {
                saved.setRevoked(true);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Rotation prevents replay of a refresh token that has already been used.
            saved.setRevoked(true);
            String rotated = issueRefreshToken(member);
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(rotated, refreshCookieMaxAge).toString());
            return ResponseEntity.ok(loginResponse(member));
        } catch (RuntimeException exception) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        revokeCookieToken(request);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    private String issueRefreshToken(Member member) {
        String rawToken = jwtTokenProvider.createRefreshToken(member);
        refreshTokenRepository.save(RefreshToken.builder()
                .memberId(member.getMemberId())
                .tokenHash(jwtTokenProvider.hash(rawToken))
                .expiryDate(jwtTokenProvider.getExpiration(rawToken))
                .revoked(false)
                .build());
        return rawToken;
    }

    private LoginResponseDto loginResponse(Member member) {
        return new LoginResponseDto(
                jwtTokenProvider.createAccessToken(member), member.getMemberId(), member.getCharacterName(),
                member.getRole().name(), Boolean.TRUE.equals(member.getMustChangePassword())
        );
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("refreshToken".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void revokeCookieToken(HttpServletRequest request) {
        String rawToken = readRefreshCookie(request);
        if (rawToken != null) {
            refreshTokenRepository.findByTokenHash(jwtTokenProvider.hash(rawToken))
                    .ifPresent(token -> token.setRevoked(true));
        }
    }
}
