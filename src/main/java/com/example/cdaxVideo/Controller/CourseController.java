    package com.example.cdaxVideo.Controller;

    import com.example.cdaxVideo.DTO.CourseResponseDTO;
    import com.example.cdaxVideo.DTO.ModuleResponseDTO;
    import com.example.cdaxVideo.DTO.StreakDayDTO;
    import com.example.cdaxVideo.DTO.StreakSummaryDTO;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import com.example.cdaxVideo.DTO.VideoResponseDTO;
    import com.example.cdaxVideo.Entity.*;
    import com.example.cdaxVideo.Repository.CourseRepository;
    import com.example.cdaxVideo.Repository.UserVideoProgressRepository;
    import com.example.cdaxVideo.Entity.Module;
    import com.example.cdaxVideo.Repository.UserCoursePurchaseRepository;
    import com.example.cdaxVideo.Service.CourseService;
    import com.example.cdaxVideo.Service.StreakService;
    import java.time.LocalDate;
    import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;


import java.util.*;
    import java.util.stream.Collectors;

    @RestController
    @RequestMapping("/api")
    public class CourseController {

            @PersistenceContext
private EntityManager entityManager;
        @Autowired
        private CourseService courseService;

        private static final Logger logger = LoggerFactory.getLogger(CourseController.class);

        @Autowired
        private UserCoursePurchaseRepository userCoursePurchaseRepository; 



        @Autowired
        private UserVideoProgressRepository userVideoProgressRepository; 

        @Autowired
        private CourseRepository courseRepository; 

        @Autowired
        private StreakService streakService;

        // ---------------------- COURSE APIs ----------------------
        @PostMapping("/courses")
        public ResponseEntity<Course> createCourse(@RequestBody Course course) {
            return ResponseEntity.ok(courseService.saveCourse(course));
        }

        @GetMapping("/dashboard/courses")
        public ResponseEntity<Map<String, Object>> getDashboardCourses(@RequestParam Long userId) {
            Map<String, Object> response = new HashMap<>();
            response.put("data", courseService.getCoursesForUser(userId));
            return ResponseEntity.ok(response);
        }

        @GetMapping("/courses")
        public ResponseEntity<Map<String, Object>> getCourses(
                @RequestParam(required = false) Long userId,
                @RequestParam(required = false) String search
        ) {
            List<Course> courses;

            if (search != null && !search.trim().isEmpty()) {
                // FIXED: Use enhancedSearch instead of searchCourses
                courses = courseService.enhancedSearch(search);
            } else {
                courses = courseService.getAllCoursesWithModulesAndVideos();
            }

            if (userId != null) {
                for (Course course : courses) {
                    boolean isPurchased = userCoursePurchaseRepository
                            .existsByUserIdAndCourseId(userId, course.getId());
                    course.setPurchased(isPurchased);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("data", courses);
            return ResponseEntity.ok(response);
        }

        // FIXED: Advanced search endpoint - added missing parameters
        @GetMapping("/courses/advanced-search")
        public ResponseEntity<Map<String, Object>> advancedSearch(
                @RequestParam(required = false) Long userId,
                @RequestParam(required = false) String search,
                @RequestParam(required = false) String category,
                @RequestParam(required = false) String level,
                @RequestParam(required = false) Double minPrice,
                @RequestParam(required = false) Double maxPrice,
                @RequestParam(required = false) Double minRating
        ) {
            List<Course> courses = courseService.advancedSearch(
                search, category, minPrice, maxPrice, minRating, level);
            
            if (userId != null) {
                for (Course course : courses) {
                    boolean isPurchased = userCoursePurchaseRepository
                            .existsByUserIdAndCourseId(userId, course.getId());
                    course.setPurchased(isPurchased);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("data", courses);
            return ResponseEntity.ok(response);
        }

        // FIXED: Get search suggestions endpoint
        @GetMapping("/courses/search/suggestions")
        public ResponseEntity<Map<String, Object>> getSearchSuggestions(
                @RequestParam String query
        ) {
            List<String> suggestions = courseService.getSearchSuggestions(query);
            
            Map<String, Object> response = new HashMap<>();
            response.put("suggestions", suggestions);
            return ResponseEntity.ok(response);
        }

        // FIXED: Get popular tags endpoint
        @GetMapping("/courses/tags/popular")
        public ResponseEntity<Map<String, Object>> getPopularTags() {
            List<String> tags = courseService.getPopularTags();
            
            Map<String, Object> response = new HashMap<>();
            response.put("tags", tags);
            return ResponseEntity.ok(response);
        }

        // FIXED: Get courses by tag endpoint
        @GetMapping("/courses/tag/{tagName}")
        public ResponseEntity<Map<String, Object>> getCoursesByTag(
                @PathVariable String tagName,
                @RequestParam(required = false) Long userId
        ) {
            List<Course> courses = courseService.getCoursesByTag(tagName);
            
            if (userId != null) {
                for (Course course : courses) {
                    boolean isPurchased = userCoursePurchaseRepository
                            .existsByUserIdAndCourseId(userId, course.getId());
                    course.setPurchased(isPurchased);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("data", courses);
            return ResponseEntity.ok(response);
        }


@GetMapping("/courses/{id}")
public ResponseEntity<Map<String, Object>> getCourse(
        @PathVariable Long id,
        @RequestParam Long userId) {
    
    try {
        Course course = courseService.getCourseForUser(userId, id);
        
        // Create response without circular references
        Map<String, Object> response = new HashMap<>();
        response.put("id", course.getId());
        response.put("title", course.getTitle());
        response.put("description", course.getDescription());
        response.put("thumbnailUrl", course.getThumbnailUrl());
        response.put("instructor", course.getInstructor());
        response.put("isPurchased", course.isPurchased());
        response.put("isSubscribed", course.isSubscribed());
        
        // Add modules without circular references
        if (course.getModules() != null && !course.getModules().isEmpty()) {
            List<Map<String, Object>> modules = course.getModules().stream()
                .map(module -> {
                    Map<String, Object> moduleMap = new HashMap<>();
                    moduleMap.put("id", module.getId());
                    moduleMap.put("title", module.getTitle());
                    moduleMap.put("durationSec", module.getDurationSec());
                    moduleMap.put("isLocked", module.isLocked());
                    moduleMap.put("assessmentLocked", module.isAssessmentLocked());
                    
                    // Add videos without circular references
                    if (module.getVideos() != null) {
                        List<Map<String, Object>> videos = module.getVideos().stream()
                            .map(video -> {
                                Map<String, Object> videoMap = new HashMap<>();
                                videoMap.put("id", video.getId());
                                videoMap.put("title", video.getTitle());
                                videoMap.put("duration", video.getDuration());
                                videoMap.put("isLocked", video.isLocked());
                                videoMap.put("isCompleted", video.isCompleted());
                                videoMap.put("displayOrder", video.getDisplayOrder());
                                videoMap.put("isPreview", video.getIsPreview());
                                // ✅ ADD THESE TWO CRITICAL FIELDS:
                                videoMap.put("videoUrl", video.getVideoUrl());
                                videoMap.put("youtubeId", video.getYoutubeId());
                                return videoMap;
                            })
                            .collect(Collectors.toList());
                        moduleMap.put("videos", videos);
                    }
                    
                    return moduleMap;
                })
                .collect(Collectors.toList());
            response.put("modules", modules);
        }
        
        Map<String, Object> finalResponse = new HashMap<>();
        finalResponse.put("data", response);
        return ResponseEntity.ok(finalResponse);
        
    } catch (Exception e) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

        @GetMapping("/courses/subscribed/{userId}")
        @Transactional(readOnly = true) 
        public ResponseEntity<List<Course>> getSubscribedCourses(
                @PathVariable Long userId) {
            List<Course> courses = courseService.getSubscribedCourses(userId);
            return ResponseEntity.ok(courses);
        }
        
// In CourseController.java
@GetMapping("/courses/public")
public ResponseEntity<Map<String, Object>> getPublicCourses() {
    System.out.println("\n📚 GET /api/courses/public called");
    
    try {
        // Get courses from service
        List<Course> courses = courseService.getPublicCourses();
        
        // Create response object
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", courses);
        response.put("total", courses.size());
        response.put("userAuthenticated", false);
        response.put("message", "Public courses retrieved successfully");
        response.put("timestamp", new Date());
        
        System.out.println("   ✅ Returning " + courses.size() + " public courses");
        return ResponseEntity.ok(response);
        
    } catch (Exception e) {
        System.out.println("❌ Error in /courses/public endpoint: " + e.getMessage());
        e.printStackTrace();
        
        // Error response
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", "Failed to retrieve public courses: " + e.getMessage());
        errorResponse.put("userAuthenticated", false);
        errorResponse.put("timestamp", new Date());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}

        // ---------------------- MODULE APIs ----------------------
        @PostMapping("/modules")
        public ResponseEntity<?> addModule(
                @RequestParam("courseId") Long courseId,
                @RequestBody Module module) {
            try {
                return ResponseEntity.ok(courseService.saveModule(courseId, module));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }
@GetMapping("/modules/course/{courseId}")
@Transactional(readOnly = true) 
public ResponseEntity<Map<String, Object>> getModulesByCourse(
        @PathVariable Long courseId,
        @RequestParam Long userId) {

    try {
        // 1️⃣ Fetch course
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        // 2️⃣ Check if user purchased the course
        boolean isPurchased = userCoursePurchaseRepository
                .existsByUserIdAndCourseId(userId, courseId);

        // 3️⃣ Build response ALWAYS
        Map<String, Object> response = new HashMap<>();
        response.put("courseId", course.getId());
        response.put("courseTitle", course.getTitle());
        response.put("isPurchased", isPurchased);

        // 4️⃣ Create CourseResponseDTO with modules (ALWAYS)
        CourseResponseDTO courseDTO = new CourseResponseDTO(course);
        courseDTO.setIsPurchased(isPurchased);
        courseDTO.setIsSubscribed(isPurchased);

        // 5️⃣ Apply progress if purchased, otherwise set defaults
        if (isPurchased) {
            applyUserVideoProgress(courseDTO, userId, true);
        } else {
            // For non-purchased users:
            // - First module unlocked
            // - First 3 videos of first module unlocked
            // - Everything else locked
            if (courseDTO.getModules() != null && !courseDTO.getModules().isEmpty()) {
                for (int i = 0; i < courseDTO.getModules().size(); i++) {
                    ModuleResponseDTO module = courseDTO.getModules().get(i);
                    
                    // First module is unlocked for non-purchased users
                    module.setIsLocked(i > 0);
                    
                    // Handle videos in first module
                    if (i == 0 && module.getVideos() != null) {
                        for (int j = 0; j < module.getVideos().size(); j++) {
                            VideoResponseDTO video = module.getVideos().get(j);
                            // First video unlocked, others locked
                            video.setIsLocked(j > 0);
                            video.setIsCompleted(false);
                        }
                    } else {
                        // Other modules: all videos locked
                        if (module.getVideos() != null) {
                            for (VideoResponseDTO video : module.getVideos()) {
                                video.setIsLocked(true);
                                video.setIsCompleted(false);
                            }
                        }
                    }
                }
            }
        }

        // 6️⃣ Always add modules to response
        response.put("modules", courseDTO.getModules());
        response.put("totalModules", courseDTO.getTotalModules());
        response.put("totalVideos", courseDTO.getTotalVideos());
        
        if (isPurchased) {
            // Add progress stats for purchased users
            response.put("completedModules", courseDTO.getCompletedModules());
            response.put("completedVideos", courseDTO.getCompletedVideos());
            response.put("progressPercent", courseDTO.getProgressPercent());
            response.put("isCompleted", courseDTO.getIsCompleted());
        }

        return ResponseEntity.ok(response);

    } catch (Exception e) {
        e.printStackTrace();
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}


private void applyUserVideoProgress(CourseResponseDTO courseDTO, Long userId, boolean isCoursePurchased) {
    System.out.println("=== APPLY USER VIDEO PROGRESS ===");
    System.out.println("User ID: " + userId);
    System.out.println("Course purchased: " + isCoursePurchased);
    System.out.println("Total modules: " + courseDTO.getModules().size());
    
    // Get user's video progress from database
    List<UserVideoProgress> userProgress = userVideoProgressRepository.findByUserId(userId);
    System.out.println("User progress records found: " + userProgress.size());
    
    // Create a map for quick lookup: videoId -> progress
    Map<Long, UserVideoProgress> progressMap = userProgress.stream()
        .collect(Collectors.toMap(
            up -> up.getVideo().getId(),
            up -> up
        ));
    
    // Track if previous module was completed
    boolean previousModuleCompleted = true;
    
    // Track overall course stats
    int totalCourseVideos = 0;
    int totalCompletedCourseVideos = 0;
    int totalCompletedModules = 0;
    
    // Apply progress to videos in DTO
    for (int moduleIndex = 0; moduleIndex < courseDTO.getModules().size(); moduleIndex++) {
        ModuleResponseDTO module = courseDTO.getModules().get(moduleIndex);
        System.out.println("\nProcessing module [" + moduleIndex + "]: " + 
                          module.getTitle() + " (ID: " + module.getId() + ")");
        
        // Determine if this is the first module
        boolean isFirstModule = moduleIndex == 0;
        
        System.out.println("  Is first module: " + isFirstModule);
        System.out.println("  Is course purchased: " + isCoursePurchased);
        
        if (isCoursePurchased) {
            // If course is purchased, check module sequence
            if (isFirstModule) {
                // First module is always unlocked for purchased courses
                module.setIsLocked(false);
                System.out.println("  ✓ First module unlocked (course purchased)");
            } else {
                // For subsequent modules, check if previous module was completed
                module.setIsLocked(!previousModuleCompleted);
                System.out.println("  Module locked status: " + (!previousModuleCompleted) + 
                                " (previous module completed: " + previousModuleCompleted + ")");
            }
        } else {
            // If course not purchased, only first module is unlocked
            module.setIsLocked(!isFirstModule);
            System.out.println("  Module locked (course not purchased): " + (!isFirstModule));
        }
        
        // Initialize module video counters
        int moduleTotalVideos = 0;
        int moduleCompletedVideos = 0;
        boolean moduleHasUnlockedVideo = false;
        boolean allVideosCompleted = true;
        
        if (module.getVideos() != null) {
            System.out.println("  Module has " + module.getVideos().size() + " videos");
            moduleTotalVideos = module.getVideos().size();
            totalCourseVideos += moduleTotalVideos;
            
            // FOR DEBUGGING: Print all video details
            System.out.println("  Video list:");
            for (VideoResponseDTO video : module.getVideos()) {
                System.out.println("    - ID: " + video.getId() + 
                                 ", Title: " + video.getTitle() + 
                                 ", DisplayOrder: " + video.getDisplayOrder() + 
                                 ", Current isLocked: " + video.getIsLocked());
            }
            
            for (VideoResponseDTO video : module.getVideos()) {
                System.out.println("    Processing video: " + video.getTitle() + 
                                 " (ID: " + video.getId() + 
                                 ", DisplayOrder: " + video.getDisplayOrder() + ")");
                
                // SPECIAL LOGIC FOR SUBSCRIBED COURSES: Unlock first 3 videos of first module
                if (isCoursePurchased && isFirstModule && video.getDisplayOrder() <= 3) {
                    System.out.println("    ✓ Special rule: First 3 videos of first module for subscribed course");
                    video.setIsLocked(false);
                    
                    UserVideoProgress progress = progressMap.get(video.getId());
                    if (progress != null) {
                        System.out.println("      Progress exists - Completed: " + progress.isCompleted());
                        video.setIsCompleted(progress.isCompleted());
                        if (progress.isCompleted()) {
                            moduleCompletedVideos++;
                            totalCompletedCourseVideos++;
                        }
                    } else {
                        System.out.println("      No progress record, marking as not completed");
                        video.setIsCompleted(false);
                    }
                    continue; // Skip the rest of the logic for these videos
                }
                
                UserVideoProgress progress = progressMap.get(video.getId());
                if (progress != null) {
                    System.out.println("      Progress found - Unlocked: " + progress.isUnlocked() + 
                                    ", Completed: " + progress.isCompleted());
                    
                    video.setIsLocked(!progress.isUnlocked());
                    video.setIsCompleted(progress.isCompleted());
                    
                    if (progress.isUnlocked()) {
                        moduleHasUnlockedVideo = true;
                    }
                    
                    // Count completed videos
                    if (progress.isCompleted()) {
                        moduleCompletedVideos++;
                        totalCompletedCourseVideos++;
                    }
                    
                    if (!progress.isCompleted()) {
                        allVideosCompleted = false;
                    }
                } else {
                    System.out.println("      No progress record found");
                    
                    // Apply default logic based on module and purchase status
                    boolean isFirstVideo = video.getDisplayOrder() == 1;
                    
                    System.out.println("      Is first video (displayOrder==1): " + isFirstVideo);
                    System.out.println("      Module isLocked: " + module.getIsLocked());
                    
                    if (Boolean.FALSE.equals(module.getIsLocked()) && isFirstVideo) {
                        // First video of an unlocked module is unlocked
                        video.setIsLocked(false);
                        moduleHasUnlockedVideo = true;
                        System.out.println("      ✓ First video unlocked (module unlocked)");
                    } else {
                        video.setIsLocked(true);
                        System.out.println("      ✗ Video locked (not first video or module locked)");
                    }
                    video.setIsCompleted(false);
                    allVideosCompleted = false;
                }
            }
        }
        
        // Set module stats using the helper method from ModuleResponseDTO
        module.calculateStatsFromVideos();
        
        // Override with our calculated values
        module.setCompletedVideos(moduleCompletedVideos);
        module.setTotalVideos(moduleTotalVideos);
        
        // Calculate progress percentage
        int moduleProgressPercent = moduleTotalVideos > 0 ? 
            (moduleCompletedVideos * 100) / moduleTotalVideos : 0;
        module.setProgressPercent(moduleProgressPercent);
        
        // Determine if module is completed
        boolean moduleCompleted = Boolean.FALSE.equals(module.getIsLocked()) && 
                                 moduleTotalVideos > 0 && 
                                 moduleCompletedVideos == moduleTotalVideos;
        module.setIsCompleted(moduleCompleted);
        
        if (moduleCompleted) {
            totalCompletedModules++;
        }
        
        System.out.println("  Module stats - Completed: " + moduleCompletedVideos + 
                         "/" + moduleTotalVideos + " videos, Progress: " + 
                         moduleProgressPercent + "%");
        
        // Update module completion tracking
        previousModuleCompleted = Boolean.FALSE.equals(module.getIsLocked()) && allVideosCompleted;
        System.out.println("  Module all videos completed: " + allVideosCompleted);
        System.out.println("  Previous module completed for next: " + previousModuleCompleted);
        
        // If module is locked but has an unlocked video from progress, unlock the module
        if (Boolean.TRUE.equals(module.getIsLocked()) && moduleHasUnlockedVideo) {
            module.setIsLocked(false);
            System.out.println("  ✓ Module unlocked (has unlocked videos from progress)");
        }
    }
    
    // Set course-level stats in CourseResponseDTO
    int courseProgressPercent = totalCourseVideos > 0 ? 
        (totalCompletedCourseVideos * 100) / totalCourseVideos : 0;
    
    // Update the CourseResponseDTO fields
    courseDTO.setTotalVideos(totalCourseVideos);
    courseDTO.setCompletedVideos(totalCompletedCourseVideos);
    courseDTO.setCompletedModules(totalCompletedModules);  // This is the key field!
    courseDTO.setProgressPercent((double) courseProgressPercent);
    courseDTO.setIsCompleted(totalCompletedCourseVideos == totalCourseVideos && totalCourseVideos > 0);
    
    System.out.println("\n=== COURSE STATS ===");
    System.out.println("Total videos: " + totalCourseVideos);
    System.out.println("Completed videos: " + totalCompletedCourseVideos);
    System.out.println("Completed modules: " + totalCompletedModules);
    System.out.println("Course progress: " + courseProgressPercent + "%");
}

@GetMapping("/modules/{id}")
@Transactional(readOnly = true)
public ResponseEntity<?> getModule(@PathVariable Long id) {
    try {
        Optional<Module> moduleOpt = courseService.getModuleById(id);
        
        if (moduleOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Module module = moduleOpt.get();
        
        // Create response without circular references
        Map<String, Object> response = new HashMap<>();
        response.put("id", module.getId());
        response.put("title", module.getTitle());
        response.put("durationSec", module.getDurationSec());
        
        if (module.getCourse() != null) {
            response.put("courseId", module.getCourse().getId());
            response.put("courseTitle", module.getCourse().getTitle());
        }
        
        // Add videos without circular references
        if (module.getVideos() != null && !module.getVideos().isEmpty()) {
            List<Map<String, Object>> videos = module.getVideos().stream()
                .map(video -> {
                    Map<String, Object> videoMap = new HashMap<>();
                    videoMap.put("id", video.getId());
                    videoMap.put("title", video.getTitle());
                    videoMap.put("duration", video.getDuration());
                    videoMap.put("displayOrder", video.getDisplayOrder());
                    videoMap.put("isPreview", video.getIsPreview());
                    // ✅ ADD THESE FIELDS:
                    videoMap.put("videoUrl", video.getVideoUrl());
                    videoMap.put("youtubeId", video.getYoutubeId());
                    // Don't include module to avoid circular reference
                    return videoMap;
                })
                .collect(Collectors.toList());
            response.put("videos", videos);
        }
        
        return ResponseEntity.ok(response);
        
    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", e.getMessage()));
    }
}


        // ---------------------- VIDEO APIs ----------------------
        @PostMapping("/videos")
        public ResponseEntity<?> addVideo(
                @RequestParam("moduleId") Long moduleId,
                @RequestBody Video video) {
            try {
                return ResponseEntity.ok(courseService.saveVideo(moduleId, video));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

@GetMapping("/modules/{moduleId}/videos")
@Transactional(readOnly = true)
public ResponseEntity<?> getVideosByModule(@PathVariable Long moduleId) {
    try {
        List<Video> videos = courseService.getVideosByModuleId(moduleId);
        System.out.println("✅ Found " + videos.size() + " videos");
        
        // Simple response to avoid lazy loading
        List<Map<String, Object>> response = new ArrayList<>();
        for (Video v : videos) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", v.getId());
            map.put("title", v.getTitle());
            map.put("duration", v.getDuration());
            map.put("displayOrder", v.getDisplayOrder());
            map.put("isPreview", v.getIsPreview());
            map.put("videoUrl", v.getVideoUrl());
            map.put("youtubeId", v.getYoutubeId());
            response.add(map);
        }
        
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
    }
}

        // @GetMapping("/modules/{moduleId}/assessments")
        // public ResponseEntity<List<Assessment>> getAssessmentsByModule(@PathVariable Long moduleId) {
        //     return ResponseEntity.ok(courseService.getAssessmentsByModuleId(moduleId));
        // }

@GetMapping("/modules/{moduleId}/assessments")
@Transactional(readOnly = true)
public ResponseEntity<?> getAssessmentsByModule(@PathVariable Long moduleId) {
    try {
        List<Assessment> assessments = courseService.getAssessmentsByModuleId(moduleId);
        System.out.println("✅ Found " + assessments.size() + " assessments");
        
        // Simple response to avoid lazy loading
        List<Map<String, Object>> response = new ArrayList<>();
        for (Assessment a : assessments) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", a.getId());
            map.put("title", a.getTitle());
            map.put("totalMarks", a.getTotalMarks());
            map.put("totalQuestions", a.getTotalQuestions());
            response.add(map);
        }
        
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
    }
}

        // ---------------------- QUESTION APIs ----------------------
        @PostMapping("/questions")
        public ResponseEntity<?> addQuestion(
                @RequestParam("assessmentId") Long assessmentId,
                @RequestBody Question question) {
            try {
                return ResponseEntity.ok(courseService.saveQuestion(assessmentId, question));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(e.getMessage());
            }
        }

        @GetMapping("/assessments/{assessmentId}/questions")
        public ResponseEntity<Map<String, Object>> getQuestionsByAssessment(@PathVariable Long assessmentId) {
            Map<String, Object> response = new HashMap<>();
            response.put("assessmentId", assessmentId);
            response.put("questions", courseService.getQuestionsByAssessmentId(assessmentId));
            return ResponseEntity.ok(response);

        }
        //-----------------------------Submit Assessment and Check its Status-----------------------

        
        @PostMapping("/course/assessment/submit")
        public ResponseEntity<?> submitAssessment(
                @RequestParam Long userId,
                @RequestParam Long assessmentId,
                @RequestBody Map<Long, String> answers) {
                    
            
            try {
                Map<String, Object> result = courseService.submitAssessment(userId, assessmentId, answers);
                return ResponseEntity.ok(result);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
                ));
            }
        }
        
        @GetMapping("/course/assessment/status")
        public ResponseEntity<?> getAssessmentStatus(
                @RequestParam Long userId,
                @RequestParam Long assessmentId) {
            
            try {
                Map<String, Object> status = courseService.getAssessmentStatus(userId, assessmentId);
                return ResponseEntity.ok(status);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
                ));
            }
        }



        // Add this endpoint to your CourseController
    @GetMapping("/course/assessment/questions")
    public ResponseEntity<?> getAssessmentQuestions(
            @RequestParam Long userId,
            @RequestParam Long assessmentId) {
        
        try {
            Map<String, Object> assessmentData = courseService.getAssessmentWithQuestions(userId, assessmentId);
            return ResponseEntity.ok(assessmentData);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    // Add this to CourseController
@GetMapping("/course/assessment/can-attempt")
public ResponseEntity<?> canAttemptAssessment(
        @RequestParam(required = false) Long assessmentId,
        @RequestParam(required = false) Long userId) {
    
    // Handle OPTIONS pre-flight request
    if (assessmentId == null) {
        return ResponseEntity.ok(Map.of(
            "message", "CORS pre-flight check",
            "status", "ok"
        ));
    }
    
    try {
        System.out.println("🔍 /course/assessment/can-attempt called:");
        System.out.println("   ├─ Assessment ID: " + assessmentId);
        System.out.println("   ├─ User ID: " + userId);
        
        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "User ID is required"
            ));
        }
        
        boolean canAttempt = courseService.canAttemptAssessment(userId, assessmentId);
        
        System.out.println("   ✅ Service result: " + canAttempt);
        
        return ResponseEntity.ok(Map.of(
            "canAttempt", canAttempt,
            "success", true
        ));
        
    } catch (Exception e) {
        System.out.println("❌ Error in canAttemptAssessment: " + e.getMessage());
        e.printStackTrace();
        
        return ResponseEntity.badRequest().body(Map.of(
            "success", false,
            "error", e.getMessage()
        ));
    }
}


        // ---------------------- PURCHASE / UNLOCK / PROGRESS APIs ----------------------
        @PostMapping("/purchase")
        public ResponseEntity<Map<String, Object>> purchaseCourse(
                @RequestParam Long userId,
                @RequestParam Long courseId) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("message", courseService.purchaseCourse(userId, courseId));
            return ResponseEntity.ok(resp);
        }
        
        @PostMapping("/modules/{moduleId}/unlock-assessment")
        public ResponseEntity<Map<String,Object>> unlockAssessment(
                @PathVariable Long moduleId,
                @RequestParam Long userId) {
            Map<String,Object> resp = new HashMap<>();
            resp.put("success", courseService.unlockAssessmentForModule(userId, moduleId));
            return ResponseEntity.ok(resp);
        }

        @PostMapping("/modules/{moduleId}/unlock-next")
        public ResponseEntity<Map<String,Object>> unlockNextModule(
                @PathVariable Long moduleId,
                @RequestParam Long userId,
                @RequestParam Long courseId) {
            Map<String,Object> resp = new HashMap<>();
            resp.put("success", courseService.unlockNextModuleForUser(userId, courseId, moduleId));
            return ResponseEntity.ok(resp);
        }

// In CourseController.java

// Update this existing endpoint in your CourseController.java
@Transactional(readOnly = true)
@GetMapping("/streak/course/{courseId}")
public ResponseEntity<?> getCourseStreak(
        @PathVariable Long courseId,
        @RequestParam Long userId,
        @RequestParam(required = false) String month) {  // NEW: Optional month parameter
    
    try {
        logger.info("📊 Getting streak for user: {}, course: {}, month: {}", 
                   userId, courseId, month != null ? month : "30-day");
        
        StreakSummaryDTO streakSummary;
        
        if (month != null && !month.isEmpty()) {
            // Parse month parameter
            String[] parts = month.split("-");
            if (parts.length >= 2) {
                int year = Integer.parseInt(parts[0]);
                int monthNum = Integer.parseInt(parts[1]);
                LocalDate monthDate = LocalDate.of(year, monthNum, 15);
                
                // Use month-based streak
                streakSummary = streakService.getCourseStreakForMonth(userId, courseId, monthDate);
            } else {
                throw new IllegalArgumentException("Invalid month format. Use YYYY-MM");
            }
        } else {
            // Use existing 30-day streak
            streakSummary = streakService.getCourseStreak(userId, courseId);
        }
        
        return ResponseEntity.ok(streakSummary);
        
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
            "success", false,
            "error", e.getMessage()
        ));
    }
}

@Transactional(readOnly = true)
@GetMapping("/streak/overview")
public ResponseEntity<?> getUserStreakOverview(@RequestParam Long userId) {
    try {
        Map<String, Object> streakOverview = streakService.getUserStreakOverview(userId);
        return ResponseEntity.ok(streakOverview);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
            "success", false,
            "error", e.getMessage()
        ));
    }
}

@Transactional(readOnly = true)
@GetMapping("/streak/day/{courseId}")
public ResponseEntity<?> getDayDetails(
        @PathVariable Long courseId,
        @RequestParam Long userId,
        @RequestParam String date) {
    
    try {
        LocalDate localDate = LocalDate.parse(date);
        StreakDayDTO dayDetails = streakService.getDayDetails(userId, courseId, localDate);
        return ResponseEntity.ok(dayDetails);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
            "success", false,
            "error", e.getMessage()
        ));
    }
}

