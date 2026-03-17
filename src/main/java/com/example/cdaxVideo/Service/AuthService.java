package com.example.cdaxVideo.Service;

import com.example.cdaxVideo.Entity.User;
import com.example.cdaxVideo.Entity.UserSession;
import com.example.cdaxVideo.Repository.UserSessionRepository;
import java.io.IOException;
import com.example.cdaxVideo.Entity.UserCoursePurchase;
import com.example.cdaxVideo.Repository.UserCoursePurchaseRepository;
import com.example.cdaxVideo.Repository.UserRepository;
import com.example.cdaxVideo.Config.JwtTokenUtil;
import com.example.cdaxVideo.DTO.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Service
@Transactional
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository sessionRepository; // Add this

    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserCoursePurchaseRepository userCoursePurchaseRepository;
    
    @Autowired
    private CustomUserDetailsService userDetailsService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== UPDATED LOGIN METHOD WITH SESSION CONTROL ====================

    public Map<String, Object> loginUserWithJWT(String email, String password, String deviceType, String deviceId) {
        // 🔍 Debug: print the email received
        System.out.println("🔍 [DEBUG] loginUserWithJWT called with email: '" + email + "'");
        System.out.println("   ├─ Password length: " + (password != null ? password.length() : 0));
        System.out.println("   ├─ Device Type: " + deviceType);
        System.out.println("   ├─ Device ID: " + deviceId);

        // Fetch user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    System.out.println("⚠️ [DEBUG] User not found for email: '" + email + "'");
                    return new RuntimeException("Email not found");
                });

        System.out.println("✅ [DEBUG] User found: " + user.getEmail());

        // Check password using BCrypt
        if (!passwordEncoder.matches(password, user.getPassword())) {
            System.out.println("❌ [DEBUG] Incorrect password for user: " + user.getEmail());
            throw new RuntimeException("Incorrect password...");
        }

        // SESSION MANAGEMENT - Check if user is already logged in on a different platform
        String normalizedDeviceType = deviceType != null ? deviceType.toUpperCase() : "WEB";
        
        // Check for active session on OTHER platform
        Optional<UserSession> otherPlatformSession = sessionRepository
                .findByUserAndDeviceTypeAndIsActiveTrue(user, 
                    normalizedDeviceType.equals("MOBILE") ? "WEB" : "MOBILE");
        
        if (otherPlatformSession.isPresent()) {
            // Deactivate the other platform session
            sessionRepository.deactivateOtherPlatformSessions(user, normalizedDeviceType);
            System.out.println("🔄 User " + user.getFullName() + " (" + user.getEmail() + ") logged in from " + normalizedDeviceType + 
                             ", deactivated " + otherPlatformSession.get().getDeviceType() + " session");
        }
        
        // Deactivate any existing sessions for this specific device
        if (deviceId != null && !deviceId.isEmpty()) {
            sessionRepository.deactivateCurrentDeviceSessions(user, normalizedDeviceType, deviceId);
        }

        //  Generate JWT token with device info
        Map<String, Object> additionalClaims = new HashMap<>();
        additionalClaims.put("role", user.getRole());
        additionalClaims.put("userId", user.getId());
        additionalClaims.put("deviceType", normalizedDeviceType);
        additionalClaims.put("deviceId", deviceId);
        additionalClaims.put("sessionId", UUID.randomUUID().toString());

        String token = jwtTokenUtil.generateToken(user.getEmail(), additionalClaims);

        // Generate refresh token
        String refreshToken = UUID.randomUUID().toString();
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        // ✅ Create and save new session
        UserSession session = new UserSession(user, normalizedDeviceType, deviceId, token);
        sessionRepository.save(session);

        // ✅ Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("accessToken", token);
        response.put("refreshToken", refreshToken);
        response.put("user", new UserDTO(user));
        response.put("deviceType", normalizedDeviceType);
        response.put("message", "Login successful");
        
        System.out.println("✅ [DEBUG] Login successful, session created for " + normalizedDeviceType);
        return response;
    }

    // ==================== OVERLOADED METHODS FOR BACKWARD COMPATIBILITY ====================

    // Original login method (for backward compatibility - defaults to WEB platform)
    public Map<String, Object> loginUserWithJWT(String email, String password) {
        return loginUserWithJWT(email, password, "WEB", null);
    }

    // ==================== NEW SESSION MANAGEMENT METHODS ====================

    @Transactional
    public void logout(String token) {
        if (token != null) {
            Optional<UserSession> session = sessionRepository.findByJwtToken(token);
            session.ifPresent(s -> {
                s.setActive(false);
                sessionRepository.save(s);
                System.out.println("User logged out, session deactivated");
            });
        }
    }

    public boolean validateSession(String token) {
        if (token == null || !jwtTokenUtil.validateToken(token)) {
            return false;
        }
        
        Optional<UserSession> session = sessionRepository.findByJwtToken(token);
        boolean isValid = session.isPresent() && session.get().isActive();
        
        if (!isValid) {
            System.out.println("Session invalid or inactive for token");
        }
        
        return isValid;
    }

    @Transactional
    public void updateActivity(String token) {
        if (token != null) {
            sessionRepository.updateLastActivity(token);
        }
    }

    public List<UserSession> getUserActiveSessions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return sessionRepository.findByUserAndIsActiveTrue(user);
    }

    @Transactional
    public void forceLogoutAllSessions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        sessionRepository.deactivateOtherPlatformSessions(user, "NONE");
        System.out.println("All sessions deactivated for user: " + userId);
    }

    @Transactional
    public void forceLogoutOtherPlatforms(Long userId, String currentDeviceType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        sessionRepository.deactivateOtherPlatformSessions(user, currentDeviceType);
        System.out.println("Other platform sessions deactivated for user: " + userId);
    }

    // ==================== EXISTING METHODS (UNCHANGED) ====================

    // Method 1: Enhanced registration with JWT
    public Map<String, Object> registerUserWithJWT(User user) {
        // Email normalization
        String email = user.getEmail().trim().toLowerCase();
        user.setEmail(email);

        // Validations
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        if (userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            throw new RuntimeException("Mobile number already registered");
        }

        // Hash password before saving
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // Set default role if not provided
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("USER");
        }
        
        // Set isNewUser flag
        user.setIsNewUser(1);
        
        user = userRepository.save(user);

        // Generate JWT token
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String token = jwtTokenUtil.generateToken(userDetails);
        
        // Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", new UserDTO(user));
        response.put("message", "Registration successful");
        
        return response;
    }

    public AuthService(UserRepository userRepository, JwtTokenUtil jwtTokenUtil) {
        this.userRepository = userRepository;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    // Method 3: Original registration method (for backward compatibility)
    public String registerUser(User user) {
        String email = user.getEmail().trim().toLowerCase();
        user.setEmail(email);

        if (userRepository.existsByEmail(email)) {
            return "Email already exists";
        }

        if (userRepository.existsByPhoneNumber(user.getPhoneNumber())) {
            return "Mobile number already registered";
        }

        // Only hash if password is not already hashed
        String rawPassword = user.getPassword();
        if (!isPasswordHashed(rawPassword)) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        }
        
        // Set isNewUser flag
        user.setIsNewUser(1);
        
        userRepository.save(user);
        return "Registration successful"; 
    }

    // Method 4: Original login method (for backward compatibility)
    public String loginUser(User user) {
        String email = user.getEmail().trim().toLowerCase();
        String password = user.getPassword().trim();

        Optional<User> dbUser = userRepository.findByEmail(email);

        if (dbUser.isEmpty()) {
            return "Email not found";
        }

        User foundUser = dbUser.get();
        
        // Check if password is hashed or plain text
        if (isPasswordHashed(foundUser.getPassword())) {
            // Password is hashed, use passwordEncoder.matches()
            if (!passwordEncoder.matches(password, foundUser.getPassword())) {
                return "Incorrect password";
            }
        } else {
            // Password is plain text (legacy)
            if (!foundUser.getPassword().trim().equals(password)) {
                return "Incorrect password";
            }
        }

        return "Login successful";
    }
    
    // Method 5: Get user by email (unchanged)
    public String getFirstNameByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getFirstName)
                .orElse(null);
    }

    // Method 6: Get user by email with debug info (unchanged)
    public User getUserByEmail(String email) {
        System.out.println("\n🔍 getUserByEmail() called");
        System.out.println("   ├─ Email: " + email);
        
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("   ✅ User found:");
            System.out.println("      ├─ ID: " + user.getId());
            System.out.println("      ├─ Name: " + user.getFirstName() + " " + user.getLastName());
            System.out.println("      ├─ Email: " + user.getEmail());
            System.out.println("      └─ Role: " + user.getRole());
            
            if (email.equals("arjun.sharma@email.com") && user.getId() != 19L) {
                System.out.println("   ⚠️⚠️⚠️ WRONG USER ID!");
                System.out.println("   ⚠️ Expected ID 19 for Arjun, got: " + user.getId());
            }
            
            return user;
        } else {
            System.out.println("   ❌ User not found!");
            return null;
        }
    }
    
    // ==================== JWT UTILITY METHODS ====================
    
    // Method 7: Get current authenticated user
    public UserDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            System.out.println("❌ Authentication is null or not authenticated");
            throw new RuntimeException("User not authenticated");
        }
        
        String email = authentication.getName();
        System.out.println("🔍 Authentication details:");
        System.out.println("   ├─ Name: " + email);
        System.out.println("   ├─ Principal: " + authentication.getPrincipal());
        System.out.println("   ├─ Authenticated: " + authentication.isAuthenticated());
        System.out.println("   ├─ Authorities: " + authentication.getAuthorities());
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    System.out.println("❌ User not found for email: " + email);
                    return new RuntimeException("User not found");
                });
        
        System.out.println("✅ User found: " + user.getEmail());
        
        return new UserDTO(user);
    }
    
    // Method 8: Validate JWT token (updated to check session)
    public boolean validateToken(String token) {
        return validateSession(token);
    }
    
    // Method 9: Refresh JWT token
    public String refreshToken(String oldToken) {
        if (jwtTokenUtil.validateToken(oldToken)) {
            String username = jwtTokenUtil.getUsernameFromToken(oldToken);
            
            // Get device info from old token
            String deviceType = jwtTokenUtil.getDeviceTypeFromToken(oldToken);
            String deviceId = jwtTokenUtil.getDeviceIdFromToken(oldToken);
            
            // Generate new token with same device info
            Map<String, Object> claims = new HashMap<>();
            claims.put("deviceType", deviceType);
            claims.put("deviceId", deviceId);
            
            return jwtTokenUtil.generateToken(username, claims);
        }
        throw new RuntimeException("Invalid token");
    }
    
    // Method 10: Validate user credentials
    public boolean validateCredentials(String email, String password) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    // Helper method to check if password is already hashed
    private boolean isPasswordHashed(String password) {
        // BCrypt hashed passwords start with $2a$, $2b$, $2y$ or $2x$
        return password != null && 
               (password.startsWith("$2a$") || 
                password.startsWith("$2b$") || 
                password.startsWith("$2y$") || 
                password.startsWith("$2x$"));
    }

    // ---------------------- DEBUG AUTH CONTEXT ----------------------
    @GetMapping("/jwt/debug")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> debugAuth() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("name", authentication.getName());
        debugInfo.put("principal", authentication.getPrincipal().toString());
        debugInfo.put("authenticated", authentication.isAuthenticated());
        debugInfo.put("authorities", authentication.getAuthorities().toString());
        
        return ResponseEntity.ok(debugInfo);
    }

    // Method 11: Update user profile
    public UserDTO updateProfile(Map<String, Object> updates) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Update fields if present in request
        if (updates.containsKey("firstName")) {
            user.setFirstName((String) updates.get("firstName"));
        }
        if (updates.containsKey("lastName")) {
            user.setLastName((String) updates.get("lastName"));
        }
        if (updates.containsKey("phone")) {
            user.setPhoneNumber((String) updates.get("phone"));
        }
        if (updates.containsKey("phoneNumber")) {
            user.setPhoneNumber((String) updates.get("phoneNumber"));
        }
        if (updates.containsKey("address")) {
            user.setAddress((String) updates.get("address"));
        }
        if (updates.containsKey("dateOfBirth")) {
            String dobStr = (String) updates.get("dateOfBirth");
            if (dobStr != null && !dobStr.isEmpty()) {
                user.setDateOfBirth(LocalDate.parse(dobStr));
            }
        }
        
        userRepository.save(user);
        return new UserDTO(user);
    }

    // Method 12: Get user by ID
    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    // Update determineIfSubscribed method to use SubscriptionService
    private Boolean determineIfSubscribed(User user) {
        try {
            // Option 1: Check if user has any active subscription
            if (subscriptionService.hasAnyActiveSubscription(user.getId())) {
                return true;
            }
            
            // Option 2: Fallback to checking purchased courses
            List<UserCoursePurchase> purchases = userCoursePurchaseRepository.findByUserId(user.getId());
            return !purchases.isEmpty();
            
        } catch (Exception e) {
            // If subscription service is not available, fallback to purchased courses
            List<UserCoursePurchase> purchases = userCoursePurchaseRepository.findByUserId(user.getId());
            return !purchases.isEmpty();
        }
    }
    
    // Update getFullUserProfile method
    public UserDTO getFullUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Get enrolled courses count
        Integer enrolledCoursesCount = 0;
        if (user.getSubscribedCourses() != null) {
            enrolledCoursesCount = user.getSubscribedCourses().size();
        }
        
        // Check subscription status
        Boolean subscribed = determineIfSubscribed(user);
        
        // Create UserDTO with subscription info
        UserDTO userDTO = new UserDTO(user);
        userDTO.setEnrolledCoursesCount(enrolledCoursesCount);
        userDTO.setSubscribed(subscribed);
        
        return userDTO;
    }
    
    // Add this method to get subscription details (calls SubscriptionService)
    public Map<String, Object> getUserSubscriptionDetails(Long userId) {
        try {
            return subscriptionService.getSubscriptionDetails(userId);
        } catch (Exception e) {
            // Return basic info if subscription service fails
            Map<String, Object> basicInfo = new HashMap<>();
            basicInfo.put("userId", userId);
            basicInfo.put("hasActiveSubscription", false);
            basicInfo.put("activeSubscriptionCount", 0);
            basicInfo.put("subscriptions", new ArrayList<>());
            return basicInfo;
        }
    }

    public Map<String, Object> uploadProfileImage(MultipartFile file, Long userId) throws IOException {
        System.out.println("📤 Uploading profile image for user ID: " + userId);
        
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        
        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Only image files are allowed. Received: " + contentType);
        }
        
        // Get user by ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        
        System.out.println("✅ User found: " + user.getEmail() + " (ID: " + user.getId() + ")");
        
        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
        
        // Create uploads directory if it doesn't exist
        Path uploadDir = Paths.get("uploads/profile-images");
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
            System.out.println("✅ Created upload directory: " + uploadDir.toAbsolutePath());
        }
        
        // Save file
        Path filePath = uploadDir.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath);
        
        System.out.println("✅ File saved: " + filePath.toAbsolutePath());
        
        // Construct URL for the uploaded file
        String imageUrl = "/uploads/profile-images/" + uniqueFilename;
        
        // Update user's profile image in database (user is already fetched above)
        user.setProfileImage(imageUrl);
        userRepository.save(user);
        
        System.out.println("✅ User profile image updated in database: " + imageUrl);
        
        // Prepare response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Image uploaded successfully");
        response.put("imageUrl", imageUrl);
        response.put("filename", uniqueFilename);
        
        return response;
    }
}