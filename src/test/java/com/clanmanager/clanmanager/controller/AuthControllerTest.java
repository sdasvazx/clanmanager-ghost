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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private AuthController controller(MemberRepository members, RefreshTokenRepository tokens) {
        JwtTokenProvider jwt = new JwtTokenProvider(
                "test-jwt-secret-that-is-definitely-longer-than-32-characters", Duration.ofMinutes(30), Duration.ofDays(30)
        );
        AuthController controller = new AuthController(members, tokens, jwt);
        ReflectionTestUtils.setField(controller, "secureCookie", false);
        ReflectionTestUtils.setField(controller, "cookieSameSite", "Strict");
        ReflectionTestUtils.setField(controller, "refreshCookieMaxAge", Duration.ofDays(30));
        return controller;
    }

    @Test
    void publicRegistrationWaitsForAdminApproval() {
        MemberRepository repository = mock(MemberRepository.class);
        when(repository.count()).thenReturn(10L);
        when(repository.save(any(Member.class))).thenAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            saved.setMemberId(207L);
            return saved;
        });

        RegisterRequestDto request = new RegisterRequestDto();
        request.setCharacterName("신규회원");
        request.setPassword(PasswordSupport.DEFAULT_INITIAL_PASSWORD);
        request.setCombatPower(1_000_000);

        Map<String, Object> response = controller(repository, mock(RefreshTokenRepository.class)).register(request);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(repository).save(captor.capture());
        assertThat(response.get("approvalPending")).isEqualTo(true);
        assertThat(captor.getValue().getActive()).isFalse();
        assertThat(captor.getValue().getStatus()).isEqualTo("가입승인대기");
    }

    @Test
    void loginIssuesAccessAndRefreshTokens() {
        MemberRepository repository = mock(MemberRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        Member member = Member.builder()
                .memberId(1L).characterName("테스트").password(PasswordSupport.encode(PasswordSupport.DEFAULT_INITIAL_PASSWORD))
                .mustChangePassword(false).role(MemberRole.MEMBER).active(true).build();
        when(repository.findByCharacterName("테스트")).thenReturn(Optional.of(member));
        when(repository.save(member)).thenReturn(member);

        LoginRequestDto request = new LoginRequestDto();
        request.setCharacterName("테스트");
        request.setPassword(PasswordSupport.DEFAULT_INITIAL_PASSWORD);
        request.setRememberMe(true);
        HttpServletResponse servletResponse = mock(HttpServletResponse.class);

        ResponseEntity<LoginResponseDto> response = controller(repository, tokens).login(
                request, mock(jakarta.servlet.http.HttpServletRequest.class), servletResponse
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().mustChangePassword()).isTrue();
        verify(tokens).save(any());
        verify(servletResponse).addHeader(eq("Set-Cookie"), contains("HttpOnly"));
    }

    @Test
    void loginWithoutRememberMeDoesNotCreateRefreshTokenAndDeletesExistingCookie() {
        MemberRepository repository = mock(MemberRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        Member member = Member.builder()
                .memberId(2L).characterName("no-remember").password(PasswordSupport.encode("password"))
                .mustChangePassword(false).role(MemberRole.MEMBER).active(true).build();
        when(repository.findByCharacterName("no-remember")).thenReturn(Optional.of(member));

        LoginRequestDto request = new LoginRequestDto();
        request.setCharacterName("no-remember");
        request.setPassword("password");
        request.setRememberMe(false);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getCookies()).thenReturn(null);
        HttpServletResponse servletResponse = mock(HttpServletResponse.class);

        controller(repository, tokens).login(request, servletRequest, servletResponse);

        verify(tokens, never()).save(any());
        verify(servletResponse).addHeader(eq("Set-Cookie"), contains("Max-Age=0"));
    }

    @Test
    void logoutRevokesStoredRefreshTokenAndDeletesCookie() {
        MemberRepository repository = mock(MemberRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        JwtTokenProvider jwt = new JwtTokenProvider(
                "test-jwt-secret-that-is-definitely-longer-than-32-characters", Duration.ofMinutes(30), Duration.ofDays(30)
        );
        Member member = Member.builder().memberId(3L).role(MemberRole.MEMBER).active(true).build();
        String rawToken = jwt.createRefreshToken(member);
        RefreshToken stored = RefreshToken.builder()
                .memberId(3L).tokenHash(jwt.hash(rawToken)).expiryDate(Instant.now().plus(Duration.ofDays(30))).revoked(false).build();
        when(tokens.findByTokenHash(jwt.hash(rawToken))).thenReturn(Optional.of(stored));
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getCookies()).thenReturn(new Cookie[]{new Cookie("refreshToken", rawToken)});
        HttpServletResponse servletResponse = mock(HttpServletResponse.class);

        controller(repository, tokens).logout(servletRequest, servletResponse);

        assertThat(stored.isRevoked()).isTrue();
        verify(servletResponse).addHeader(eq("Set-Cookie"), contains("Max-Age=0"));
    }
}