// Update the existing profile/streak endpoint
// Update this endpoint to support month parameter
@Transactional(readOnly = true)
@GetMapping("/profile/streak")
public ResponseEntity<?> getStreak(
        @RequestParam Long userId,
        @RequestParam(required = false) Long courseId,
        @RequestParam(required = false) String month) {  // NEW: Optional month parameter
    
    try {
        if (courseId != null) {
            // Course-specific streak
            StreakSummaryDTO streak;
            
            if (month != null && !month.isEmpty()) {
                // Parse month parameter for month view
                String[] parts = month.split("-");
                if (parts.length >= 2) {
                    int year = Integer.parseInt(parts[0]);
                    int monthNum = Integer.parseInt(parts[1]);
                    LocalDate monthDate = LocalDate.of(year, monthNum, 15);
                    
                    // Use month-based streak
                    streak = streakService.getCourseStreakForMonth(userId, courseId, monthDate);
                } else {
                    throw new IllegalArgumentException("Invalid month format. Use YYYY-MM");
                }
            } else {
                // Use existing 30-day streak
                streak = streakService.getCourseStreak(userId, courseId);
            }
            
            return ResponseEntity.ok(streak);
        } else {
            // Overview for all courses (only 30-day view available for overview)
            Map<String, Object> overview = streakService.getUserStreakOverview(userId);
            return ResponseEntity.ok(overview);
        }
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
            "success", false,
            "error", e.getMessage()
        ));
    }
}

