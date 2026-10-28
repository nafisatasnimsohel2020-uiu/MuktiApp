package NTSA.Mukti_app.service;

import NTSA.Mukti_app.model.OtpToken;
import NTSA.Mukti_app.model.User;
import NTSA.Mukti_app.repository.OtpRepository;
import NTSA.Mukti_app.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class ForgotPasswordService {

    private final UserRepository userRepo;
    private final OtpRepository otpRepo;

    public ForgotPasswordService(UserRepository userRepo, OtpRepository otpRepo) {
        this.userRepo = userRepo;
        this.otpRepo = otpRepo;
    }

    @Transactional
    public String sendOtp(String email) {
        // ১. চেক করা ইউজার আছে কিনা
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) {
            // Check if input was a phone number
            user = userRepo.findByPhone(email).orElse(null);
            if (user == null) {
                return "User not found";
            }
            if (user.getEmail() != null) {
                email = user.getEmail();
            }
        }

        // ২. আগের কোনো OTP থেকে থাকলে তা ডিলিট করা
        otpRepo.findByEmail(email).ifPresent(otpRepo::delete);

        // ৩. নতুন ৬ ডিজিটের OTP জেনারেট করা
        String otp = String.format("%06d", new Random().nextInt(1000000));

        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setOtp(otp);
        token.setExpiryTime(LocalDateTime.now().plusMinutes(5));

        otpRepo.save(token);

        // ✅ কনসোলে ওটিপি প্রদর্শন (Console OTP display)
        System.out.println("\n=======================================================");
        System.out.println("🔑 [MUKTI OTP CONSOLE] Password Reset OTP Requested");
        System.out.println("   Target Account: " + email);
        System.out.println("   >>> YOUR OTP IS: [ " + otp + " ] <<<");
        System.out.println("   Expires In: 5 Minutes");
        System.out.println("=======================================================\n");

        return "OTP Sent successfully";
    }

    public String getLatestOtp(String email) {
        // If email was phone, find user email first
        User user = userRepo.findByPhone(email).orElse(null);
        if (user != null && user.getEmail() != null) {
            email = user.getEmail();
        }
        return otpRepo.findByEmail(email).map(OtpToken::getOtp).orElse(null);
    }

    @Transactional
    public String resetPassword(String email, String otp, String newPassword) {
        // Find by phone fallback
        User checkUser = userRepo.findByPhone(email).orElse(null);
        if (checkUser != null && checkUser.getEmail() != null) {
            email = checkUser.getEmail();
        }

        // ১. OTP চেক করা
        OtpToken token = otpRepo.findByEmailAndOtp(email, otp).orElse(null);

        if (token == null) return "Invalid OTP";

        // ২. মেয়াদের সময় চেক করা
        if (token.getExpiryTime().isBefore(LocalDateTime.now())) {
            otpRepo.delete(token);
            return "OTP Expired";
        }

        // ৩. যদি পাসওয়ার্ড প্যারামিটার দেওয়া থাকে তবে আপডেট করা
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setPassword(newPassword);
            userRepo.save(user);
            otpRepo.delete(token); // কাজ শেষ হলে OTP ডিলিট করা
            return "Password Reset Successful";
        }

        return "OTP Valid";
    }
}
