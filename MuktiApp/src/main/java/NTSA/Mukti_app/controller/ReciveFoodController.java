package NTSA.Mukti_app.controller;

import NTSA.Mukti_app.model.ChatMessage;
import NTSA.Mukti_app.model.FoodPost;
import NTSA.Mukti_app.model.History;
import NTSA.Mukti_app.model.User;
import NTSA.Mukti_app.repository.ChatMessageRepository;
import NTSA.Mukti_app.repository.HistoryRepository;
import NTSA.Mukti_app.service.FoodService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import java.util.List;

@Controller
@RequestMapping("/food")
public class ReciveFoodController {

    @Autowired
    private FoodService service;

    @Autowired
    private HistoryRepository historyRepo;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private NTSA.Mukti_app.service.NotificationService notificationService;

    @Autowired
    private NTSA.Mukti_app.repository.BlockedUserRepository blockedUserRepository;

    @GetMapping("/receive-page")
    public String receivePage(
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            HttpSession session,
            Model model) {
        List<FoodPost> allFoods = service.allFoods();
        // Filter out expired food posts
        List<FoodPost> availableFoods = allFoods.stream()
                .filter(food -> food.getExpireTime() == null
                        || food.getExpireTime().isAfter(java.time.LocalDateTime.now()))
                .filter(food -> !food.isReceived())
                .toList();

        if (area != null && !area.trim().isEmpty() && !"ALL".equalsIgnoreCase(area)) {
            final String filterArea = area.toLowerCase().trim();
            availableFoods = availableFoods.stream()
                    .filter(food -> (food.getLocation() != null && food.getLocation().toLowerCase().contains(filterArea))
                            || (food.getArea() != null && food.getArea().toLowerCase().contains(filterArea)))
                    .toList();
        }

        if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            final String filterCat = category.toUpperCase().trim();
            availableFoods = availableFoods.stream()
                    .filter(food -> food.getCategory() != null && food.getCategory().toUpperCase().contains(filterCat))
                    .toList();
        }

        if (type != null && !type.trim().isEmpty() && !"ALL".equalsIgnoreCase(type)) {
            final String filterType = type.toUpperCase().trim();
            if ("FOOD".equalsIgnoreCase(filterType)) {
                availableFoods = availableFoods.stream()
                        .filter(food -> {
                            String c = food.getCategory() != null ? food.getCategory().toUpperCase() : "REGULAR";
                            return !c.contains("BLANKET") && !c.contains("DRESS") && !c.contains("CLOTHES") && !c.contains("OTHER") && !c.contains("ACCESSOR");
                        })
                        .toList();
            } else if ("OTHERS".equalsIgnoreCase(filterType) || "OTHER_ACCESSORIES".equalsIgnoreCase(filterType) || "ACCESSORIES".equalsIgnoreCase(filterType)) {
                availableFoods = availableFoods.stream()
                        .filter(food -> {
                            String c = food.getCategory() != null ? food.getCategory().toUpperCase() : "";
                            return c.contains("BLANKET") || c.contains("DRESS") || c.contains("CLOTHES") || c.contains("OTHER") || c.contains("ACCESSOR");
                        })
                        .toList();
            }
        }

        model.addAttribute("foods", availableFoods);
        model.addAttribute("selectedArea", area != null ? area : "ALL");
        model.addAttribute("selectedCategory", category != null ? category : "ALL");
        model.addAttribute("selectedType", type != null ? type.toUpperCase() : "ALL");
        model.addAttribute("isLoggedIn", session.getAttribute("user") != null);
        return "receive";
    }

    @PostMapping("/receive/{id}")
    @ResponseBody
    public ResponseEntity<String> receiveFood(@PathVariable Long id, HttpSession session) {
        User receiver = (User) session.getAttribute("user");
        if (receiver == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error: Unauthorized. Please login first.");
        }

        try {
            FoodPost food = service.allFoods().stream()
                    .filter(f -> f.getId().equals(id)).findFirst().orElse(null);

            if (food != null && !food.isReceived()) {
                if (blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(food.getDonorPhone(), receiver.getPhone())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Error: You cannot receive this donation because the donor has blocked you.");
                }
                if (blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(receiver.getPhone(), food.getDonorPhone())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Error: You have blocked this donor. Unblock them first.");
                }

                service.received(id, receiver.getPhone());

                historyRepo.save(new History(receiver.getPhone(), food.getFoodName(), food.getLocation(),
                        "RECEIVER", "Processing", food.getDonorName(), food.getDonorPhone(), id, food.getCategory(), 0));

                List<History> donorHistories = historyRepo.findByUserPhoneOrderByActivityTimeDesc(food.getDonorPhone());
                for (History h : donorHistories) {
                    if (h.getFoodName().equals(food.getFoodName()) && "DONOR".equals(h.getRole())) {
                        h.setStatus("Processing");
                        h.setOtherPartyName(receiver.getName());
                        h.setOtherPartyPhone(receiver.getPhone());
                        historyRepo.save(h);
                        break;
                    }
                }

                // Create initial chat message
                try {
                    ChatMessage initialMessage = new ChatMessage(
                            id,
                            receiver.getPhone(),
                            receiver.getName(),
                            food.getDonorPhone(),
                            "Hi! I would like to receive the " + food.getFoodName() + ". Can we arrange the pickup?");
                    chatMessageRepository.save(initialMessage);

                    // Notify the donor
                    notificationService.notifyUser(
                            food.getDonorPhone(),
                            "Your donation '" + food.getFoodName() + "' was successfully requested by "
                                    + receiver.getName(),
                            "message");

                } catch (Exception e) {
                    System.err.println("Failed to create initial chat message: " + e.getMessage());
                }

                return ResponseEntity.ok("Success");
            }
            return ResponseEntity.badRequest().body("Error: Already Taken");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error");
        }
    }
}
