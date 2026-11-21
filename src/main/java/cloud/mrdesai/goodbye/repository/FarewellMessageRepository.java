package cloud.mrdesai.goodbye.repository;

import cloud.mrdesai.goodbye.model.FarewellMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FarewellMessageRepository extends JpaRepository<FarewellMessage, Long> {

    // Spring Data JPA reads this method name and generates:
    // SELECT * FROM farewell_messages WHERE otp = ?
    //
    // Optional<> means: the result might be empty (if the OTP doesn't exist)
    // This forces us to handle the "not found" case explicitly — no NullPointerExceptions
    Optional<FarewellMessage> findByOtp(String otp);
}