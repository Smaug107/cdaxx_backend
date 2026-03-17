package com.example.cdaxVideo.Repository;
import com.example.cdaxVideo.Entity.User;
import com.example.cdaxVideo.Entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    
    List<UserSession> findByUserAndIsActiveTrue(User user);
    
    Optional<UserSession> findByUserAndDeviceTypeAndIsActiveTrue(User user, String deviceType);
    
    Optional<UserSession> findByJwtToken(String token);
    
    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.user = :user AND s.deviceType != :deviceType AND s.isActive = true")
    void deactivateOtherPlatformSessions(@Param("user") User user, @Param("deviceType") String deviceType);
    
    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.user = :user AND s.deviceType = :deviceType AND s.deviceId = :deviceId")
    void deactivateCurrentDeviceSessions(@Param("user") User user, @Param("deviceType") String deviceType, @Param("deviceId") String deviceId);
    
    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.lastActivity = CURRENT_TIMESTAMP WHERE s.jwtToken = :token")
    void updateLastActivity(@Param("token") String token);
    
    @Modifying
    @Transactional
    @Query("UPDATE UserSession s SET s.isActive = false WHERE s.jwtToken = :token")
    void deactivateSession(@Param("token") String token);
}