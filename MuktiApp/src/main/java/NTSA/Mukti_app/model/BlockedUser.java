package NTSA.Mukti_app.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blocked_users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"blockerPhone", "blockedPhone"})
})
public class BlockedUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String blockerPhone;

    @Column(nullable = false)
    private String blockedPhone;

    private String blockedName;

    private LocalDateTime blockTime = LocalDateTime.now();

    public BlockedUser() {
    }

    public BlockedUser(String blockerPhone, String blockedPhone, String blockedName) {
        this.blockerPhone = blockerPhone;
        this.blockedPhone = blockedPhone;
        this.blockedName = blockedName;
        this.blockTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBlockerPhone() {
        return blockerPhone;
    }

    public void setBlockerPhone(String blockerPhone) {
        this.blockerPhone = blockerPhone;
    }

    public String getBlockedPhone() {
        return blockedPhone;
    }

    public void setBlockedPhone(String blockedPhone) {
        this.blockedPhone = blockedPhone;
    }

    public String getBlockedName() {
        return blockedName;
    }

    public void setBlockedName(String blockedName) {
        this.blockedName = blockedName;
    }

    public LocalDateTime getBlockTime() {
        return blockTime;
    }

    public void setBlockTime(LocalDateTime blockTime) {
        this.blockTime = blockTime;
    }
}
