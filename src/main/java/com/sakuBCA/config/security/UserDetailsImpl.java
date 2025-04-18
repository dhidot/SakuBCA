package com.sakuBCA.config.security;

import com.sakuBCA.models.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class UserDetailsImpl implements UserDetails {

    private final User user;
    private final String username;
    private final String password;
    private final List<GrantedAuthority> authorities;

    public UserDetailsImpl(User user, String username, String password, List<GrantedAuthority> authorities) {
        this.user = user;
        this.username = username;
        this.password = password;
        this.authorities = List.copyOf(authorities); // Membuat list tidak bisa diubah
    }

    public static UserDetails build(User user) {
        List<GrantedAuthority> grantedAuthorities = user.getRole() != null
                ? user.getRole().getRoleFeatures().stream()
                .map(roleFeature -> new SimpleGrantedAuthority("FEATURE_" + roleFeature.getFeature().getName()))
                .collect(Collectors.toList())
                : List.of();

        // Tambahkan role sebagai authority
        if (user.getRole() != null) {
            grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
        }

        return new UserDetailsImpl(user, user.getEmail(), user.getPassword(), grantedAuthorities);
    }

    public List<String> getFeatures() {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("FEATURE_"))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
