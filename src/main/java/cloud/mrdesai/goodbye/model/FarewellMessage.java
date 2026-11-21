package cloud.mrdesai.goodbye.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "farewell_messages")
public class FarewellMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The 8-digit OTP code — unique per friend
    @Column(nullable = false, unique = true, length = 8)
    private String otp;

    // The friend's name (displayed on the farewell page)
    @Column(nullable = false)
    private String recipientName;

    // The personalized farewell message (can be long)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    // Track whether the message has been opened
    @Column(nullable = false)
    private boolean opened = false;

    // When they first opened it (null until opened)
    private LocalDateTime openedAt;

    // --- Constructors ---

    public FarewellMessage() {
        // JPA requires a no-arg constructor
    }

    public FarewellMessage(String otp, String recipientName, String message) {
        this.otp = otp;
        this.recipientName = recipientName;
        this.message = message;
    }

    // --- Getters and Setters ---
    // (IntelliJ shortcut: right-click → Generate → Getters and Setters → Select All)

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isOpened() { return opened; }
    public void setOpened(boolean opened) { this.opened = opened; }

    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }
}
