package be.dnit.authserver.config;

import be.dnit.authserver.model.UserEntity;
import be.dnit.authserver.repository.UserRepository;
import be.dnit.authserver.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final String redirectBaseUrl;

    public OAuth2SuccessHandler(UserRepository userRepository, JwtService jwtService,
                                @Value("${app.oauth2.success-redirect-base-url}") String redirectBaseUrl) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.redirectBaseUrl = redirectBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "OAuth2 user not found in local DB: " + email));

        String redirectUrl = switch (user.getStatus()) {
            case ACTIVE -> redirectBaseUrl + "/login?oauth2=success&token=" + jwtService.generateToken(user);
            case PENDING -> redirectBaseUrl + "/login?oauth2=pending";
            default -> redirectBaseUrl + "/login?oauth2=error";
        };

        response.sendRedirect(redirectUrl);
    }
}
