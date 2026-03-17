package com.example.cdaxVideo.Repository;

import com.example.cdaxVideo.Entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    // ✅ Fetch only courses + modules (with ORDER BY)
    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.modules m ORDER BY m.id ASC")
    List<Course> findAllWithModules();

    // ✅ Fetch single course + modules (with ORDER BY)
    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.modules m WHERE c.id = :id ORDER BY m.id ASC")
    Optional<Course> findByIdWithModules(@Param("id") Long id);

    // ✅ FIXED: Use EXISTS subquery for collection navigation
    @Query("SELECT DISTINCT c FROM Course c " +
           "LEFT JOIN FETCH c.modules m " +
           "WHERE EXISTS (" +
           "  SELECT 1 FROM c.subscribedUsers u WHERE u.id = :userId" +
           ") " +
           "ORDER BY m.id ASC")
    List<Course> findBySubscribedUsers_Id(@Param("userId") Long userId);

    // Alternative: Simple Spring Data JPA method (if you don't need modules fetched)
    List<Course> findBySubscribedUsersId(Long userId);

    List<Course> findByTitleContainingIgnoreCase(String title);

    // ✅ FIXED: Search by tag EXACT match (case-insensitive)
    @Query("SELECT DISTINCT c FROM Course c JOIN c.tags t WHERE LOWER(t) = LOWER(:tag)")
    List<Course> findByTagExact(@Param("tag") String tag);

    // ✅ FIXED: Search by tag CONTAINS match (case-insensitive)
    @Query("SELECT DISTINCT c FROM Course c JOIN c.tags t WHERE LOWER(t) LIKE LOWER(CONCAT('%', :tag, '%'))")
    List<Course> findByTagContaining(@Param("tag") String tag);

    // ✅ FIXED: Search by title OR tags (case-insensitive)
    @Query("SELECT DISTINCT c FROM Course c WHERE " +
           "LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "EXISTS (SELECT t FROM c.tags t WHERE LOWER(t) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Course> searchByTitleOrTags(@Param("keyword") String keyword);

    // ✅ Get all courses with tags
    @Query("SELECT DISTINCT c FROM Course c WHERE c.tags IS NOT EMPTY")
    List<Course> findAllWithTags();

    @Query("SELECT DISTINCT c FROM Course c " +
           "LEFT JOIN FETCH c.modules m " +
           "LEFT JOIN FETCH m.videos v " +
           "WHERE c.id IN (SELECT cp.course.id FROM UserCoursePurchase cp WHERE cp.user.id = :userId) " +
           "ORDER BY c.id, m.id, v.displayOrder")
    List<Course> findBySubscribedUsers_IdWithModules(@Param("userId") Long userId);

    @Query("SELECT DISTINCT c FROM Course c " +
           "LEFT JOIN FETCH c.modules m " +
           "LEFT JOIN FETCH m.videos v " +
           "ORDER BY c.id, m.id, v.displayOrder")
    List<Course> findAllWithModulesAndVideos();

    @Query("SELECT DISTINCT c FROM Course c " +
           "LEFT JOIN FETCH c.modules m " +
           "LEFT JOIN FETCH m.videos v " +
           "WHERE c.id = :courseId " +
           "ORDER BY m.id, v.displayOrder")
    Optional<Course> findByIdWithModulesAndVideos(@Param("courseId") Long courseId);
}