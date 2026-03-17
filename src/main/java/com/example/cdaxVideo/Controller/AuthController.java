package com.example.cdaxVideo.Controller;

import com.example.cdaxVideo.Entity.User;
import com.example.cdaxVideo.Entity.UserSession;
import com.example.cdaxVideo.Repository.UserRepository;
import com.example.cdaxVideo.Service.AuthService;
import com.example.cdaxVideo.Config.JwtTokenUtil;
import com.example.cdaxVideo.DTO.LoginRequest;
import com.example.cdaxVideo.DTO.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;


    // ---------------------- REGISTER (Original) ----------------------
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(@RequestBody User user) {
        String result = authService.registerUser(user);
        Map<String, Object> resp = new HashMap<>();

        switch (result) {
            case "Email already exists":
            case "Passwords do not match":
            case "Mobile number already registered":
                resp.put("success", false);
                resp.put("message", result);
                return ResponseEntity.badRequest().body(resp);

            case "Registration successful":
                resp.put("success", true);
                resp.put("message", result);
                return ResponseEntity.ok(resp);

            default:
                resp.put("success", false);
                resp.put("message", "Something went wrong");
                return ResponseEntity.status(500).body(resp);
        }
    }

    // ---------------------- LOGIN (Original) ----------------------
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginUser(@RequestBody User user) {
        String result = authService.loginUser(user);
        Map<String, Object> resp = new HashMap<>();

        switch (result) {
            case "Login successful":
                // GET FULL USER DETAILS
                User fullUser = authService.getUserByEmail(user.getEmail());

                resp.put("success", true);
                resp.put("message", "Login successful");
                resp.put("user", fullUser);
                return ResponseEntity.ok(resp);

            case "Incorrect password":
                resp.put("success", false);
                resp.put("message", result);
                return ResponseEntity.status(401).body(resp);

            case "Email not found":
                resp.put("success", false);
                resp.put("message", result);
                return ResponseEntity.status(404).body(resp);

            default:
                resp.put("success", false);
                resp.put("message", "Something went wrong");
                return ResponseEntity.badRequest().body(resp);
        }
    }

    // ==================== NEW JWT ENDPOINTS WITH DEVICE TRACKING ====================

    // ---------------------- LOGIN with JWT and Device Info ----------------------
    @PostMapping("/jwt/login")
    public ResponseEntity<Map<String, Object>> loginUserWithJWT(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            String email = loginRequest.getEmail();
            String password = loginRequest.getPassword();
            String deviceType = loginRequest.getDeviceType();
            String deviceId = loginRequest.getDeviceId();

            if (email == null || password == null || email.isEmpty() || password.isEmpty()) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "Email and password are required");
                return ResponseEntity.badRequest().body(resp);
            }

            // Validate device type
            if (deviceType == null || deviceType.isEmpty()) {
                deviceType = "WEB"; // Default to WEB
            }
            deviceType = deviceType.toUpperCase();
            
            if (!deviceType.equals("MOBILE") && !deviceType.equals("WEB")) {
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", false);
                resp.put("message", "Invalid device type. Must be MOBILE or WEB");
                return ResponseEntity.badRequest().body(resp);
            }

            Map<String, Object> result = authService.loginUserWithJWT(email, password, deviceType, deviceId);
            result.put("success", true);
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(401).body(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "Login failed: " + e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    // ---------------------- REGISTER with JWT ----------------------
    @PostMapping("/jwt/register")
    public ResponseEntity<Map<String, Object>> registerUserWithJWT(@RequestBody User user) {
        try {
            Map<String, Object> result = authService.registerUserWithJWT(user);
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", "Registration failed");
            return ResponseEntity.status(500).body(resp);
        }
    }

    // ---------------------- LOGOUT with Session Cleanup ----------------------
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> response = new HashMap<>();
        
        if (token != null && token.startsWith("Bearer ")) {
            String jwtToken = token.substring(7);
            authService.logout(jwtToken);
            response.put("success", true);
            response.put("message", "Logout successful");
            return ResponseEntity.ok(response);
        }
        
        response.put("success", false);
        response.put("message", "No token provided");
        return ResponseEntity.badRequest().body(response);
    }

    // ---------------------- VALIDATE TOKEN with Session Check ----------------------
    @PostMapping("/jwt/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestBody Map<String, String> tokenRequest) {
        try {
            String token = tokenRequest.get("token");
            if (token == null || token.isEmpty()) {
                throw new RuntimeException("Token is required");
            }
            
            boolean isValid = authService.validateSession(token);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("valid", isValid);
            
            if (isValid) {
                // Add device info to response
                String deviceType = jwtTokenUtil.getDeviceTypeFromToken(token);
                resp.put("deviceType", deviceType);
            }
            
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            resp.put("valid", false);
            return ResponseEntity.ok(resp);
        }
    }

    // ---------------------- GET ACTIVE SESSIONS ----------------------
    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getActiveSessions(@RequestHeader("Authorization") String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                String jwtToken = token.substring(7);
                Long userId = jwtTokenUtil.getUserIdFromToken(jwtToken);
                
                List<UserSession> sessions = authService.getUserActiveSessions(userId);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("sessions", sessions);
                response.put("count", sessions.size());
                return ResponseEntity.ok(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "No token provided");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ---------------------- FORCE LOGOUT OTHER PLATFORMS ----------------------
    @PostMapping("/force-logout-other")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> forceLogoutOtherPlatforms(@RequestHeader("Authorization") String token) {
        try {
            if (token != null && token.startsWith("Bearer ")) {
                String jwtToken = token.substring(7);
                String deviceType = jwtTokenUtil.getDeviceTypeFromToken(jwtToken);
                Long userId = jwtTokenUtil.getUserIdFromToken(jwtToken);
                
                authService.forceLogoutOtherPlatforms(userId, deviceType);
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Other platforms logged out successfully");
                return ResponseEntity.ok(response);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "No token provided");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ---------------------- REFRESH TOKEN ----------------------
    @PostMapping("/jwt/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@RequestBody Map<String, String> body) {
        try {
            String refreshToken = body.get("refreshToken");
            
            if (refreshToken == null || refreshToken.isEmpty()) {
                throw new RuntimeException("Refresh token is required");
            }

            User user = userRepository.findByRefreshToken(refreshToken)
                    .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

            // Get device info from old token if available
            String oldToken = body.get("oldToken");
            String deviceType = "WEB";
            String deviceId = null;
            
            if (oldToken != null && !oldToken.isEmpty()) {
                deviceType = jwtTokenUtil.getDeviceTypeFromToken(oldToken);
                deviceId = jwtTokenUtil.getDeviceIdFromToken(oldToken);
            }

            // Generate new access token with device info
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", user.getRole());
            claims.put("userId", user.getId());
            claims.put("deviceType", deviceType != null ? deviceType : "WEB");
            claims.put("deviceId", deviceId);
            
            String newAccessToken = jwtTokenUtil.generateToken(user.getEmail(), claims);

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("accessToken", newAccessToken);
            resp.put("deviceType", deviceType);
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    // ---------------------- GET CURRENT USER (Protected) ----------------------
    @GetMapping("/jwt/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            
            System.out.println("🔍 [DEBUG] /jwt/me called for email: " + email);
            
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("id", user.getId());
            userResponse.put("firstName", user.getFirstName());
            userResponse.put("lastName", user.getLastName());
            userResponse.put("email", user.getEmail());
            userResponse.put("phoneNumber", user.getPhoneNumber());
            userResponse.put("role", user.getRole());
            userResponse.put("isNewUser", user.getIsNewUser() != null && user.getIsNewUser() == 1);
            userResponse.put("isActive", user.getIsActive());
            userResponse.put("isEmailVerified", user.getIsEmailVerified());
            
            if (user.getAddress() != null) {
                userResponse.put("address", user.getAddress());
            }
            if (user.getDateOfBirth() != null) {
                userResponse.put("dateOfBirth", user.getDateOfBirth().toString());
            }
            if (user.getProfileImage() != null) {
                userResponse.put("profileImage", user.getProfileImage());
            }
            
            userResponse.put("subscribed", false);
            userResponse.put("enrolledCoursesCount", 0);
            
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("user", userResponse);
            
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(401).body(resp);
        }
    }

    // ==================== PROFILE UPDATE ENDPOINTS ====================

    @PutMapping("/profile/update")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> updates) {
        try {
            UserDTO updatedUser = authService.updateProfile(updates);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Profile updated successfully");
            resp.put("user", updatedUser);
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @GetMapping("/profile/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getMyProfile() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            Map<String, Object> userResponse = new HashMap<>();
            userResponse.put("id", user.getId());
            userResponse.put("firstName", user.getFirstName());
            userResponse.put("lastName", user.getLastName());
            userResponse.put("email", user.getEmail());
            userResponse.put("phoneNumber", user.getPhoneNumber());
            userResponse.put("role", user.getRole());
            userResponse.put("isNewUser", user.getIsNewUser() != null && user.getIsNewUser() == 1);
            userResponse.put("isActive", user.getIsActive());
            userResponse.put("isEmailVerified", user.getIsEmailVerified());
            
            if (user.getAddress() != null) {
                userResponse.put("address", user.getAddress());
            }
            if (user.getDateOfBirth() != null) {
                userResponse.put("dateOfBirth", user.getDateOfBirth().toString());
            }
            if (user.getProfileImage() != null) {
                userResponse.put("profileImage", user.getProfileImage());
            }
            
            userResponse.put("subscribed", false);
            userResponse.put("enrolledCoursesCount", 0);
            
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("user", userResponse);
            
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(401).body(resp);
        }
    }

    @PostMapping("/profile/upload-image")
    public ResponseEntity<Map<String, Object>> uploadProfileImage(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String authHeader) {
        
        System.out.println("📤 Profile image upload via AuthController");
        
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Authentication required"
                ));
            }
            
            String token = authHeader.substring(7);
            
            // Validate session first
            if (!authService.validateSession(token)) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Session expired or logged in from another device"
                ));
            }
            
            String email = jwtTokenUtil.getUsernameFromToken(token);
            
            if (email == null || !jwtTokenUtil.validateToken(token)) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Invalid token"
                ));
            }
            
            User user = authService.getUserByEmail(email);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "User not found"
                ));
            }
            
            Map<String, Object> result = authService.uploadProfileImage(file, user.getId());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Upload failed: " + e.getMessage()
            ));
        }
    }

    // ==================== EXISTING UTILITY ENDPOINTS ====================

    @GetMapping("/test")
    public ResponseEntity<String> testServer() {
        return ResponseEntity.ok("Server is running!");
    }

    @GetMapping("/firstName")
    public Map<String, Object> getFirstName(@RequestParam String email) {
        String firstName = authService.getFirstNameByEmail(email);
        Map<String, Object> resp = new HashMap<>();

        if (firstName != null) {
            resp.put("status", "success");
            resp.put("firstName", firstName);
        } else {
            resp.put("status", "error");
            resp.put("message", "User not found");
        }
        return resp;
    }

    @GetMapping("/getUserByEmail")
    public ResponseEntity<Map<String, Object>> getUserByEmail(@RequestParam String email) {
        User user = authService.getUserByEmail(email);
        Map<String, Object> response = new HashMap<>();

        if (user != null) {
            response.put("status", "success");
            response.put("email", user.getEmail());
            response.put("firstName", user.getFirstName());
            response.put("lastName", user.getLastName());
            response.put("mobile", user.getPhoneNumber());
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "User not found");
            return ResponseEntity.status(404).body(response);
        }
    }

    @PostMapping("/debug/hash")
    public String generateHash(@RequestParam String password) {
        return new BCryptPasswordEncoder().encode(password);
    }
}