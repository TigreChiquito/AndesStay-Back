package cl.tigrechiquito.ms_andesstay_notify.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cl.tigrechiquito.ms_andesstay_notify.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByEventId(String eventId);

    /** Avisos visibles para el usuario: los suyos de huésped y, si es personal, los STAFF. */
    @Query("""
            select n from Notification n
            where (n.audience = cl.tigrechiquito.ms_andesstay_notify.domain.Audience.GUEST
                   and n.recipientId = :userId)
               or (:staff = true
                   and n.audience = cl.tigrechiquito.ms_andesstay_notify.domain.Audience.STAFF)
            order by n.createdAt desc
            """)
    List<Notification> findVisibleTo(@Param("userId") String userId,
                                     @Param("staff") boolean staff,
                                     Pageable pageable);

    @Modifying
    @Query("""
            update Notification n set n.read = true
            where n.read = false
              and ((n.audience = cl.tigrechiquito.ms_andesstay_notify.domain.Audience.GUEST
                    and n.recipientId = :userId)
                or (:staff = true
                    and n.audience = cl.tigrechiquito.ms_andesstay_notify.domain.Audience.STAFF))
            """)
    int markAllReadFor(@Param("userId") String userId, @Param("staff") boolean staff);
}
