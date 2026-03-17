package com.example.cdaxVideo.Entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
public class UserSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "device_type", nullable = false)
    private String deviceType; // "MOBILE" or "WEB" this will help us understand 
    
    @Column(name = "device_id")
    private String deviceId;
    
    @Column(name = "jwt_token", columnDefinition = "TEXT", nullable = false)
    private String jwtToken;
    
    @Column(name = "login_time")
    private LocalDateTime loginTime;
    
    @Column(name = "last_activity")
    private LocalDateTime lastActivity;
    
    @Column(name = "is_active")
    private boolean isActive = true;
    
    // Constructors
    public UserSession() {}
    
    public UserSession(User user, String deviceType, String deviceId, String jwtToken) {
        this.user = user;
        this.deviceType = deviceType;
        this.deviceId = deviceId;
        this.jwtToken = jwtToken;
        this.loginTime = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
        this.isActive = true;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getJwtToken() { return jwtToken; }
    public void setJwtToken(String jwtToken) { this.jwtToken = jwtToken; }
    
    public LocalDateTime getLoginTime() { return loginTime; }
    public void setLoginTime(LocalDateTime loginTime) { this.loginTime = loginTime; }
    
    public LocalDateTime getLastActivity() { return lastActivity; }
    public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}