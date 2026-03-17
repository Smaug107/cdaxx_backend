package com.example.cdaxVideo.DTO;

public class LoginResponse {
    private boolean success;
    private String accessToken;
    private String refreshToken;
    private UserDTO user;
    private String deviceType;
    private String message;
    
    public LoginResponse() {}
    
    public LoginResponse(String message, boolean success) {
        this.message = message;
        this.success = success;
    }
    
    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    
    public UserDTO getUser() { return user; }
    public void setUser(UserDTO user) { this.user = user; }
    
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}