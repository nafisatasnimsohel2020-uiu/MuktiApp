package NTSA.Mukti_app.controller;

import NTSA.Mukti_app.service.ForgotPasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class ForgotPasswordController {

    @Autowired
    private ForgotPasswordService forgotPasswordService;

    @GetMapping("/forgot")
    public String forgotPage() {
        return "forgot";
    }

    @PostMapping("/forgot/send-otp")
    @ResponseBody
    public Map<String, String> sendOtp(@RequestParam String email) {
        String result = forgotPasswordService.sendOtp(email);
        Map<String, String> response = new HashMap<>();
        response.put("message", result);
        boolean success = result.contains("successfully");
        response.put("status", success ? "success" : "error");

        if (success) {
            String otp = forgotPasswordService.getLatestOtp(email);
            // ✅ Returned in JSON so client-side script can output to browser console
            response.put("otp", otp);
        }
        return response;
    }

    @PostMapping("/forgot/verify-otp")
    @ResponseBody
    public Map<String, String> verifyOtp(@RequestParam String email, @RequestParam String otp) {
        String result = forgotPasswordService.resetPassword(email, otp, null);
        Map<String, String> response = new HashMap<>();
        if (result.equals("OTP Valid") || result.equals("Password Reset Successful")) {
            response.put("status", "success");
            response.put("message", "OTP Verified Successfully");
        } else {
            response.put("status", "error");
            response.put("message", result);
        }
        return response;
    }

    @PostMapping("/forgot/reset-password")
    @ResponseBody
    public Map<String, String> resetPassword(@RequestParam String email, @RequestParam String otp, @RequestParam String newPassword) {
        String result = forgotPasswordService.resetPassword(email, otp, newPassword);
        Map<String, String> response = new HashMap<>();
        if (result.equals("Password Reset Successful")) {
            response.put("status", "success");
            response.put("message", "Password has been successfully updated! You can now log in.");
        } else {
            response.put("status", "error");
            response.put("message", result);
        }
        return response;
    }
}
