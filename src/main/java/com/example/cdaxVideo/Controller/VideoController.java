package com.example.cdaxVideo.Controller;

import com.example.cdaxVideo.DTO.VideoCompletionRequestDTO;
import com.example.cdaxVideo.DTO.VideoProgressDTO;
import com.example.cdaxVideo.Entity.User;
import com.example.cdaxVideo.Entity.UserVideoProgress;
import com.example.cdaxVideo.Entity.Video;
import com.example.cdaxVideo.Repository.UserRepository;
import com.example.cdaxVideo.Service.VideoService;
import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
public class VideoController {
    @Autowired
    private UserRepository userRepository; 
    
    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);
    
    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    // ========== EXISTING ENDPOINTS (NO CHANGES) ==========

    /**
     * POST /api/videos/{videoId}/complete
     * Marks a video as completed
     */
    @PostMapping("/{videoId}/complete") 
    public ResponseEntity<Map<String, Object>> markVideoAsCompleted(
            @PathVariable Long videoId,
            @RequestParam Long userId,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long moduleId,
            HttpServletRequest request) {
        
        try {
            logger.info("🎬 === VIDEO COMPLETION REQUEST ===");
            
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.error("❌ No authentication found in SecurityContext");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Not authenticated");
                errorResponse.put("code", "UNAUTHORIZED");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            String authenticatedEmail = authentication.getName();
            logger.info("✅ Authenticated email from JWT: {}", authenticatedEmail);
            
            User authenticatedUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> {
                    logger.error("❌ User not found with email: {}", authenticatedEmail);
                    return new RuntimeException("User not found");
                });
            
            logger.info("✅ Authenticated user ID from DB: {}", authenticatedUser.getId());
            logger.info("✅ Request user ID parameter: {}", userId);
            
            Long actualUserId = authenticatedUser.getId();
            
            if (!actualUserId.equals(userId)) {
                logger.warn("⚠️ User ID mismatch! JWT user ID: {}, Request param user ID: {}", 
                    actualUserId, userId);
                logger.info("⚠️ Using JWT user ID ({}) instead of parameter ({})", 
                    actualUserId, userId);
            }
            
            VideoCompletionRequestDTO requestDTO = new VideoCompletionRequestDTO();
            requestDTO.setVideoId(videoId);
            requestDTO.setUserId(actualUserId);
            requestDTO.setCourseId(courseId);
            requestDTO.setModuleId(moduleId);
            
            logger.info("📦 Calling videoService with: userId={}", actualUserId);
            
            UserVideoProgress progress = videoService.markVideoAsCompleted(requestDTO);
            
            logger.info("📊 VideoService returned:");
            logger.info("   ├─ Progress ID: {}", progress.getId());
            logger.info("   ├─ Video ID: {}", progress.getVideo().getId());
            logger.info("   ├─ User ID: {}", progress.getUser().getId());
            logger.info("   ├─ Completed: {}", progress.isCompleted());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Video marked as completed successfully");
            response.put("videoId", videoId);
            response.put("userId", actualUserId);
            response.put("completed", progress.isCompleted());
            response.put("unlocked", progress.isUnlocked());
            response.put("completedOn", progress.getCompletedOn());
            response.put("watchedSeconds", progress.getWatchedSeconds());
            
            if (!progress.isCompleted()) {
                logger.warn("⚠️ WARNING: Video {} was NOT marked as completed for user {}!", 
                           videoId, actualUserId);
                response.put("warning", "Video was not marked as completed - check requirements");
            } else {
                logger.info("✅ Video {} successfully completed for user {}", videoId, actualUserId);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("❌ Error marking video as completed: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("code", "VALIDATION_ERROR");
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("❌ Unexpected error marking video as completed: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error");
            errorResponse.put("code", "INTERNAL_ERROR");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * POST /api/videos/{videoId}/progress
     * Updates video progress (watch time, position, etc.)
     */
    @PostMapping("/{videoId}/progress")
    public ResponseEntity<Map<String, Object>> updateVideoProgress(
            @PathVariable Long videoId,
            @RequestBody VideoProgressDTO progressDTO) {
        
        try {
            logger.debug("Updating video progress: {}", progressDTO);
            
            progressDTO.setVideoId(videoId);
            
            UserVideoProgress progress = videoService.updateVideoProgress(progressDTO);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Video progress updated successfully");
            response.put("videoId", videoId);
            response.put("userId", progressDTO.getUserId());
            response.put("watchedSeconds", progress.getWatchedSeconds());
            response.put("lastPositionSeconds", progress.getLastPositionSeconds());
            response.put("forwardJumpsCount", progress.getForwardJumpsCount());
            response.put("completed", progress.isCompleted());
            response.put("unlocked", progress.isUnlocked());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error updating video progress: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Unexpected error updating video progress: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * GET /api/videos/{videoId}/progress
     * Gets video progress for a user
     */
    @GetMapping("/{videoId}/progress")
    public ResponseEntity<Map<String, Object>> getVideoProgress(
            @PathVariable Long videoId,
            @RequestParam Long userId) {
        
        try {
            logger.debug("Getting video progress - Video: {}, User: {}", videoId, userId);
            
            VideoProgressDTO progress = videoService.getVideoProgress(videoId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("progress", progress);
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error getting video progress: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Unexpected error getting video progress: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * POST /api/videos/{videoId}/unlock
     * Unlocks a video for a user
     */
    @PostMapping("/{videoId}/unlock")
    public ResponseEntity<Map<String, Object>> unlockVideo(
            @PathVariable Long videoId,
            @RequestParam Long userId) {
        
        try {
            logger.info("Unlocking video - Video: {}, User: {}", videoId, userId);
            
            UserVideoProgress progress = videoService.unlockVideo(videoId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Video unlocked successfully");
            response.put("videoId", videoId);
            response.put("userId", userId);
            response.put("unlocked", true);
            response.put("unlockedOn", progress.getUnlockedOn());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error unlocking video: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    /**
     * POST /api/videos/{videoId}/manual-complete
     * Manually marks a video as completed (admin override)
     */
    @PostMapping("/{videoId}/manual-complete")
    public ResponseEntity<Map<String, Object>> manuallyCompleteVideo(
            @PathVariable Long videoId,
            @RequestParam Long userId) {
        
        try {
            logger.info("Manual video completion - Video: {}, User: {}", videoId, userId);
            
            UserVideoProgress progress = videoService.manuallyCompleteVideo(videoId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Video manually marked as completed");
            response.put("videoId", videoId);
            response.put("userId", userId);
            response.put("completed", true);
            response.put("manuallyCompleted", true);
            response.put("completedOn", progress.getCompletedOn());
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error in manual video completion: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "VideoService");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    // ========== NEW OPTIMIZED ENDPOINTS ==========
    
    /**
     * ✅ GET /api/videos/module/{moduleId}/optimized
     * Get videos for module with optimized queries
     */
    @GetMapping("/module/{moduleId}/optimized")
    public ResponseEntity<Map<String, Object>> getVideosByModuleOptimized(
            @PathVariable Long moduleId,
            @RequestParam(required = false) Long userId) {
        
        try {
            logger.info("🚀 Getting OPTIMIZED videos for module {} with user {}", moduleId, userId);
            
            List<Video> videos = videoService.getVideosByModuleOptimized(moduleId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", videos);
            response.put("moduleId", moduleId);
            response.put("count", videos.size());
            response.put("optimized", true);
            response.put("message", "Optimized query used - no N+1 issues");
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error in optimized video fetch: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Unexpected error in optimized video fetch: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * ✅ POST /api/videos/batch-optimized
     * Batch get videos for multiple modules with optimized queries
     */
    @PostMapping("/batch-optimized")
    public ResponseEntity<Map<String, Object>> getVideosBatchOptimized(
            @RequestBody Map<String, Object> request,
            @RequestParam(required = false) Long userId) {
        
        try {
            @SuppressWarnings("unchecked")
            List<Long> moduleIds = (List<Long>) request.get("moduleIds");
            
            if (moduleIds == null || moduleIds.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "moduleIds is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            logger.info("🚀 Batch OPTIMIZED video fetch for {} modules", moduleIds.size());
            
            Map<Long, List<Video>> videosByModule = videoService.getVideosByModuleIdsOptimized(moduleIds, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", videosByModule);
            response.put("totalModules", moduleIds.size());
            response.put("modulesWithData", videosByModule.size());
            response.put("optimized", true);
            response.put("message", "Batch optimized query used - no N+1 issues");
            
            if (videosByModule.size() < moduleIds.size()) {
                response.put("warning", "Some modules have no videos");
            }
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error in batch optimized video fetch: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Unexpected error in batch optimized video fetch: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * ✅ GET /api/videos/{videoId}/optimized
     * Get video details with optimized queries
     */
    @GetMapping("/{videoId}/optimized")
    public ResponseEntity<Map<String, Object>> getVideoDetailsOptimized(
            @PathVariable Long videoId,
            @RequestParam(required = false) Long userId) {
        
        try {
            logger.info("🚀 Getting OPTIMIZED details for video {} with user {}", videoId, userId);
            
            Video video = videoService.getVideoDetailsOptimized(videoId, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", video);
            response.put("videoId", videoId);
            response.put("optimized", true);
            response.put("message", "Optimized query used - no N+1 issues");
            
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            logger.error("Error in optimized video details fetch: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        } catch (Exception e) {
            logger.error("Unexpected error in optimized video details fetch: {}", e.getMessage(), e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Internal server error");
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}