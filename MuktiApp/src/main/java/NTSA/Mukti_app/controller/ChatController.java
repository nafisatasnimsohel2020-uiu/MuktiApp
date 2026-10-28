package NTSA.Mukti_app.controller;

import NTSA.Mukti_app.model.BlockedUser;
import NTSA.Mukti_app.model.ChatMessage;
import NTSA.Mukti_app.model.FoodPost;
import NTSA.Mukti_app.model.FoodRequest;
import NTSA.Mukti_app.model.History;
import NTSA.Mukti_app.model.User;
import NTSA.Mukti_app.repository.BlockedUserRepository;
import NTSA.Mukti_app.repository.ChatMessageRepository;
import NTSA.Mukti_app.repository.FoodRepository;
import NTSA.Mukti_app.repository.FoodRequestRepository;
import NTSA.Mukti_app.repository.HistoryRepository;
import NTSA.Mukti_app.repository.UserRepository;
import NTSA.Mukti_app.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FoodRequestRepository foodRequestRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private BlockedUserRepository blockedUserRepository;

    @Autowired
    private HistoryRepository historyRepo;

    /**
     * Checks if a food donation or assistance request has been completed/received/cancelled.
     */
    public boolean isDonationCompleted(Long foodPostId, boolean isRequest) {
        if (foodPostId == null) {
            return false;
        }

        if (isRequest) {
            Optional<FoodRequest> reqOpt = foodRequestRepository.findById(foodPostId);
            if (reqOpt.isEmpty()) {
                return true;
            }
            String status = reqOpt.get().getStatus();
            return "Received".equalsIgnoreCase(status)
                    || "Completed".equalsIgnoreCase(status)
                    || "Cancelled".equalsIgnoreCase(status);
        } else {
            Optional<FoodPost> postOpt = foodRepository.findById(foodPostId);
            if (postOpt.isEmpty()) {
                return true;
            }
            // Check History records for this food donation post
            List<History> histories = historyRepo.findByFoodPostId(foodPostId);
            if (!histories.isEmpty()) {
                return histories.stream().anyMatch(h ->
                        (h.getIsRequestDonation() == null || !h.getIsRequestDonation())
                                && ("Received".equalsIgnoreCase(h.getStatus())
                                || "Completed".equalsIgnoreCase(h.getStatus())
                                || "Cancelled".equalsIgnoreCase(h.getStatus()))
                );
            }
            return false;
        }
    }

    // View chat list page
    @GetMapping(value = { "", "/list" })
    public String chatListPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        String userPhone = user.getPhone().trim();

        // Get all messages for this user, newest first
        List<ChatMessage> allMessages = chatMessageRepository
                .findBySenderPhoneOrReceiverPhoneOrderByTimestampDesc(userPhone, userPhone);

        List<Map<String, Object>> asDonor = new ArrayList<>();
        List<Map<String, Object>> asReceiver = new ArrayList<>();
        Set<String> processedConversations = new HashSet<>();

        for (ChatMessage msg : allMessages) {
            String otherPhone = msg.getSenderPhone().trim().equals(userPhone)
                    ? msg.getReceiverPhone().trim()
                    : msg.getSenderPhone().trim();

            // Unique key for conversation: FoodPostID + isRequest + OtherUser
            String conversationKey = msg.getFoodPostId() + "_" + msg.isRequest() + "_" + otherPhone;

            if (processedConversations.contains(conversationKey)) {
                continue;
            }
            processedConversations.add(conversationKey);

            String foodName;
            String location;
            boolean amIDonorSide; // True if I am the Donor/Fulfiller, False if I am Receiver/Requester
            boolean isPostReceived;

            if (msg.isRequest()) {
                Optional<FoodRequest> reqOpt = foodRequestRepository.findById(msg.getFoodPostId());
                if (reqOpt.isEmpty())
                    continue;
                FoodRequest req = reqOpt.get();
                foodName = req.getFoodName();
                location = req.getLocation();
                isPostReceived = "Received".equalsIgnoreCase(req.getStatus());

                // Logic: If I am the requester, I am on the Receiver side. Everyone else
                // (helping me) is Donor side.
                if (req.getRequesterPhone() != null && req.getRequesterPhone().trim().equals(userPhone)) {
                    amIDonorSide = false;
                } else {
                    amIDonorSide = true;
                }
            } else {
                Optional<FoodPost> foodPostOpt = foodRepository.findById(msg.getFoodPostId());
                if (foodPostOpt.isEmpty())
                    continue;
                FoodPost post = foodPostOpt.get();
                foodName = post.getFoodName();
                location = post.getLocation();
                isPostReceived = post.isReceived();

                // Logic: If I am the donor, I am Donor side. Everyone else (asking me) is
                // Receiver side.
                if (post.getDonorPhone().trim().equals(userPhone)) {
                    amIDonorSide = true;
                } else {
                    amIDonorSide = false;
                }
            }

            String otherName = userRepository.findByPhone(otherPhone)
                    .map(User::getName)
                    .orElse(msg.getSenderPhone().trim().equals(userPhone) ? "User" : msg.getSenderName());

            long unreadCount = chatMessageRepository
                    .findConversation(msg.getFoodPostId(), userPhone, otherPhone, msg.isRequest())
                    .stream()
                    .filter(m -> !m.isReadStatus() && m.getReceiverPhone().trim().equals(userPhone))
                    .count();

            boolean isCompleted = isDonationCompleted(msg.getFoodPostId(), msg.isRequest());
            boolean isBlockedByThem = blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(otherPhone, userPhone);
            boolean isBlockedByMe = blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(userPhone, otherPhone);

            Map<String, Object> conv = new HashMap<>();
            conv.put("foodPostId", msg.getFoodPostId());
            conv.put("isRequest", msg.isRequest());
            conv.put("foodName", foodName);
            conv.put("otherUserName", otherName);
            conv.put("otherUserPhone", otherPhone);
            conv.put("lastMessage", msg.getMessage());
            conv.put("lastMessageTime", msg.getTimestamp());
            conv.put("unreadCount", unreadCount);
            conv.put("isReceived", isPostReceived);
            conv.put("isCompleted", isCompleted);
            conv.put("isBlockedByThem", isBlockedByThem);
            conv.put("isBlockedByMe", isBlockedByMe);
            conv.put("location", location);

            if (amIDonorSide) {
                asDonor.add(conv);
            } else {
                asReceiver.add(conv);
            }
        }

        model.addAttribute("donations", asDonor);
        model.addAttribute("receivals", asReceiver);
        model.addAttribute("user", user);
        return "chat-list";
    }

    // View specific chat room
    @GetMapping("/room/{foodPostId}")
    public String chatRoom(@PathVariable Long foodPostId,
            @RequestParam String otherPhone,
            @RequestParam(defaultValue = "false") boolean isRequest,
            HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return "redirect:/login";

        String foodName;
        boolean isDonor;
        boolean isReceiver;
        String userPhone = user.getPhone().trim();
        String targetPhone = otherPhone.trim();

        if (isRequest) {
            Optional<FoodRequest> reqOpt = foodRequestRepository.findById(foodPostId);
            if (reqOpt.isEmpty())
                return "redirect:/chat";
            FoodRequest req = reqOpt.get();
            foodName = req.getFoodName();
            isDonor = req.getDonorPhone() != null && req.getDonorPhone().trim().equals(userPhone);
            isReceiver = req.getRequesterPhone() != null && req.getRequesterPhone().trim().equals(userPhone);

            boolean isValidCounterpart = (isDonor && req.getRequesterPhone().trim().equals(targetPhone))
                    || (isReceiver && req.getDonorPhone() != null && req.getDonorPhone().trim().equals(targetPhone));
            if (!isValidCounterpart)
                return "redirect:/chat";

            Map<String, Object> postMap = new HashMap<>();
            postMap.put("id", req.getId());
            postMap.put("foodName", req.getFoodName());
            model.addAttribute("foodPost", postMap);
        } else {
            Optional<FoodPost> foodPostOpt = foodRepository.findById(foodPostId);
            if (foodPostOpt.isEmpty())
                return "redirect:/chat";
            FoodPost post = foodPostOpt.get();
            foodName = post.getFoodName();
            isDonor = post.getDonorPhone().trim().equals(userPhone);
            isReceiver = post.getReceiverPhone() != null && post.getReceiverPhone().trim().equals(userPhone);

            boolean isValidCounterpart = (isDonor && post.getReceiverPhone() != null
                    && post.getReceiverPhone().trim().equals(targetPhone))
                    || (isReceiver && post.getDonorPhone() != null && post.getDonorPhone().trim().equals(targetPhone));
            if (!isValidCounterpart)
                return "redirect:/chat";

            model.addAttribute("foodPost", post);
        }

        // Get and mark as read
        List<ChatMessage> messages = chatMessageRepository.findConversation(foodPostId, userPhone, targetPhone,
                isRequest);
        chatMessageRepository.markAsRead(foodPostId, userPhone, isRequest);

        String otherName = userRepository.findByPhone(targetPhone)
                .map(User::getName)
                .orElse("Unknown User");

        boolean isCompleted = isDonationCompleted(foodPostId, isRequest);
        boolean isBlockedByThem = blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(targetPhone, userPhone);
        boolean isBlockedByMe = blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(userPhone, targetPhone);
        boolean canMessage = !isCompleted && !isBlockedByThem && !isBlockedByMe;

        model.addAttribute("messages", messages);
        model.addAttribute("currentUser", user);
        model.addAttribute("otherUserName", otherName);
        model.addAttribute("otherUserPhone", targetPhone);
        model.addAttribute("myRole", isDonor ? "DONOR" : "RECEIVER");
        model.addAttribute("isRequest", isRequest);
        model.addAttribute("isDonationCompleted", isCompleted);
        model.addAttribute("isBlockedByThem", isBlockedByThem);
        model.addAttribute("isBlockedByMe", isBlockedByMe);
        model.addAttribute("canMessage", canMessage);

        return "chat-room";
    }

    // Send message via REST API
    @PostMapping("/send")
    @ResponseBody
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        try {
            Long foodPostId = Long.parseLong(payload.get("foodPostId").toString());
            String targetPhone = payload.get("receiverPhone").toString().trim();
            String message = payload.get("message").toString();
            boolean isRequest = payload.containsKey("isRequest") && (boolean) payload.get("isRequest");
            String userPhone = user.getPhone().trim();

            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Empty message"));
            }

            // 1. Check if recipient has blocked sender
            if (blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(targetPhone, userPhone)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "You have been blocked by this user. You cannot send messages."
                ));
            }

            // 2. Check if sender has blocked recipient
            if (blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(userPhone, targetPhone)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "You have blocked this user. Unblock them first to send messages."
                ));
            }

            // 3. Check if donation is completed
            if (isDonationCompleted(foodPostId, isRequest)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                        "error", "This donation has been completed. Messaging has been automatically stopped."
                ));
            }

            if (isRequest) {
                Optional<FoodRequest> reqOpt = foodRequestRepository.findById(foodPostId);
                if (reqOpt.isEmpty())
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid request"));
                FoodRequest req = reqOpt.get();
                boolean isDonor = req.getDonorPhone() != null && req.getDonorPhone().trim().equals(userPhone);
                boolean isReceiver = req.getRequesterPhone() != null
                        && req.getRequesterPhone().trim().equals(userPhone);
                boolean authorized = (isDonor && req.getRequesterPhone().trim().equals(targetPhone))
                        || (isReceiver && req.getDonorPhone() != null && req.getDonorPhone().trim().equals(targetPhone));
                if (!authorized)
                    return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            } else {
                Optional<FoodPost> postOpt = foodRepository.findById(foodPostId);
                if (postOpt.isEmpty())
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid post"));

                FoodPost post = postOpt.get();
                boolean isDonor = post.getDonorPhone().trim().equals(userPhone);
                boolean isReceiver = post.getReceiverPhone() != null
                        && post.getReceiverPhone().trim().equals(userPhone);

                boolean authorizedReceiver = (isDonor && post.getReceiverPhone() != null
                        && post.getReceiverPhone().trim().equals(targetPhone))
                        || (isReceiver && post.getDonorPhone() != null && post.getDonorPhone().trim().equals(targetPhone));

                if (!authorizedReceiver) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "You are not authorized to message this user about this post"));
                }
            }

            ChatMessage chatMsg = new ChatMessage(foodPostId, userPhone, user.getName(), targetPhone, message.trim(),
                    isRequest);
            chatMessageRepository.save(chatMsg);

            // Notify Receiver
            notificationService.notifyUser(targetPhone, "New message from " + user.getName(), "message");

            return ResponseEntity.ok(Map.of("success", true, "message", chatMsg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get messages
    @GetMapping("/messages/{foodPostId}")
    @ResponseBody
    public ResponseEntity<?> getMessages(@PathVariable Long foodPostId,
            @RequestParam String otherPhone,
            @RequestParam(defaultValue = "false") boolean isRequest,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        List<ChatMessage> messages = chatMessageRepository.findConversation(foodPostId, user.getPhone().trim(),
                otherPhone.trim(), isRequest);
        return ResponseEntity.ok(messages);
    }

    // Live status check (Completed donation, Block status)
    @GetMapping("/status/{foodPostId}")
    @ResponseBody
    public ResponseEntity<?> getChatStatus(
            @PathVariable Long foodPostId,
            @RequestParam String otherPhone,
            @RequestParam(defaultValue = "false") boolean isRequest,
            HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String userPhone = user.getPhone().trim();
        String targetPhone = otherPhone.trim();

        boolean isCompleted = isDonationCompleted(foodPostId, isRequest);
        boolean isBlockedByThem = blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(targetPhone, userPhone);
        boolean isBlockedByMe = blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(userPhone, targetPhone);
        boolean canMessage = !isCompleted && !isBlockedByThem && !isBlockedByMe;

        Map<String, Object> status = new HashMap<>();
        status.put("isCompleted", isCompleted);
        status.put("isBlockedByThem", isBlockedByThem);
        status.put("isBlockedByMe", isBlockedByMe);
        status.put("canMessage", canMessage);

        return ResponseEntity.ok(status);
    }

    // Block a user at any time
    @PostMapping("/block")
    @ResponseBody
    public ResponseEntity<?> blockUser(@RequestBody Map<String, String> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String targetPhone = payload.get("targetPhone");
        if (targetPhone == null || targetPhone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Target phone is required"));
        }
        targetPhone = targetPhone.trim();
        String userPhone = user.getPhone().trim();

        if (userPhone.equals(targetPhone)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot block yourself"));
        }

        if (!blockedUserRepository.existsByBlockerPhoneAndBlockedPhone(userPhone, targetPhone)) {
            String targetName = userRepository.findByPhone(targetPhone).map(User::getName).orElse("User");
            blockedUserRepository.save(new BlockedUser(userPhone, targetPhone, targetName));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "blocked", true,
                "message", "User has been blocked successfully"
        ));
    }

    // Unblock a user
    @PostMapping("/unblock")
    @ResponseBody
    public ResponseEntity<?> unblockUser(@RequestBody Map<String, String> payload, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String targetPhone = payload.get("targetPhone");
        if (targetPhone == null || targetPhone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Target phone is required"));
        }
        targetPhone = targetPhone.trim();
        String userPhone = user.getPhone().trim();

        blockedUserRepository.deleteByBlockerPhoneAndBlockedPhone(userPhone, targetPhone);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "blocked", false,
                "message", "User has been unblocked successfully"
        ));
    }

    // Get list of blocked users
    @GetMapping("/blocked-users")
    @ResponseBody
    public ResponseEntity<?> getBlockedUsers(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        List<BlockedUser> list = blockedUserRepository.findByBlockerPhoneOrderByBlockTimeDesc(user.getPhone().trim());
        List<Map<String, Object>> result = new ArrayList<>();
        for (BlockedUser b : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", b.getId());
            map.put("blockedPhone", b.getBlockedPhone());
            map.put("blockedName", b.getBlockedName() != null ? b.getBlockedName() :
                    userRepository.findByPhone(b.getBlockedPhone()).map(User::getName).orElse("User"));
            map.put("blockTime", b.getBlockTime() != null ? b.getBlockTime().toString() : "");
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    // Unread count
    @GetMapping("/unread-count")
    @ResponseBody
    public ResponseEntity<?> getUnreadCount(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null)
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        return ResponseEntity
                .ok(Map.of("unreadCount", chatMessageRepository.countUnreadMessages(user.getPhone().trim())));
    }
}
