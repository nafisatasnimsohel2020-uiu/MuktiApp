package NTSA.Mukti_app.repository;

import NTSA.Mukti_app.model.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {

    boolean existsByBlockerPhoneAndBlockedPhone(String blockerPhone, String blockedPhone);

    Optional<BlockedUser> findByBlockerPhoneAndBlockedPhone(String blockerPhone, String blockedPhone);

    List<BlockedUser> findByBlockerPhoneOrderByBlockTimeDesc(String blockerPhone);

    List<BlockedUser> findByBlockedPhone(String blockedPhone);

    @Modifying
    @Transactional
    void deleteByBlockerPhoneAndBlockedPhone(String blockerPhone, String blockedPhone);
}
