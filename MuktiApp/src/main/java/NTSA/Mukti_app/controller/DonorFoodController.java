package NTSA.Mukti_app.controller;

import NTSA.Mukti_app.model.FoodPost;
import NTSA.Mukti_app.model.History;
import NTSA.Mukti_app.model.User;
import NTSA.Mukti_app.repository.HistoryRepository;
import NTSA.Mukti_app.service.FoodService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/food")
public class DonorFoodController {

    @Autowired
    private FoodService service;

    @Autowired
    private HistoryRepository historyRepo;

    @Autowired
    private NTSA.Mukti_app.repository.UserRepository userRepo;

    @Autowired
    private NTSA.Mukti_app.repository.FoodRepository foodRepo;

    @Autowired
    private NTSA.Mukti_app.service.NotificationService notificationService;

    @Autowired
    private NTSA.Mukti_app.repository.FoodRequestRepository foodRequestRepo;

    @GetMapping("/donate")
    public String donatePage(
            @RequestParam(required = false) Long requestId,
            @RequestParam(required = false) String campaign,
            HttpSession session, Model model) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null)
            return "redirect:/login";

        // Fetch latest version of user to get current address & points
        User user = userRepo.findByPhone(sessionUser.getPhone()).orElse(sessionUser);
        model.addAttribute("user", user);

        // If donating in response to a request, pass it to the view
        if (requestId != null) {
            model.addAttribute("requestId", requestId);
        }

        // Pre-select campaign if passed
        model.addAttribute("campaign", campaign != null ? campaign.toUpperCase() : "REGULAR");

        return "donate";
    }

    @PostMapping("/donate")
    public String submitDonation(@ModelAttribute FoodPost foodPost,
            @RequestParam(required = false) Long requestId,
            HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser != null) {
            User user = userRepo.findByPhone(sessionUser.getPhone()).orElse(sessionUser);
            foodPost.setDonorName(user.getName());
            foodPost.setDonorPhone(user.getPhone());

            // Calculate points & bonuses:
            // Base food donation: 10 pts
            // Ramadan (Ifter / Seheri): +5 bonus -> 15 pts
            // Qurban (Meat): +5 bonus -> 15 pts
            // Blanket / Dresses (separate section): 30 pts
            String cat = foodPost.getCategory() != null ? foodPost.getCategory().toUpperCase() : "REGULAR";
            int earnedPoints = 10;
            int bonusPoints = 0;
            String campaignName = "Food Donation";

            if (cat.contains("RAMADAN") || cat.contains("IFTER") || cat.contains("SEHERI")) {
                bonusPoints = 5;
                earnedPoints = 15;
                campaignName = "🌙 Ramadan Iftar & Sehri Drive (+5 bonus)";
            } else if (cat.contains("QURBAN") || cat.contains("MEAT")) {
                bonusPoints = 5;
                earnedPoints = 15;
                campaignName = "🥩 Qurbani Meat Drive (+5 bonus)";
            } else if (cat.contains("BLANKET") || cat.contains("DRESS") || cat.contains("CLOTHES") || cat.contains("OTHER") || cat.contains("ACCESSOR")) {
                bonusPoints = 30;
                earnedPoints = 30;
                campaignName = "🧥 Warmth & Dignity: Blanket, Dress & Others Drive (30 points)";
                // Non-food items (blankets, dresses, clothes, and other supplies) have no cooking or expiration time
                foodPost.setCookTime(null);
                foodPost.setExpireTime(null);
            }

            foodPost.setBonusPoints(bonusPoints);
            service.donate(foodPost);

            // Points will only be awarded AFTER donation is completed/confirmed from history
            History history = new History(user.getPhone(), foodPost.getFoodName(), foodPost.getLocation(), "DONOR",
                    "Available", null, null, foodPost.getId(), cat, earnedPoints);
            historyRepo.save(history);

            // If this donation is in response to a request, link them and update status
            if (requestId != null) {
                foodRequestRepo.findById(requestId).ifPresent(request -> {
                    request.setStatus("Processing");
                    request.setDonorPhone(user.getPhone());
                    request.setDonorName(user.getName());
                    request.setFoodPostId(foodPost.getId());
                    foodRequestRepo.save(request);
                });
            }

            // Public broadcast donation notification to all users across all pages
            String msg = user.getName() + " donated: " + foodPost.getFoodName() + " (" + foodPost.getLocation()
                    + ") [" + campaignName + "]";
            notificationService.notifyAll(msg, "donation");
        }
        return "redirect:/dashboard";
    }

    private boolean isOtherCategory(String cat, String name) {
        String c = cat != null ? cat.toUpperCase() : "";
        String n = name != null ? name.toUpperCase() : "";
        return c.contains("BLANKET") || c.contains("DRESS") || c.contains("CLOTHES") || c.contains("OTHER") || c.contains("ACCESSOR")
                || n.contains("BLANKET") || n.contains("DRESS") || n.contains("CLOTHES") || n.contains("SHIRT") || n.contains("PANT")
                || n.contains("SHOE") || n.contains("BAG") || n.contains("SWEATER") || n.contains("JACKET") || n.contains("SAREE");
    }

    @GetMapping("/history")
    public String viewHistory(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        List<History> all = historyRepo.findByUserPhoneOrderByActivityTimeDesc(user.getPhone());
        List<History> donations = new ArrayList<>();
        List<History> receives = new ArrayList<>();

        if (all != null) {
            for (History h : all) {
                // Retroactive fix for missing foodPostId
                if (h.getFoodPostId() == null) {
                    List<FoodPost> matchingPosts = foodRepo.findAll().stream()
                            .filter(fp -> fp.getFoodName().equalsIgnoreCase(h.getFoodName()))
                            .filter(fp -> {
                                if ("DONOR".equals(h.getRole())) {
                                    return fp.getDonorPhone().trim().equals(h.getUserPhone().trim());
                                } else {
                                    return fp.getReceiverPhone() != null
                                            && fp.getReceiverPhone().trim().equals(h.getUserPhone().trim());
                                }
                            })
                            .toList();
                    if (!matchingPosts.isEmpty()) {
                        h.setFoodPostId(matchingPosts.get(0).getId());
                        historyRepo.save(h);
                    }
                }

                if ("DONOR".equals(h.getRole()))
                    donations.add(h);
                else
                    receives.add(h);
            }
        }

        // Separate Food vs Other Accessories history
        List<History> foodDonations = new ArrayList<>();
        List<History> otherDonations = new ArrayList<>();
        for (History h : donations) {
            if (isOtherCategory(h.getCategory(), h.getFoodName())) {
                otherDonations.add(h);
            } else {
                foodDonations.add(h);
            }
        }

        List<History> foodReceives = new ArrayList<>();
        List<History> otherReceives = new ArrayList<>();
        for (History h : receives) {
            if (isOtherCategory(h.getCategory(), h.getFoodName())) {
                otherReceives.add(h);
            } else {
                foodReceives.add(h);
            }
        }

        // Also include requests made by this user, separated by category
        List<NTSA.Mukti_app.model.FoodRequest> userRequests = foodRequestRepo.findAll().stream()
                .filter(req -> user.getPhone().equals(req.getRequesterPhone()))
                .sorted((a, b) -> b.getRequestTime().compareTo(a.getRequestTime()))
                .toList();

        List<NTSA.Mukti_app.model.FoodRequest> foodRequestsOnly = new ArrayList<>();
        List<NTSA.Mukti_app.model.FoodRequest> otherRequestsOnly = new ArrayList<>();
        for (NTSA.Mukti_app.model.FoodRequest req : userRequests) {
            if (isOtherCategory(req.getCategory(), req.getFoodName())) {
                otherRequestsOnly.add(req);
            } else {
                foodRequestsOnly.add(req);
            }
        }

        model.addAttribute("allActivities", all != null ? all : new ArrayList<>());
        model.addAttribute("donations", donations);
        model.addAttribute("receives", receives);
        model.addAttribute("foodDonations", foodDonations);
        model.addAttribute("otherDonations", otherDonations);
        model.addAttribute("foodReceives", foodReceives);
        model.addAttribute("otherReceives", otherReceives);
        model.addAttribute("foodRequests", userRequests);
        model.addAttribute("foodRequestsOnly", foodRequestsOnly);
        model.addAttribute("otherRequestsOnly", otherRequestsOnly);
        model.addAttribute("currentUser", user);

        return "history";
    }

    @PostMapping("/complete/{id}")
    @ResponseBody
    public String complete(@PathVariable Long id, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            return "Error: Unauthorized. Please log in first.";
        }

        History h = historyRepo.findById(id).orElse(null);
        if (h == null) {
            return "Error: Record not found.";
        }

        // Only the DONOR can confirm and complete donation
        if (!"DONOR".equalsIgnoreCase(h.getRole()) || !sessionUser.getPhone().equals(h.getUserPhone())) {
            return "Error: Only the donor can confirm and complete this donation.";
        }

        h.setStatus("Received");
        historyRepo.save(h);

        boolean isRequest = Boolean.TRUE.equals(h.getIsRequestDonation());
        int pointsToAward = (h.getPointsEarned() != null && h.getPointsEarned() > 0) ? h.getPointsEarned() : 10;

        if (isRequest) {
            if (h.getFoodPostId() != null) {
                foodRequestRepo.findById(h.getFoodPostId()).ifPresent(req -> {
                    req.setStatus("Received");
                    foodRequestRepo.save(req);
                });

                // Update receiver's history record
                historyRepo.findAll().stream()
                        .filter(item -> h.getFoodPostId().equals(item.getFoodPostId())
                                && Boolean.TRUE.equals(item.getIsRequestDonation())
                                && "RECEIVER".equalsIgnoreCase(item.getRole()))
                        .forEach(item -> {
                            item.setStatus("Received");
                            historyRepo.save(item);
                        });
            }

            // Award points after completing request donation
            userRepo.findByPhone(sessionUser.getPhone()).ifPresent(user -> {
                user.setPoints(user.getPoints() + pointsToAward);
                userRepo.save(user);
                session.setAttribute("user", user);
                notificationService.notifyUser(user.getPhone(),
                        "🎉 You earned " + pointsToAward + " karma points for fulfilling this request!", "points");
            });

            if (h.getOtherPartyPhone() != null) {
                notificationService.notifyUser(
                        h.getOtherPartyPhone(),
                        "Your request for '" + h.getFoodName() + "' was successfully fulfilled by " + sessionUser.getName() + "!",
                        "update");
            }
            notificationService.notifyUser(sessionUser.getPhone(),
                    "Donation for request '" + h.getFoodName() + "' completed! Thank you for helping.", "update");

        } else {
            if (h.getFoodPostId() != null) {
                foodRepo.findById(h.getFoodPostId()).ifPresent(post -> {
                    post.setReceived(true);
                    foodRepo.save(post);
                });

                // Update receiver's history record
                historyRepo.findAll().stream()
                        .filter(item -> h.getFoodPostId().equals(item.getFoodPostId())
                                && !Boolean.TRUE.equals(item.getIsRequestDonation())
                                && "RECEIVER".equalsIgnoreCase(item.getRole()))
                        .forEach(item -> {
                            item.setStatus("Received");
                            historyRepo.save(item);
                        });
            } else {
                syncStatus(h.getFoodName(), "Received");
            }

            // Award points after completing donation
            userRepo.findByPhone(sessionUser.getPhone()).ifPresent(user -> {
                user.setPoints(user.getPoints() + pointsToAward);
                userRepo.save(user);
                session.setAttribute("user", user);
                notificationService.notifyUser(user.getPhone(),
                        "🎉 You earned " + pointsToAward + " karma points for completing the donation of '" + h.getFoodName() + "'!", "points");
            });

            if (h.getOtherPartyPhone() != null) {
                notificationService.notifyUser(
                        h.getOtherPartyPhone(),
                        "Donation for '" + h.getFoodName() + "' has been confirmed by donor " + sessionUser.getName() + "!",
                        "update");
            }
            notificationService.notifyUser(sessionUser.getPhone(),
                    "Donation for '" + h.getFoodName() + "' marked as completed! Thank you for sharing.", "update");
        }

        return "Success";
    }

    @PostMapping("/complete-request/{id}")
    @ResponseBody
    public String completeRequest(@PathVariable Long id, HttpSession session) {
        return complete(id, session);
    }

    @GetMapping("/leaderboard")
    public String viewLeaderboard(Model model) {
        model.addAttribute("topDonors", userRepo.findAllByOrderByPointsDesc());
        return "leaderboard";
    }

    @PostMapping("/cancel/{id}")
    @ResponseBody
    public String cancel(@PathVariable Long id, HttpSession session) {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            return "Error: Unauthorized. Please log in.";
        }

        History h = historyRepo.findById(id).orElse(null);
        if (h == null) {
            return "Error: Record not found.";
        }

        if (!sessionUser.getPhone().equals(h.getUserPhone())) {
            return "Error: You can only cancel your own activity.";
        }

        boolean isRequest = Boolean.TRUE.equals(h.getIsRequestDonation());

        if (isRequest) {
            h.setStatus("Cancelled");
            historyRepo.save(h);

            if (h.getFoodPostId() != null) {
                foodRequestRepo.findById(h.getFoodPostId()).ifPresent(req -> {
                    req.setStatus("Pending");
                    req.setDonorPhone(null);
                    req.setDonorName(null);
                    foodRequestRepo.save(req);
                });

                historyRepo.findAll().stream()
                        .filter(item -> h.getFoodPostId().equals(item.getFoodPostId())
                                && Boolean.TRUE.equals(item.getIsRequestDonation()))
                        .forEach(item -> {
                            item.setStatus("Cancelled");
                            historyRepo.save(item);
                        });
            }

            if (h.getOtherPartyPhone() != null) {
                notificationService.notifyUser(
                        h.getOtherPartyPhone(),
                        "The donation interaction for request '" + h.getFoodName() + "' was cancelled.",
                        "cancel");
            }
            notificationService.notifyUser(sessionUser.getPhone(),
                    "Activity '" + h.getFoodName() + "' was cancelled.", "cancel");

        } else {
            boolean isDonor = "DONOR".equalsIgnoreCase(h.getRole());

            if (isDonor) {
                h.setStatus("Cancelled");
                historyRepo.save(h);

                if (h.getFoodPostId() != null) {
                    foodRepo.findById(h.getFoodPostId()).ifPresent(post -> {
                        post.setReceived(false);
                        post.setReceiverPhone(null);
                        foodRepo.save(post);
                    });

                    historyRepo.findAll().stream()
                            .filter(item -> h.getFoodPostId().equals(item.getFoodPostId())
                                    && "RECEIVER".equalsIgnoreCase(item.getRole()))
                            .forEach(item -> {
                                item.setStatus("Cancelled");
                                historyRepo.save(item);
                            });
                }

                if (h.getOtherPartyPhone() != null) {
                    notificationService.notifyUser(
                            h.getOtherPartyPhone(),
                            "The donation for '" + h.getFoodName() + "' was cancelled by the donor.",
                            "cancel");
                }
                notificationService.notifyUser(sessionUser.getPhone(),
                        "Activity '" + h.getFoodName() + "' was cancelled.", "cancel");

            } else {
                h.setStatus("Cancelled");
                historyRepo.save(h);

                if (h.getFoodPostId() != null) {
                    foodRepo.findById(h.getFoodPostId()).ifPresent(post -> {
                        post.setReceived(false);
                        post.setReceiverPhone(null);
                        foodRepo.save(post);
                    });

                    historyRepo.findAll().stream()
                            .filter(item -> h.getFoodPostId().equals(item.getFoodPostId())
                                    && "DONOR".equalsIgnoreCase(item.getRole()))
                            .forEach(item -> {
                                item.setStatus("Available");
                                item.setOtherPartyName(null);
                                item.setOtherPartyPhone(null);
                                historyRepo.save(item);
                            });
                }

                if (h.getOtherPartyPhone() != null) {
                    notificationService.notifyUser(
                            h.getOtherPartyPhone(),
                            "The claim for '" + h.getFoodName() + "' was cancelled by the recipient. It is now available again.",
                            "update");
                }
                notificationService.notifyUser(sessionUser.getPhone(),
                        "Your claim for '" + h.getFoodName() + "' has been cancelled.", "cancel");
            }
        }

        return "Success";
    }

    @PostMapping("/cancel-request/{id}")
    @ResponseBody
    public String cancelRequest(@PathVariable Long id, HttpSession session) {
        return cancel(id, session);
    }

    private void syncStatus(String foodName, String status) {
        List<History> all = historyRepo.findAll();
        for (History h : all) {
            if (h.getFoodName().equals(foodName) && "RECEIVER".equals(h.getRole())
                    && "Processing".equals(h.getStatus())) {
                h.setStatus(status);
                historyRepo.save(h);
            }
        }
    }
}
