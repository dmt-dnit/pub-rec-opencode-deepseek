package be.dnit.authserver.service;

import be.dnit.authserver.model.UserEntity;
import be.dnit.authserver.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private OAuth2UserService<OidcUserRequest, OidcUser> delegate = new OidcUserService();

    public CustomOidcUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    void setDelegate(OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = delegate.loadUser(userRequest);

        String email = oidcUser.getAttribute("email");
        String name = oidcUser.getAttribute("name");

        userRepository.findByEmail(email).orElseGet(() -> userRepository.save(new UserEntity(
                email,
                passwordEncoder.encode(UUID.randomUUID().toString()),
                name != null ? name : email,
                UserEntity.Role.CUSTOMER,
                UserEntity.Status.PENDING
        )));

        return oidcUser;
    }
}
