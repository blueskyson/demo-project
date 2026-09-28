package com.example.esgaward.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.esgaward.entity.User;
import com.example.esgaward.entity.UserRole;
import com.example.esgaward.repository.UserRepository;

/**
 * Resolves the authenticated Keycloak user to a local {@link User} row, creating it on first
 * login and keeping username/email/role in sync with the latest token afterwards.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final Clock clock;

    public CurrentUserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Not authenticated with a JWT");
        }
        Jwt jwt = token.getToken();
        UUID id = UUID.fromString(jwt.getSubject());
        String username = jwt.hasClaim("preferred_username") ? jwt.getClaimAsString("preferred_username") : jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String displayName = jwt.getClaimAsString("name");
        UserRole role = hasAuthority(authentication, UserRole.ADMIN) ? UserRole.ADMIN : UserRole.NORMAL_USER;
        Instant now = clock.instant();

        return userRepository.findById(id)
                .map(user -> {
                    user.syncProfile(username, email, displayName, role, now);
                    return user;
                })
                .orElseGet(() -> userRepository.save(new User(id, username, email, displayName, role, now)));
    }

    private static boolean hasAuthority(Authentication authentication, UserRole role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role.authority()::equals);
    }
}
