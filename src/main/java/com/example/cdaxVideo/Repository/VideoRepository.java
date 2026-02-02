package com.example.cdaxVideo.Repository;

import com.example.cdaxVideo.Entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {
    
    // ========== EXISTING METHODS (DON'T CHANGE) ==========
    
    // ✅ KEEP THIS - Used by other services
    List<Video> findByModuleId(Long moduleId);
    
    // ✅ KEEP THIS - Used by other services  
    @Query("SELECT COUNT(v) FROM Video v " +
           "JOIN v.module m " +
           "WHERE m.course.id = :courseId")
    Long countByCourseId(@Param("courseId") Long courseId);
    
    // ✅ KEEP THIS - Used by VideoService
    @Query("SELECT DISTINCT v FROM Video v " +
           "LEFT JOIN FETCH v.module m " +
           "WHERE v.module.id = :moduleId " +
           "ORDER BY v.displayOrder ASC")
    List<Video> findByModuleIdWithModule(@Param("moduleId") Long moduleId);
    
    // ✅ KEEP THIS - Used by VideoService
    @Query("SELECT DISTINCT v FROM Video v " +
           "LEFT JOIN FETCH v.module m " +
           "LEFT JOIN FETCH m.course c " +
           "WHERE v.id = :videoId")
    Optional<Video> findByIdWithModuleAndCourse(@Param("videoId") Long videoId);
    
    // ✅ KEEP THIS - Used by VideoService
    @Query("SELECT DISTINCT v FROM Video v " +
           "LEFT JOIN FETCH v.module m " +
           "WHERE m.id IN :moduleIds " +
           "ORDER BY m.id, v.displayOrder ASC")
    List<Video> findByModuleIdsWithModule(@Param("moduleIds") List<Long> moduleIds);
    
    // ✅ KEEP THIS - Used by VideoService
    @Query("SELECT v FROM Video v " +
           "WHERE v.id = :videoId")
    Optional<Video> findByIdBasic(@Param("videoId") Long videoId);
    
    // ✅ KEEP THIS - Used by other services
    @Query("SELECT v FROM Video v " +
           "WHERE v.module.id = :moduleId " +
           "ORDER BY v.displayOrder ASC " +
           "LIMIT 1")
    Optional<Video> findFirstByModuleId(@Param("moduleId") Long moduleId);
    
    // ✅ KEEP THIS - Used by other services
    @Query("SELECT v FROM Video v " +
           "WHERE v.module.id = :moduleId " +
           "AND v.displayOrder > :currentOrder " +
           "ORDER BY v.displayOrder ASC " +
           "LIMIT 1")
    Optional<Video> findNextVideo(@Param("moduleId") Long moduleId, 
                                  @Param("currentOrder") Integer currentOrder);
    
    // ✅ KEEP THIS - Used by other services
    @Query("SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END " +
           "FROM Video v WHERE v.id = :videoId AND v.module.id = :moduleId")
    boolean existsByIdAndModuleId(@Param("videoId") Long videoId, 
                                  @Param("moduleId") Long moduleId);
    
    // ========== NEW OPTIMIZED METHODS (ADD THESE) ==========
    
    // ✅ NEW: Get videos with module AND course (Fixes N+1)
    @Query("SELECT DISTINCT v FROM Video v " +
           "LEFT JOIN FETCH v.module m " +
           "LEFT JOIN FETCH m.course c " +
           "WHERE m.id = :moduleId " +
           "ORDER BY v.displayOrder ASC")
    List<Video> findByModuleIdWithModuleAndCourse(@Param("moduleId") Long moduleId);
    
    // ✅ NEW: Get videos for multiple modules with all joins
    @Query("SELECT DISTINCT v FROM Video v " +
           "LEFT JOIN FETCH v.module m " +
           "LEFT JOIN FETCH m.course c " +
           "WHERE m.id IN :moduleIds " +
           "ORDER BY m.id, v.displayOrder ASC")
    List<Video> findByModuleIdsWithModuleAndCourse(@Param("moduleIds") List<Long> moduleIds);
    
    // ✅ NEW: Simple find with ordering (no joins)
    List<Video> findByModuleIdOrderByDisplayOrderAsc(Long moduleId);

    // Add these methods to your existing VideoRepository.java

// ✅ NEW: Optimized method to fix N+1 queries
@Query("SELECT DISTINCT v FROM Video v " +
       "LEFT JOIN FETCH v.module m " +
       "LEFT JOIN FETCH m.course c " +
       "WHERE m.id = :moduleId " +
       "ORDER BY v.displayOrder ASC")
List<Video> findVideosByModuleIdOptimized(@Param("moduleId") Long moduleId);

// ✅ NEW: Batch optimized method
@Query("SELECT DISTINCT v FROM Video v " +
       "LEFT JOIN FETCH v.module m " +
       "LEFT JOIN FETCH m.course c " +
       "WHERE m.id IN :moduleIds " +
       "ORDER BY m.id, v.displayOrder ASC")
List<Video> findVideosByModuleIdsOptimized(@Param("moduleIds") List<Long> moduleIds);

// ✅ NEW: Optimized video details with all joins
@Query("SELECT DISTINCT v FROM Video v " +
       "LEFT JOIN FETCH v.module m " +
       "LEFT JOIN FETCH m.course c " +
       "LEFT JOIN FETCH c.instructor i " +
       "WHERE v.id = :videoId")
Optional<Video> findVideoDetailsOptimized(@Param("videoId") Long videoId);
}