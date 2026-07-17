package be.dnit.authserver.service;

import be.dnit.authserver.model.UserEntity;
import be.dnit.authserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Autowired
    public CustomOAuth2UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.delegate = new DefaultOAuth2UserService();
    }

    void setDelegate(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        userRepository.findByEmail(email).orElseGet(() -> {
            UserEntity user = new UserEntity(
                    email,
                    passwordEncoder.encode(UUID.randomUUID().toString()),
                    name != null ? name : email,
                    UserEntity.Role.CUSTOMER,
                    UserEntity.Status.PENDING
            );
            return userRepository.save(user);
        });

        return oAuth2User;
    }
}
