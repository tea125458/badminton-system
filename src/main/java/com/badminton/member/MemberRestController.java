package com.badminton.member;

import com.badminton.config.JwtUtil;
import com.badminton.member.dto.MemberResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.badminton.common.SystemLogService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/members")
public class MemberRestController {

    @Autowired
    private MemberService memberService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    @Autowired
    private VerificationCodeStore verificationCodeStore;

    @Autowired
    private SystemLogService systemLogService;

    // 1. 會員登入（回傳 JWT Token）
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        return memberService.login(username, password)
            .<ResponseEntity<?>>map(user -> {
                String token = jwtUtil.generateToken(user.getMemberId(), user.getUsername(), "MEMBER");
                Map<String, Object> result = new HashMap<>();
                result.put("token", token);
                result.put("member", new MemberResponseDTO(user));

                // 記錄登入日誌
                String displayName = (user.getFullName() != null ? user.getFullName() : "未填寫") + " " + user.getUsername();
                systemLogService.log("MEMBER", user.getMemberId(), displayName, "LOGIN", "SYSTEM", user.getMemberId(), displayName, "會員登入系統");

                return ResponseEntity.ok(result);
            })
            .orElse(ResponseEntity.status(401).body("帳號或密碼錯誤"));
    }

    // 1.5 Google 第三方登入
    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody com.badminton.member.dto.GoogleLoginRequestDTO request) {
        try {
            // 注意：這裡的 Client ID 必須替換為您在 Google Cloud Console 申請的真實 ID
            String CLIENT_ID = "901309102855-shi28efflfnhvjkjo27askl6ntt3tqv9.apps.googleusercontent.com"; 

            com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier verifier = 
                new com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(
                    new com.google.api.client.http.javanet.NetHttpTransport(), 
                    new com.google.api.client.json.gson.GsonFactory())
                .setAudience(java.util.Collections.singletonList(CLIENT_ID))
                .build();

            com.google.api.client.googleapis.auth.oauth2.GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken != null) {
                com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload = idToken.getPayload();

                String googleId = payload.getSubject();
                String email = payload.getEmail();
                String name = (String) payload.get("name");
                String pictureUrl = (String) payload.get("picture");

                // 呼叫 Service 執行綁定或自動註冊
                Member user = memberService.googleLogin(googleId, email, name, pictureUrl);

                // 取得顯示名稱（用於日誌）
                String displayName = (user.getFullName() != null ? user.getFullName() : "未填寫") + " (" + user.getUsername() + ")";

                // 如果是新註冊會員 (判斷建立時間與現在差距 5 秒內)
                if (user.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(5))) {
                    // 記錄註冊日誌 (供統計使用)
                    systemLogService.log("MEMBER", user.getMemberId(), displayName, 
                        "REGISTER", "MEMBER", user.getMemberId(), displayName, "透過 Google 帳號註冊成功");
                }

                // 核發系統的 JWT Token
                String token = jwtUtil.generateToken(user.getMemberId(), user.getUsername(), "MEMBER");

                // 記錄登入日誌
                systemLogService.log("MEMBER", user.getMemberId(), displayName, "LOGIN", "SYSTEM", user.getMemberId(), displayName, "會員透過 Google 登入成功");

                Map<String, Object> result = new HashMap<>();
                result.put("token", token);
                result.put("member", new MemberResponseDTO(user));
                return ResponseEntity.ok(result);

            } else {
                return ResponseEntity.status(401).body(java.util.Map.of("message", "無效的 Google Token"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(java.util.Map.of("message", "伺服器驗證失敗：" + e.getMessage()));
        }
    }

    // 2. 註冊 新增會員
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Member member, @RequestParam(required = false) String code, HttpServletRequest request) {
        try {
            // 驗證碼檢查 (前端如果有帶 code 過來就驗證)
            if (code != null && !code.trim().isEmpty()) {
                if (member.getEmail() == null || member.getEmail().trim().isEmpty()) {
                    return ResponseEntity.badRequest().body("缺少電子信箱，無法驗證");
                }
                if (!verificationCodeStore.verify(member.getEmail().trim(), code.trim())) {
                    return ResponseEntity.badRequest().body("驗證碼錯誤或已過期，請重新取得");
                }
                // 驗證成功後移除驗證碼，避免重複使用
                verificationCodeStore.remove(member.getEmail().trim());
            }

            if (member.getUsername() == null || !member.getUsername().matches("^[A-Za-z0-9]{6,15}$")) {
                return ResponseEntity.badRequest().body("帳號必須為 6-15 碼英數字 (不可包含特殊字元)");
            }
            if (member.getPassword() == null || member.getPassword().length() < 6 || member.getPassword().length() > 12) {
                return ResponseEntity.badRequest().body("密碼必須為 6-12 個字元");
            }
            if (member.getBirthday() != null && member.getBirthday().isAfter(java.time.LocalDate.now())) {
                return ResponseEntity.badRequest().body("生日不能設定為未來的日期");
            }

            Member savedMember = memberService.register(member);
            
            // 記錄操作日誌
            Integer adminId = (Integer) request.getAttribute("jwtUserId");
            String adminRole = (String) request.getAttribute("jwtRole");
            String adminName = (String) request.getAttribute("jwtUsername");

            String fullName = savedMember.getFullName() != null && !savedMember.getFullName().trim().isEmpty() ? savedMember.getFullName() : "未填寫";
            String displayName = fullName + " " + savedMember.getUsername();

            if (adminId != null && ("MANAGER".equals(adminRole) || "STAFF".equals(adminRole))) {
                systemLogService.log("ADMIN", adminId, adminName,
                    "REGISTER", "MEMBER", savedMember.getMemberId(), displayName,
                    "新增會員");
            } else {
                systemLogService.log("MEMBER", savedMember.getMemberId(), displayName,
                    "REGISTER", "MEMBER", savedMember.getMemberId(), displayName,
                    "新會員註冊成功");
            }

            // 註冊成功後直接產生 Token（註冊即登入）
            String token = jwtUtil.generateToken(
                savedMember.getMemberId(),
                savedMember.getUsername(),
                "MEMBER"
            );
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("member", new MemberResponseDTO(savedMember));
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 3. 搜尋
    @GetMapping("/search")
    public List<MemberResponseDTO> search(@RequestParam String keyword) {
        return memberService.searchMembers(keyword).stream()
                .map(MemberResponseDTO::new)
                .collect(java.util.stream.Collectors.toList());
    }

    // 4. 取得個人資料（從 JWT 的 request attribute 取得 userId）
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("jwtUserId");
        if (userId == null) {
            return ResponseEntity.status(401).body("未登入");
        }
        return memberService.getMemberById(userId)
                .map(MemberResponseDTO::new)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 5. 更新資料（從 JWT 的 request attribute 取得 userId）
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Member member, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("jwtUserId");
        if (userId == null) {
            return ResponseEntity.status(401).body("請重新登入後再修改");
        }
        member.setMemberId(userId);
        Member updated = memberService.updateMember(member);
        if (updated != null) {
            return ResponseEntity.ok(new MemberResponseDTO(updated));
        }
        return ResponseEntity.badRequest().body("更新失敗");
    }

    // 5.5 修改密碼
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> data, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("jwtUserId");
        if (userId == null) {
            return ResponseEntity.status(401).body("請重新登入後再操作");
        }
        
        String oldPassword = data.get("oldPassword");
        String newPassword = data.get("newPassword");
        
        if (oldPassword == null || newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body("密碼格式不正確或長度不足 6 位");
        }
        
        try {
            boolean success = memberService.changePassword(userId, oldPassword, newPassword);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "密碼修改成功"));
            }
            return ResponseEntity.badRequest().body("修改失敗");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("jwtUserId");
        String username = (String) request.getAttribute("jwtUsername");
        String role = (String) request.getAttribute("jwtRole");
        
        if (userId != null && username != null) {
            String fullName = memberService.getMemberById(userId).map(m -> m.getFullName()).orElse(null);
            String displayName = (fullName != null && !fullName.isEmpty() ? fullName : "未填寫") + " " + username;
            
            String operatorType = "MEMBER";
            if ("MANAGER".equals(role) || "STAFF".equals(role)) {
                operatorType = "ADMIN";
            }
            
            systemLogService.log(operatorType, userId, displayName, "LOGOUT", "SYSTEM", null, displayName, operatorType.equals("ADMIN") ? "職員登出系統" : "會員登出系統");
        }
        
        return ResponseEntity.ok("已成功登出");
    }

    // 7. 發送驗證碼到會員信箱
    @PostMapping("/send-verification-code")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> data) {
        String username = data.get("username");
        String email = data.get("email");

        if (username == null || email == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "請輸入帳號和電子信箱"));
        }

        // 檢查帳號 + Email 是否匹配
        var member = memberService.findByUsernameAndEmail(username.trim(), email.trim());
        if (member.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "找不到符合的會員，請確認帳號與信箱是否正確"));
        }

        // 產生 6 位數驗證碼
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);
        verificationCodeStore.save(email.trim(), code);

        // 寄送驗證碼
        try {
            emailService.sendVerificationCode(email.trim(), code);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "驗證碼寄送失敗，請稍後再試"));
        }

        return ResponseEntity.ok(Map.of("message", "驗證碼已寄送至您的信箱"));
    }

    // 7.5 發送驗證碼到信箱 (用於註冊，不檢查帳號是否已存在)
    @PostMapping("/send-register-code")
    public ResponseEntity<?> sendRegisterCode(@RequestBody Map<String, String> data) {
        String email = data.get("email");
        String username = data.get("username");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "請輸入電子信箱"));
        }

        // 為了 Demo 測試，暫時關閉「檢查帳號是否已被註冊」的限制
        /*
        if (username != null && !username.trim().isEmpty()) {
            if (memberService.isUsernameExists(username.trim())) {
                return ResponseEntity.badRequest().body(Map.of("message", "此帳號已被使用，請更換一個"));
            }
        }
        */

        // 產生 6 位數驗證碼
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);
        verificationCodeStore.save(email.trim(), code);

        // 寄送驗證碼
        try {
            emailService.sendRegisterVerificationCode(email.trim(), code);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "驗證碼寄送失敗，請稍後再試"));
        }

        return ResponseEntity.ok(Map.of("message", "註冊驗證碼已寄送至您的信箱"));
    }

    // 8. 忘記密碼 — 使用驗證碼重設密碼
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> data) {
        String username = data.get("username");
        String email = data.get("email");
        String code = data.get("code");
        String newPassword = data.get("newPassword");

        if (username == null || email == null || code == null || newPassword == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "所有欄位都必須填寫"));
        }
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "新密碼至少需要 6 個字元"));
        }

        // 驗證碼檢查
        if (!verificationCodeStore.verify(email.trim(), code.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "驗證碼錯誤或已過期，請重新取得"));
        }

        // 重設密碼
        boolean success = memberService.resetPasswordByEmail(username.trim(), email.trim(), newPassword);
        if (success) {
            verificationCodeStore.remove(email.trim()); // 驗證碼一次性，用完就刪
            return ResponseEntity.ok(Map.of("message", "密碼已重設成功，請使用新密碼登入"));
        }
        return ResponseEntity.badRequest().body(Map.of("message", "重設失敗，請確認帳號與信箱是否正確"));
    }

    // 9. 上傳會員頭像
    @PostMapping("/upload-avatar")
    public ResponseEntity<?> uploadAvatar(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("jwtUserId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "請重新登入"));
        }

        try {
            // 建立上傳目錄（使用絕對路徑）
            java.io.File dir = new java.io.File("./uploads/members").getAbsoluteFile();
            if (!dir.exists()) dir.mkdirs();

            // 產生唯一檔名
            String ext = file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
            String fileName = "member_" + userId + "_" + System.currentTimeMillis() + ext;

            // 儲存檔案（使用絕對路徑）
            java.io.File dest = new java.io.File(dir, fileName);
            file.transferTo(dest.getAbsoluteFile());

            // 更新 DB 的 profilePicture 欄位
            String imageUrl = "/uploads/members/" + fileName;
            memberService.getMemberById(userId).ifPresent(m -> {
                m.setProfilePicture(imageUrl);
                memberService.updateMember(m);
            });

            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("message", "上傳失敗：" + e.getMessage()));
        }
    }
}
