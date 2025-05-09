package com.fintara.utils;

import com.fintara.models.User;
import com.fintara.security.UserDetailsImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Calendar;
import java.util.Date;

@Component
public class JwtUtils {
    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time-hour}")
    private int jwtExpirationHour;


    public String generateToken(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof UserDetailsImpl userPrincipal)) {
            throw new IllegalArgumentException("Unsupported principal type");
        }

        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.HOUR, jwtExpirationHour);
        Date expiredDate = calendar.getTime();

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("userId", userPrincipal.getUserId().toString())
                .claim("role", userPrincipal.getRoleName().toString())
                .issuedAt(now)
                .expiration(expiredDate)
                .signWith(getSignKey())
                .compact();
    }

    public String generateTokenForGoogle(User user) {

        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.HOUR, jwtExpirationHour);
        Date expiredDate = calendar.getTime();

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().getName())
                .issuedAt(now)
                .expiration(expiredDate)
                .signWith(getSignKey())
                .compact();
    }

    public String getUsername(String jwt) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(jwt)
                .getPayload()
                .getSubject();
    }

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getUserId(String jwt) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(jwt)
                .getPayload()
                .getId();
    }

    public String getRoleName(String jwt) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(jwt)
                .getPayload()
                .get("role", String.class);
    }

    public String extractToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ") || bearerToken.length() <= 7) {
            throw new IllegalArgumentException("Invalid Bearer token format");
        }
        return bearerToken.substring(7).trim(); // Remove "Bearer " and trim any extraneous spaces
    }

    public Date getExpirationDateFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }


}