package NTSA.Mukti_app.model;

import jakarta.persistence.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

@Entity
public class FoodPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String foodName;
    private String description;
    private int quantity;
    private String location;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime cookTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime expireTime;

    private boolean received = false;

    private String donorName;
    private String donorPhone;
    private String receiverPhone;

    // Category / Campaign: REGULAR, RAMADAN_IFTAR, RAMADAN_SEHRI, QURBANI_MEAT, BLANKET, DRESS
    private String category = "REGULAR";
    private Integer bonusPoints = 0;
    private String area;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDateTime getCookTime() { return cookTime; }
    public void setCookTime(LocalDateTime cookTime) { this.cookTime = cookTime; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public boolean isReceived() { return received; }
    public void setReceived(boolean received) { this.received = received; }
    public String getDonorName() { return donorName; }
    public void setDonorName(String donorName) { this.donorName = donorName; }
    public String getDonorPhone() { return donorPhone; }
    public void setDonorPhone(String donorPhone) { this.donorPhone = donorPhone; }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }
    public String getCategory() { return category != null ? category : "REGULAR"; }
    public void setCategory(String category) { this.category = category; }
    public Integer getBonusPoints() { return bonusPoints != null ? bonusPoints : 0; }
    public void setBonusPoints(Integer bonusPoints) { this.bonusPoints = bonusPoints; }
    public String getArea() { return area != null ? area : (location != null ? location : ""); }
    public void setArea(String area) { this.area = area; }
}
