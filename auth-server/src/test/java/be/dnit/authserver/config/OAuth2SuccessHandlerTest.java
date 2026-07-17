package be.dnit.authserver.config;

import be.dnit.authserver.model.UserEntity;
import be.dnit.authserver.repository.UserRepository;
import be.dnit.authserver.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;

    private OAuth2SuccessHandler handler;
    private final String redirectBase = "http://localhost:4200";

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(userRepository, jwtService, redirectBase);
    }

    @Test
    void activeUserRedirectsWithToken() throws Exception {
        OAuth2User principal = new DefaultOAuth2User(
                List.of(), Map.of("email", "active@example.com", "sub", "google-sub-1"), "sub");
        when(authentication.getPrincipal()).thenReturn(principal);

        UserEntity user = new UserEntity(
                "active@example.com", "pw", "Active User",
                UserEntity.Role.CUSTOMER, UserEntity.Status.ACTIVE);
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("eyJ…fake.token");

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertEquals("http://localhost:4200/login?oauth2=success&token=eyJ…fake.token",
                redirectCaptor.getValue());
    }

    @Test
    void pendingUserRedirectsWithoutToken() throws Exception {
        OAuth2User principal = new DefaultOAuth2User(
                List.of(), Map.of("email", "pending@example.com", "sub", "google-sub-2"), "sub");
        when(authentication.getPrincipal()).thenReturn(principal);

        UserEntity user = new UserEntity(
                "pending@example.com", "pw", "Pending User",
                UserEntity.Role.CUSTOMER, UserEntity.Status.PENDING);
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(user));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertEquals("http://localhost:4200/login?oauth2=pending",
                redirectCaptor.getValue());
        verifyNoInteractions(jwtService);
    }

    @Test
    void disabledUserRedirectsWithError() throws Exception {
        OAuth2User principal = new DefaultOAuth2User(
                List.of(), Map.of("email", "disabled@example.com", "sub", "google-sub-3"), "sub");
        when(authentication.getPrincipal()).thenReturn(principal);

        UserEntity user = new UserEntity(
                "disabled@example.com", "pw", "Disabled User",
                UserEntity.Role.CUSTOMER, UserEntity.Status.DISABLED);
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertEquals("http://localhost:4200/login?oauth2=error",
                redirectCaptor.getValue());
        verifyNoInteractions(jwtService);
    }

    @Test
    void throwsWhenUserNotFound() {
        OAuth2User principal = new DefaultOAuth2User(
                List.of(), Map.of("email", "ghost@example.com", "sub", "google-sub-4"), "sub");
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
                handler.onAuthenticationSuccess(request, response, authentication));
    }
}
