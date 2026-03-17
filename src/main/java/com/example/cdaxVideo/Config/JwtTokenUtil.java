package com.example.cdaxVideo.Config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtTokenUtil {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration}")
    private Long expiration;
    
    @Value("${jwt.issuer}")
    private String issuer;
    
    // Generate signing key from secret
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
    
    // Extract username from token
    public String getUsernameFromToken(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    // Extract expiration date from token
    public Date getExpirationDateFromToken(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    // Extract any claim from token
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    // Extract all claims from token
    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // Return claims even if token is expired
            return e.getClaims();
        }
    }
    
    // Check if token is expired
    public Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
    
    // Generate token for UserDetails
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        return createToken(claims, userDetails.getUsername());
    }
    
    // Generate token for username/email
    public String generateToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username);
    }
    
    // Generate token with custom claims
    public String generateToken(String username, Map<String, Object> additionalClaims) {
        Map<String, Object> claims = new HashMap<>();
        if (additionalClaims != null) {
            claims.putAll(additionalClaims);
        }
        return createToken(claims, username);
    }
    
    // Create the actual token
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }
    
    // Validate token against UserDetails
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    
    // Validate token (basic validation)
    public Boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            return !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            // Token is expired
            return false;
        } catch (UnsupportedJwtException e) {
            // Token format is invalid
            return false;
        } catch (MalformedJwtException e) {
            // Token is malformed
            return false;
        } catch (SecurityException e) {
            // Signature validation failed
            return false;
        } catch (IllegalArgumentException e) {
            // Token is empty or null
            return false;
        }
    }
    
    // Extract user ID from token (if stored in claims)
    public Long getUserIdFromToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Long.class);
    }
    
    // Extract role from token
    public String getRoleFromToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }
    
    // ==================== NEW METHODS FOR DEVICE TRACKING ====================
    
    /**
     * Extract device type from token
     */
    public String getDeviceTypeFromToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("deviceType", String.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract device ID from token
     */
    public String getDeviceIdFromToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("deviceId", String.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Extract session ID from token
     */
    public String getSessionIdFromToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.get("sessionId", String.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check if token has device information
     */
    public boolean hasDeviceInfo(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.containsKey("deviceType") || claims.containsKey("deviceId");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get all custom claims from token
     */
    public Map<String, Object> getAllCustomClaims(String token) {
        try {
            Claims claims = extractAllClaims(token);
            Map<String, Object> customClaims = new HashMap<>();
            
            // Filter out standard claims
            for (String key : claims.keySet()) {
                if (!key.equals("sub") && !key.equals("iss") && !key.equals("iat") && 
                    !key.equals("exp") && !key.equals("aud") && !key.equals("nbf")) {
                    customClaims.put(key, claims.get(key));
                }
            }
            return customClaims;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    
    // ==================== TOKEN REFRESH WITH DEVICE INFO ====================
    
    /**
     * Generate refresh token preserving device info
     */
    public String generateRefreshToken(String oldToken) {
        try {
            String username = getUsernameFromToken(oldToken);
            String deviceType = getDeviceTypeFromToken(oldToken);
            String deviceId = getDeviceIdFromToken(oldToken);
            
            Map<String, Object> claims = new HashMap<>();
            if (deviceType != null) {
                claims.put("deviceType", deviceType);
            }
            if (deviceId != null) {
                claims.put("deviceId", deviceId);
            }
            claims.put("sessionId", java.util.UUID.randomUUID().toString());
            
            return generateToken(username, claims);
        } catch (Exception e) {
            // If can't extract device info, generate simple token
            return generateToken(getUsernameFromToken(oldToken));
        }
    }
    
    // Check if token can be refreshed
    public boolean canTokenBeRefreshed(String token) {
        return (!isTokenExpired(token) || ignoreTokenExpiration(token));
    }
    
    // Override this method if you want to allow refreshing expired tokens
    private boolean ignoreTokenExpiration(String token) {
        // Here you can specify tokens for which expiration is ignored
        return false;
    }
    
    // Get remaining time until token expires (in milliseconds)
    public long getRemainingTime(String token) {
        Date expiration = getExpirationDateFromToken(token);
        Date now = new Date();
        return expiration.getTime() - now.getTime();
    }
}