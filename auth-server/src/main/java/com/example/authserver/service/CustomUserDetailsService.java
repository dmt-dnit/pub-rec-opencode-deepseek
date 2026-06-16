package com.example.authserver.service;

import com.example.authserver.model.UserEntity;
import com.example.authserver.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity entity = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        if (entity.getStatus() != UserEntity.Status.ACTIVE) {
            throw new UsernameNotFoundException("User not active: " + email);
        }

        return new User(
                entity.getEmail(),
                entity.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + entity.getRole().name()))
        );
    }
}