// Add this new endpoint to your CourseController.java
@Transactional(readOnly = true)
@GetMapping("/streak/course/{courseId}/month")
public ResponseEntity<?> getCourseStreakForMonth(
        @PathVariable Long courseId,
        @RequestParam Long userId,
        @RequestParam(required = false) String month,  // Format: "2024-01" or "2024-01-15"
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) Integer monthNum) {
    
    try {
        logger.info("📅 Request for month streak - User: {}, Course: {}, Month: {}", 
                   userId, courseId, month);
        
        LocalDate monthDate;
        
        // Determine which parameter to use
        if (month != null && !month.isEmpty()) {
            // Parse month string (format: "2024-01" or "2024-01-15")
            String[] parts = month.split("-");
            if (parts.length >= 2) {
                int yearValue = Integer.parseInt(parts[0]);
                int monthValue = Integer.parseInt(parts[1]);
                int dayValue = parts.length > 2 ? Integer.parseInt(parts[2]) : 15; // Default to middle of month
                monthDate = LocalDate.of(yearValue, monthValue, dayValue);
            } else {
                throw new IllegalArgumentException("Invalid month format. Use YYYY-MM or YYYY-MM-DD");
            }
        } else if (year != null && monthNum != null) {
            // Use year and monthNum parameters
            monthDate = LocalDate.of(year, monthNum, 15); // Middle of month
        } else {
            // Default to current month
            monthDate = LocalDate.now();
        }
        
        logger.info("📅 Processing streak for month: {}-{}", monthDate.getYear(), monthDate.getMonth());
        
        // Call the month-based service method
        StreakSummaryDTO streakSummary = streakService.getCourseStreakForMonth(
            userId, courseId, monthDate);
        
        return ResponseEntity.ok(streakSummary);
        
    } catch (Exception e) {
        logger.error("❌ Error getting month streak: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "success", false,
            "error", "Failed to get streak data for month: " + e.getMessage(),
            "timestamp", new Date()
        ));
    }
}


    // In CourseController.java

    @GetMapping("/user/{userId}/progress/overall")
    public ResponseEntity<?> getUserOverallProgress(@PathVariable Long userId) {
        try {
            Map<String, Object> progress = courseService.getUserOverallProgress(userId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/course/{courseId}/progress")
    public ResponseEntity<?> getCourseProgress(
            @PathVariable Long courseId,
            @RequestParam Long userId) {
        
        try {
            Map<String, Object> progress = courseService.calculateCourseProgress(userId, courseId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/dashboard/stats")
        public ResponseEntity<?> getDashboardStats(
                @RequestParam Long userId,
                @RequestParam(required = false) Long courseId) {
            
            try {
                System.out.println("📊 Dashboard stats request:");
                System.out.println("   ├─ User ID: " + userId);
                System.out.println("   ├─ Course ID: " + (courseId != null ? courseId : "Not specified"));
                
                // 1. Get overall progress
                Map<String, Object> overallProgress = courseService.getUserOverallProgress(userId);
                
                // 2. Get course-specific stats
                List<Map<String, Object>> courseStats = courseService.getUserCourseStats(userId);
                
                System.out.println("   ├─ Total courses found: " + courseStats.size());
                System.out.println("   ├─ Course stats available: " + (courseStats != null));
                
                // 3. Build response
                Map<String, Object> response = new HashMap<>();
                
                // Overall stats
                response.put("totalCourses", overallProgress.get("totalCourses"));
                response.put("completedCourses", overallProgress.get("completedCourses"));
                response.put("inProgressCourses", overallProgress.get("inProgressCourses"));
                response.put("totalVideos", overallProgress.get("totalVideos"));
                response.put("completedVideos", overallProgress.get("completedVideos"));
                response.put("overallProgress", overallProgress.get("overallVideoProgress"));
                response.put("completedModules", overallProgress.get("completedModules"));
                
                // Course breakdown
                response.put("courseStats", courseStats != null ? courseStats : new ArrayList<>());
                
                // 4. If specific course is selected, add its details
                if (courseId != null && courseStats != null) {
                    // Find the selected course in the list
                    Optional<Map<String, Object>> selectedCourse = courseStats.stream()
                        .filter(stat -> courseId.equals(stat.get("courseId")))
                        .findFirst();
                    
                    if (selectedCourse.isPresent()) {
                        response.put("selectedCourseId", courseId);
                        System.out.println("   └─ Selected course found: " + selectedCourse.get().get("courseTitle"));
                    } else {
                        System.out.println("   └─ Selected course not found in user's enrolled courses");
                    }
                }
                
                // Print debug info
                System.out.println("📊 Response structure:");
                System.out.println("   ├─ totalCourses: " + response.get("totalCourses"));
                System.out.println("   ├─ courseStats count: " + 
                    (response.get("courseStats") instanceof List ? ((List<?>)response.get("courseStats")).size() : "N/A"));
                
                return ResponseEntity.ok(response);
                
            } catch (Exception e) {
                System.err.println("❌ Error in dashboard stats: " + e.getMessage());
                e.printStackTrace();
                
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
                ));
            }
        }
    }

