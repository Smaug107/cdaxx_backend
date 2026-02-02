package com.example.cdaxVideo.Service;

import com.example.cdaxVideo.DTO.VideoCompletionRequestDTO;
import com.example.cdaxVideo.DTO.VideoProgressDTO;
import com.example.cdaxVideo.Entity.User;
import com.example.cdaxVideo.Entity.Video;
import com.example.cdaxVideo.Entity.UserVideoProgress;
import com.example.cdaxVideo.Repository.UserRepository;
import com.example.cdaxVideo.Repository.VideoRepository;
import com.example.cdaxVideo.Repository.UserVideoProgressRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoService {
    
    private static final Logger logger = LoggerFactory.getLogger(VideoService.class);
    
    private final VideoRepository videoRepository;
    private final UserRepository userRepository;
    private final UserVideoProgressRepository progressRepository;
    private final StreakService streakService;
    private final CourseService courseService;

    public VideoService(VideoRepository videoRepository,
                       UserRepository userRepository,
                       UserVideoProgressRepository progressRepository,
                       StreakService streakService,
                       @Lazy CourseService courseService) {
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
        this.progressRepository = progressRepository;
        this.streakService = streakService;
        this.courseService = courseService;
    }

    // ========== EXISTING METHODS ==========
    
    /**
     * Marks a video as completed for a user
     */
    @Transactional
    public UserVideoProgress markVideoAsCompleted(VideoCompletionRequestDTO request) {
        logger.info("Marking video as completed: {}", request);
        
        validateRequest(request);
        
        // FIXED: Use repository method directly
        Video video = videoRepository.findByIdWithModuleAndCourse(request.getVideoId())
                .orElseThrow(() -> new RuntimeException("Video not found with ID: " + request.getVideoId()));
        
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + request.getUserId()));
        
        if (user.getIsNewUser() != null && user.getIsNewUser() == 1) {
            user.setIsNewUser(0);
            userRepository.save(user);
            logger.info("✅ User {} marked as NOT new after completing video {}", user.getId(), video.getId());
        }
        
        UserVideoProgress progress = progressRepository
                .findByUserIdAndVideoId(user.getId(), video.getId())
                .orElseGet(() -> createNewProgressRecord(user, video));
        
        progress.setCompleted(true);
        progress.setUnlocked(true);
        
        if (progress.getWatchedSeconds() == null || 
            progress.getWatchedSeconds() < (int)(video.getDuration() * 0.95)) {
            progress.setWatchedSeconds((int)(video.getDuration() * 0.95));
        }
        
        UserVideoProgress savedProgress = progressRepository.save(progress);
        
        updateStreakForVideoCompletion(request, video, savedProgress);
        
        try {
            boolean success = courseService.completeVideoAndUnlockNext(
                request.getUserId(), 
                request.getCourseId(), 
                request.getModuleId(), 
                request.getVideoId()
            );
            
            if (success) {
                logger.info("✅ CourseService successfully processed video completion and unlocking");
            }
        } catch (Exception e) {
            logger.error("❌ Error calling CourseService.completeVideoAndUnlockNext: {}", e.getMessage(), e);
        }
        
        logger.info("Video {} marked as completed for user {}", video.getId(), user.getId());
        return savedProgress;
    }
    
    private void updateStreakForVideoCompletion(VideoCompletionRequestDTO request, 
                                           Video video, 
                                           UserVideoProgress progress) {
        try {
            if (request.getCourseId() != null) {
                int watchedSeconds = progress.getWatchedSeconds() != null ? 
                                   progress.getWatchedSeconds() : video.getDuration();
                
                logger.info("📞 ===== CALLING STREAK SERVICE =====");
                logger.info("   ├─ User ID: {}", request.getUserId());
                logger.info("   ├─ Course ID: {}", request.getCourseId());
                logger.info("   ├─ Video ID: {}", request.getVideoId());
                logger.info("   ├─ Video Title: {}", video.getTitle());
                logger.info("   ├─ Video Duration: {}s", video.getDuration());
                logger.info("   ├─ Watched Seconds: {}s", watchedSeconds);
                logger.info("   ├─ Is Completed: {}", true);
                logger.info("   └─ Current Time: {}", new Date());
                
                streakService.updateStreakForVideoWatch(
                    request.getUserId(),
                    request.getCourseId(),
                    request.getVideoId(),
                    watchedSeconds,
                    true
                );
                
                logger.info("✅ StreakService called successfully");
                
            } else {
                logger.warn("⚠️ Cannot update streak: Course ID is null for video {} user {}", 
                    request.getVideoId(), request.getUserId());
            }
        } catch (Exception e) {
            logger.error("❌ Failed to update streak for video completion: {}", e.getMessage(), e);
        }
    }

    /**
     * Updates video progress (watch time, position, forward jumps)
     */
    @Transactional
    public UserVideoProgress updateVideoProgress(VideoProgressDTO progressDTO) {
        logger.debug("Updating video progress: {}", progressDTO);
        
        if (progressDTO.getVideoId() == null || progressDTO.getUserId() == null) {
            throw new RuntimeException("Video ID and User ID are required");
        }
        
        Video video = videoRepository.findById(progressDTO.getVideoId())
                .orElseThrow(() -> new RuntimeException("Video not found with ID: " + progressDTO.getVideoId()));
        
        User user = userRepository.findById(progressDTO.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + progressDTO.getUserId()));
        
        UserVideoProgress progress = progressRepository
                .findByUserIdAndVideoId(user.getId(), video.getId())
                .orElseGet(() -> createNewProgressRecord(user, video));
        
        if (progressDTO.getWatchedSeconds() != null) {
            progress.setWatchedSeconds(progressDTO.getWatchedSeconds());
        }
        
        if (progressDTO.getLastPositionSeconds() != null) {
            progress.setLastPositionSeconds(progressDTO.getLastPositionSeconds());
        }
        
        if (progressDTO.getForwardJumpsCount() != null) {
            progress.setForwardJumpsCount(progressDTO.getForwardJumpsCount());
        }
        
        checkAndMarkCompletion(progress, video);
        
        updateStreakForVideoProgress(progressDTO, video);
        
        return progressRepository.save(progress);
    }

    private void updateStreakForVideoProgress(VideoProgressDTO progressDTO, Video video) {
        try {
            if (progressDTO.getWatchedSeconds() != null && progressDTO.getWatchedSeconds() > 60) {
                logger.debug("Video progress updated: {} seconds watched", progressDTO.getWatchedSeconds());
            }
        } catch (Exception e) {
            logger.error("Failed to update streak for video progress: {}", e.getMessage());
        }
    }

    /**
     * Gets video progress for a user
     */
    public VideoProgressDTO getVideoProgress(Long videoId, Long userId) {
        Optional<UserVideoProgress> progressOpt = 
            progressRepository.findByUserIdAndVideoId(userId, videoId);
        
        VideoProgressDTO dto = new VideoProgressDTO();
        dto.setVideoId(videoId);
        dto.setUserId(userId);
        
        if (progressOpt.isPresent()) {
            UserVideoProgress progress = progressOpt.get();
            dto.setWatchedSeconds(progress.getWatchedSeconds());
            dto.setLastPositionSeconds(progress.getLastPositionSeconds());
            dto.setForwardJumpsCount(progress.getForwardJumpsCount());
            dto.setCompleted(progress.isCompleted());
            dto.setUnlocked(progress.isUnlocked());
        }
        
        return dto;
    }

    /**
     * Manually marks a video as completed (admin/instructor override)
     */
    @Transactional
    public UserVideoProgress manuallyCompleteVideo(Long videoId, Long userId) {
        // FIXED: Use repository method directly
        Video video = videoRepository.findByIdWithModuleAndCourse(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserVideoProgress progress = progressRepository
                .findByUserIdAndVideoId(userId, videoId)
                .orElseGet(() -> createNewProgressRecord(user, video));
        
        progress.setCompleted(true);
        progress.setUnlocked(true);
        progress.setManuallyCompleted(true);
        progress.setWatchedSeconds(video.getDuration());
        
        try {
            Long moduleId = video.getModule().getId();
            Long courseId = video.getModule().getCourse().getId();
            courseService.completeVideoAndUnlockNext(userId, courseId, moduleId, videoId);
        } catch (Exception e) {
            logger.error("Error calling CourseService for manual completion: {}", e.getMessage());
        }
        
        return progressRepository.save(progress);
    }

    /**
     * Unlocks a video for a user (when prerequisites are met)
     */
    @Transactional
    public UserVideoProgress unlockVideo(Long videoId, Long userId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserVideoProgress progress = progressRepository
                .findByUserIdAndVideoId(userId, videoId)
                .orElseGet(() -> createNewProgressRecord(user, video));
        
        progress.setUnlocked(true);
        
        return progressRepository.save(progress);
    }

    /**
     * Helper method to validate completion request
     */
    private void validateRequest(VideoCompletionRequestDTO request) {
        if (request.getVideoId() == null) {
            throw new RuntimeException("Video ID is required");
        }
        if (request.getUserId() == null) {
            throw new RuntimeException("User ID is required");
        }
        if (request.getCourseId() == null) {
            logger.warn("Course ID is missing in completion request");
        }
        if (request.getModuleId() == null) {
            logger.warn("Module ID is missing in completion request");
        }
    }

    /**
     * Helper method to create new progress record
     */
    private UserVideoProgress createNewProgressRecord(User user, Video video) {
        UserVideoProgress progress = new UserVideoProgress();
        progress.setUser(user);
        progress.setVideo(video);
        progress.setUnlocked(false);
        progress.setCompleted(false);
        progress.setWatchedSeconds(0);
        progress.setLastPositionSeconds(0);
        progress.setForwardJumpsCount(0);
        return progress;
    }

    /**
     * Checks if video should be marked as completed based on watch time
     */
    private void checkAndMarkCompletion(UserVideoProgress progress, Video video) {
        if (!progress.isCompleted() && !Boolean.TRUE.equals(progress.getManuallyCompleted())) {
            if (progress.getWatchedSeconds() != null && 
                progress.getWatchedSeconds() >= (int)(video.getDuration() * 0.95) &&
                (progress.getForwardJumpsCount() == null || progress.getForwardJumpsCount() < 10)) {
                
                progress.setCompleted(true);
                progress.setUnlocked(true);
                logger.info("Auto-marking video {} as completed for user {} (watched: {}s, duration: {}s)", 
                    video.getId(), progress.getUser().getId(), 
                    progress.getWatchedSeconds(), video.getDuration());
                
                try {
                    Long userId = progress.getUser().getId();
                    Long videoId = video.getId();
                    Long moduleId = video.getModule().getId();
                    Long courseId = video.getModule().getCourse().getId();
                    
                    courseService.completeVideoAndUnlockNext(userId, courseId, moduleId, videoId);
                } catch (Exception e) {
                    logger.error("Error calling CourseService for auto-completion: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * SIMPLIFIED HELPER: Unlock just this video (without triggering chain)
     */
    @Transactional
    public boolean unlockSingleVideo(Long userId, Long videoId) {
        try {
            Video video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new RuntimeException("Video not found"));
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            UserVideoProgress progress = progressRepository
                    .findByUserIdAndVideoId(userId, videoId)
                    .orElseGet(() -> createNewProgressRecord(user, video));
            
            if (!progress.isUnlocked()) {
                progress.setUnlocked(true);
                progressRepository.save(progress);
                logger.info("Unlocked video {} for user {}", videoId, userId);
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error unlocking video {} for user {}: {}", videoId, userId, e.getMessage());
            return false;
        }
    }

    // ========== NEW OPTIMIZED METHODS ==========
    
    /**
     * ✅ NEW: Get videos for a module (optimized version)
     */
    public List<Video> getVideosByModuleOptimized(Long moduleId, Long userId) {
        logger.info("🔄 Getting videos for module {} (optimized)", moduleId);
        long startTime = System.currentTimeMillis();
        
        try {
            // Try to use optimized repository method
            List<Video> videos = videoRepository.findVideosByModuleIdOptimized(moduleId);
            
            long endTime = System.currentTimeMillis();
            logger.info("✅ Found {} videos for module {} in {}ms (optimized)", 
                videos.size(), moduleId, (endTime - startTime));
            
            return videos;
        } catch (Exception e) {
            logger.warn("⚠️ Optimized method failed, falling back to standard: {}", e.getMessage());
            // FIXED: Use repository method directly, not service method
            return videoRepository.findByModuleId(moduleId);
        }
    }
    
    /**
     * ✅ NEW: Batch get videos for multiple modules (optimized)
     */
    public Map<Long, List<Video>> getVideosByModuleIdsOptimized(List<Long> moduleIds, Long userId) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            logger.warn("Empty module IDs list provided");
            return new HashMap<>();
        }
        
        logger.info("🔄 Batch fetching videos for {} modules (optimized)", moduleIds.size());
        long startTime = System.currentTimeMillis();
        
        try {
            // Try to use optimized repository method
            List<Video> allVideos = videoRepository.findVideosByModuleIdsOptimized(moduleIds);
            
            // Group by module ID
            Map<Long, List<Video>> videosByModule = allVideos.stream()
                    .collect(Collectors.groupingBy(video -> video.getModule().getId()));
            
            long endTime = System.currentTimeMillis();
            logger.info("✅ Batch found {} videos for {} modules in {}ms (optimized)", 
                allVideos.size(), moduleIds.size(), (endTime - startTime));
            
            return videosByModule;
        } catch (Exception e) {
            logger.warn("⚠️ Optimized batch method failed: {}", e.getMessage());
            // Fallback - process one by one
            Map<Long, List<Video>> result = new HashMap<>();
            for (Long moduleId : moduleIds) {
                try {
                    List<Video> videos = getVideosByModuleOptimized(moduleId, userId);
                    result.put(moduleId, videos);
                } catch (Exception ex) {
                    logger.error("Failed to fetch videos for module {}: {}", moduleId, ex.getMessage());
                }
            }
            return result;
        }
    }
    
    /**
     * ✅ NEW: Get video details with optimized query
     */
    public Video getVideoDetailsOptimized(Long videoId, Long userId) {
        logger.info("🔄 Getting video details for {} (optimized)", videoId);
        
        try {
            // Try to use optimized repository method
            Optional<Video> videoOpt = videoRepository.findVideoDetailsOptimized(videoId);
            
            if (videoOpt.isPresent()) {
                logger.info("✅ Found video details for {} (optimized)", videoId);
                return videoOpt.get();
            } else {
                throw new RuntimeException("Video not found with ID: " + videoId);
            }
        } catch (Exception e) {
            logger.warn("⚠️ Optimized method failed, falling back to standard: {}", e.getMessage());
            // FIXED: Use repository method directly, not service method
            return videoRepository.findByIdWithModuleAndCourse(videoId)
                    .orElseThrow(() -> new RuntimeException("Video not found with ID: " + videoId));
        }
    }
}