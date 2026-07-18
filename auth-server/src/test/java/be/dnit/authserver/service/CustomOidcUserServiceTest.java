package be.dnit.authserver.service;

import be.dnit.authserver.model.UserEntity;
import be.dnit.authserver.model.UserEntity.Role;
import be.dnit.authserver.model.UserEntity.Status;
import be.dnit.authserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    private CustomOidcUserService service;
    private OidcUserRequest userRequest;
    private DefaultOidcUser oidcUser;

    @BeforeEach
    void setUp() {
        service = new CustomOidcUserService(userRepository, passwordEncoder);
        service.setDelegate(delegate);

        OidcIdToken idToken = OidcIdToken.withTokenValue("fake-id-token")
                .subject("google-sub-123")
                .issuer("https://accounts.google.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "google-sub-123")
                .claim("email", "test@example.com")
                .claim("name", "Test User")
                .claim("email_verified", true)
                .build();

        oidcUser = new DefaultOidcUser(List.of(), idToken);

        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("placeholder")
                .clientSecret("placeholder")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        userRequest = new OidcUserRequest(registration, accessToken, idToken);
    }

    @Test
    void firstCallCreatesPendingCustomer() {
        when(delegate.loadUser(userRequest)).thenReturn(oidcUser);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-random");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OidcUser result = service.loadUser(userRequest);

        assertNotNull(result);
        assertEquals("test@example.com", result.getAttribute("email"));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();
        assertEquals("test@example.com", saved.getEmail());
        assertEquals("Test User", saved.getName());
        assertEquals(Role.CUSTOMER, saved.getRole());
        assertEquals(Status.PENDING, saved.getStatus());
        assertNotNull(saved.getPassword());
        assertNotEquals("", saved.getPassword());
    }

    @Test
    void secondCallDoesNotDuplicateExistingUser() {
        UserEntity existing = new UserEntity(
                "test@example.com", "some-encoded-pw", "Test User", Role.CUSTOMER, Status.PENDING
        );

        when(delegate.loadUser(userRequest)).thenReturn(oidcUser);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existing));

        OidcUser result = service.loadUser(userRequest);

        assertNotNull(result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void secondCallDoesNotResetActiveUserRoleOrStatus() {
        UserEntity existing = new UserEntity(
                "test@example.com", "some-encoded-pw", "Test User", Role.ADMIN, Status.ACTIVE
        );

        when(delegate.loadUser(userRequest)).thenReturn(oidcUser);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existing));

        OidcUser result = service.loadUser(userRequest);

        assertNotNull(result);
        verify(userRepository, never()).save(any());
        assertEquals(Role.ADMIN, existing.getRole());
        assertEquals(Status.ACTIVE, existing.getStatus());
    }

    @Test
    void fallsBackToEmailWhenNameIsNull() {
        OidcIdToken idTokenNoName = OidcIdToken.withTokenValue("fake-id-token-2")
                .subject("google-sub-456")
                .issuer("https://accounts.google.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", "google-sub-456")
                .claim("email", "noname@example.com")
                .claim("email_verified", true)
                .build();

        DefaultOidcUser userNoName = new DefaultOidcUser(List.of(), idTokenNoName);

        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("placeholder")
                .clientSecret("placeholder")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token-value-2",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        OidcUserRequest requestNoName = new OidcUserRequest(registration, accessToken, idTokenNoName);

        when(delegate.loadUser(requestNoName)).thenReturn(userNoName);
        when(userRepository.findByEmail("noname@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded-random");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.loadUser(requestNoName);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals("noname@example.com", captor.getValue().getName());
    }
}
