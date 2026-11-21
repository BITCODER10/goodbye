package cloud.mrdesai.goodbye.controller;

import cloud.mrdesai.goodbye.model.FarewellMessage;
import cloud.mrdesai.goodbye.repository.FarewellMessageRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Controller
public class FarewellController {

    private final FarewellMessageRepository repository;

    // Constructor injection — Spring automatically passes the repository instance
    // WHY constructor injection over @Autowired on the field?
    // It makes dependencies explicit and the class easier to test
    public FarewellController(FarewellMessageRepository repository) {
        this.repository = repository;
    }

    // GET / → Show the OTP entry form
    @GetMapping("/")
    public String showOtpForm() {
        // "otp" refers to the template: src/main/resources/templates/otp.html
        return "otp";
    }

    // POST /enter → Handle OTP submission
    @PostMapping("/enter")
    public String handleOtp(@RequestParam("otp") String otp, Model model) {
        // Clean the input: remove spaces, trim
        String cleanOtp = otp.trim().replaceAll("\\s+", "");

        // Validate: must be exactly 6 digits
        if (!cleanOtp.matches("\\d{6}")) {
            model.addAttribute("error", "Please enter a valid 6-digit code.");
            return "otp";
        }

        // Look up the OTP in the database
        Optional<FarewellMessage> result = repository.findByOtp(cleanOtp);

        if (result.isEmpty()) {
            model.addAttribute("error", "Hmm, that code doesn't seem right. Double-check and try again!");
            return "otp";
        }

        // OTP found! Redirect to the farewell page
        // WHY redirect instead of rendering directly?
        // If we render directly, refreshing the page would re-submit the form
        // (the browser would ask "Resubmit form data?"). Redirect avoids this.
        return "redirect:/farewell/" + cleanOtp;
    }

    // GET /farewell/{otp} → Show the personalized farewell message
    @GetMapping("/farewell/{otp}")
    public String showFarewell(@PathVariable("otp") String otp, Model model) {
        Optional<FarewellMessage> result = repository.findByOtp(otp);

        if (result.isEmpty()) {
            return "redirect:/";
        }

        FarewellMessage msg = result.get();

        // Mark as opened (only the first time)
        if (!msg.isOpened()) {
            msg.setOpened(true);
            msg.setOpenedAt(LocalDateTime.now());
            repository.save(msg);
        }

        // Pass the message data to the template
        model.addAttribute("msg", msg);
        return "farewell";
    }
}