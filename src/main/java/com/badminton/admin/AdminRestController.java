package com.badminton.admin;

import com.badminton.config.JwtUtil;
import com.badminton.member.Member;
import com.badminton.member.MemberService;
import com.badminton.member.MemberStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.badminton.common.SystemLogService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admins")
public class AdminRestController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SystemLogService systemLogService;

    // 幫助方法：記錄日誌
    private void logAdminAction(HttpServletRequest request, String action, String targetType, Integer targetId, String targetName, String details) {
        Integer adminId = (Integer) request.getAttribute("jwtUserId");
        String username = (String) request.getAttribute("jwtUsername");
        
        if (adminId != null && username != null) {
            Admin admin = adminService.getAdminById(adminId).orElse(null);
            String operatorName = admin != null ? formatName(admin.getFullName(), admin.getUsername()) : username;
            String actualTargetName = (targetName == null || "系統".equals(targetName)) ? operatorName : targetName;
            systemLogService.log("ADMIN", adminId, operatorName, action, targetType, targetId, actualTargetName, details);
        } else if (username != null) {
            // Fallback: 如果只有 username (例如 Token 解析出來但找不到 ID)
            String actualTargetName = (targetName == null || "系統".equals(targetName)) ? username : targetName;
            systemLogService.log("ADMIN", adminId, username, action, targetType, targetId, actualTargetName, details);
        }
    }

    // 格式化顯示名稱：姓名 帳號
    private String formatName(String fullName, String username) {
        String name = fullName != null && !fullName.trim().isEmpty() ? fullName : "未填寫";
        return name + " " + username;
    }

    // 格式化修改欄位
    private void appendChange(StringBuilder sb, String fieldName, Object oldValue, Object newValue) {
        if (!java.util.Objects.equals(oldValue, newValue)) {
            String oldStr = (oldValue == null || oldValue.toString().trim().isEmpty()) ? "未填寫" : oldValue.toString();
            String newStr = (newValue == null || newValue.toString().trim().isEmpty()) ? "未填寫" : newValue.toString();
            sb.append(fieldName).append(" [").append(oldStr).append(" -> ").append(newStr).append("] ");
        }
    }

    // 管理員登入（回傳 JWT Token）
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        return adminService.login(loginData.get("username"), loginData.get("password"))
            .<ResponseEntity<?>>map(admin -> {
                String token = jwtUtil.generateToken(admin.getAdminId(), admin.getUsername(), admin.getRole().name());
                Map<String, Object> result = new HashMap<>();
                result.put("token", token);
                result.put("admin", admin);
                
                // 登入時手動寫入日誌
                String displayName = formatName(admin.getFullName(), admin.getUsername());
                systemLogService.log("ADMIN", admin.getAdminId(), displayName, "LOGIN", "SYSTEM", admin.getAdminId(), displayName, "職員登入系統");
                
                return ResponseEntity.ok(result);
            })
            .orElse(ResponseEntity.status(401).body("帳號或密碼錯誤"));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {
        Integer adminId = (Integer) request.getAttribute("jwtUserId");
        if (adminId == null) return ResponseEntity.status(401).body("未登入");
        return adminService.getAdminById(adminId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Admin admin, HttpServletRequest request) {
        Integer adminId = (Integer) request.getAttribute("jwtUserId");
        if (adminId == null) return ResponseEntity.status(401).body("請重新登入後再修改");
        
        Admin oldAdmin = adminService.getAdminById(adminId).orElse(null);
        String oldName = oldAdmin != null ? oldAdmin.getFullName() : null;
        String oldEmail = oldAdmin != null ? oldAdmin.getEmail() : null;
        String oldPhone = oldAdmin != null ? oldAdmin.getPhone() : null;
        String oldGender = oldAdmin != null ? oldAdmin.getGender() : null;
        java.time.LocalDate oldBirthday = oldAdmin != null ? oldAdmin.getBirthday() : null;
        
        admin.setAdminId(adminId);
        Admin updated = adminService.updateAdmin(admin);
        
        if (updated != null && oldAdmin != null) {
            StringBuilder changes = new StringBuilder("修改個人資料: ");
            appendChange(changes, "姓名", oldName, updated.getFullName());
            appendChange(changes, "信箱", oldEmail, updated.getEmail());
            appendChange(changes, "手機號碼", oldPhone, updated.getPhone());
            appendChange(changes, "性別", oldGender, updated.getGender());
            appendChange(changes, "生日", oldBirthday, updated.getBirthday());
            
            String displayName = formatName(updated.getFullName(), updated.getUsername());
            logAdminAction(request, "UPDATE_PROFILE", "ADMIN", updated.getAdminId(), displayName, changes.length() > 9 ? changes.toString() : "未修改實質資料");
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.badRequest().body("更新失敗");
    }

    @GetMapping("/list")
    public List<Admin> getAllAdmins(HttpServletRequest request) {
        return adminService.getAllAdmins();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAdminById(@PathVariable int id, HttpServletRequest request) {
        return adminService.getAdminById(id)
                .<ResponseEntity<?>>map(admin -> {
                    return ResponseEntity.ok(admin);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/add")
    public ResponseEntity<?> addAdmin(@RequestBody Admin admin, HttpServletRequest request) {
        try {
            Admin savedAdmin = adminService.addAdmin(admin);

            // 記錄操作日誌
            String displayName = formatName(savedAdmin.getFullName(), savedAdmin.getUsername());
            logAdminAction(request, "ADD_ADMIN", "ADMIN", savedAdmin.getAdminId(), displayName, "新增職員");

            return ResponseEntity.ok(savedAdmin);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAdmin(@PathVariable int id, @RequestBody Admin admin, HttpServletRequest request) {
        Admin oldAdmin = adminService.getAdminById(id).orElse(null);
        String oldName = oldAdmin != null ? oldAdmin.getFullName() : null;
        com.badminton.admin.AdminRole oldRole = oldAdmin != null ? oldAdmin.getRole() : null;
        String oldEmail = oldAdmin != null ? oldAdmin.getEmail() : null;
        String oldPhone = oldAdmin != null ? oldAdmin.getPhone() : null;
        String oldGender = oldAdmin != null ? oldAdmin.getGender() : null;
        java.time.LocalDate oldBirthday = oldAdmin != null ? oldAdmin.getBirthday() : null;
        
        admin.setAdminId(id);
        Admin updated = adminService.updateAdmin(admin);
        
        if (updated != null && oldAdmin != null) {
            StringBuilder changes = new StringBuilder("修改職員資料: ");
            appendChange(changes, "姓名", oldName, updated.getFullName());
            appendChange(changes, "角色", oldRole, updated.getRole());
            appendChange(changes, "信箱", oldEmail, updated.getEmail());
            appendChange(changes, "手機號碼", oldPhone, updated.getPhone());
            appendChange(changes, "性別", oldGender, updated.getGender());
            appendChange(changes, "生日", oldBirthday, updated.getBirthday());
            
            String displayName = formatName(updated.getFullName(), updated.getUsername());
            logAdminAction(request, "UPDATE_ADMIN", "ADMIN", id, displayName, changes.length() > 10 ? changes.toString() : "未修改實質資料");
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAdmin(@PathVariable int id, HttpServletRequest request) {
        try {
            String displayName = adminService.getAdminById(id).map(a -> formatName(a.getFullName(), a.getUsername())).orElse("未知");
            adminService.deleteAdmin(id);
            logAdminAction(request, "DELETE_ADMIN", "ADMIN", id, displayName, "刪除職員");
            return ResponseEntity.ok("職員刪除成功");
        } catch (Exception e) {
            return ResponseEntity.status(404).body("刪除失敗：找不到該職員");
        }
    }

    @GetMapping("/search")
    public List<Admin> searchAdmins(@RequestParam String keyword, HttpServletRequest request) {
        return adminService.searchAdmins(keyword);
    }

    @PatchMapping("/{id}/note")
    public ResponseEntity<?> updateAdminNote(@PathVariable int id, @RequestBody Map<String, String> data, HttpServletRequest request) {
        boolean success = adminService.updateAdminNote(id, data.get("note"));
        if (success) {
            String displayName = adminService.getAdminById(id).map(a -> formatName(a.getFullName(), a.getUsername())).orElse("未知");
            String noteContent = data.get("note");
            logAdminAction(request, "UPDATE_NOTE", "ADMIN", id, displayName, "更新了職員備註為: " + (noteContent != null ? noteContent : "空"));
            return ResponseEntity.ok("備註更新成功");
        }
        return ResponseEntity.badRequest().body("更新失敗");
    }

    @GetMapping("/member")
    public List<Member> getAllMembers(HttpServletRequest request) {
        return memberService.getAllMembers();
    }

    @GetMapping("/member/search")
    public List<Member> searchMembers(@RequestParam String keyword, HttpServletRequest request) {
        return memberService.searchMembers(keyword);
    }

    @PostMapping("/member")
    public ResponseEntity<?> addMemberByAdmin(@RequestBody Member member, HttpServletRequest request) {
        try {
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
            String displayName = formatName(savedMember.getFullName(), savedMember.getUsername());
            logAdminAction(request, "ADD_MEMBER", "MEMBER", savedMember.getMemberId(), displayName, "職員新增會員");

            return ResponseEntity.ok(savedMember);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/member/{id}")
    public ResponseEntity<?> getMemberById(@PathVariable int id, HttpServletRequest request) {
        return memberService.getMemberById(id)
                .<ResponseEntity<?>>map(member -> {
                    return ResponseEntity.ok(member);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/member/{id}")
    public ResponseEntity<?> updateMemberByAdmin(@PathVariable int id, @RequestBody Member member, HttpServletRequest request) {
        Member oldMember = memberService.getMemberById(id).orElse(null);
        String oldName = oldMember != null ? oldMember.getFullName() : null;
        com.badminton.member.MembershipLevel oldLevel = oldMember != null ? oldMember.getMembershipLevel() : null;
        String oldGender = oldMember != null ? oldMember.getGender() : null;
        java.time.LocalDate oldBirthday = oldMember != null ? oldMember.getBirthday() : null;
        String oldPhone = oldMember != null ? oldMember.getPhone() : null;
        String oldEmail = oldMember != null ? oldMember.getEmail() : null;
        
        member.setMemberId(id);
        Member updated = memberService.updateMemberByAdmin(member);
        
        if (updated != null && oldMember != null) {
            StringBuilder changes = new StringBuilder("修改會員資料: ");
            appendChange(changes, "姓名", oldName, updated.getFullName());
            appendChange(changes, "等級", oldLevel, updated.getMembershipLevel());
            appendChange(changes, "性別", oldGender, updated.getGender());
            appendChange(changes, "生日", oldBirthday, updated.getBirthday());
            appendChange(changes, "手機號碼", oldPhone, updated.getPhone());
            appendChange(changes, "信箱", oldEmail, updated.getEmail());
            
            String displayName = formatName(updated.getFullName(), updated.getUsername());
            logAdminAction(request, "UPDATE_MEMBER", "MEMBER", id, displayName, changes.length() > 9 ? changes.toString() : "未修改實質資料");
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/member/{id}")
    public ResponseEntity<?> deleteMember(@PathVariable int id, HttpServletRequest request) {
        try {
            String displayName = memberService.getMemberById(id).map(m -> formatName(m.getFullName(), m.getUsername())).orElse("未知");
            memberService.deleteMember(id);
            logAdminAction(request, "DELETE_MEMBER", "MEMBER", id, displayName, "刪除會員");
            return ResponseEntity.ok("會員刪除成功");
        } catch (Exception e) {
            return ResponseEntity.status(404).body("刪除失敗：找不到該會員");
        }
    }

    @PatchMapping("/member/{id}/note")
    public ResponseEntity<?> updateMemberNote(@PathVariable int id, @RequestBody Map<String, String> data, HttpServletRequest request) {
        boolean success = memberService.updateNote(id, data.get("note"));
        if (success) {
            String displayName = memberService.getMemberById(id).map(m -> formatName(m.getFullName(), m.getUsername())).orElse("未知");
            String noteContent = data.get("note");
            logAdminAction(request, "UPDATE_NOTE", "MEMBER", id, displayName, "更新會員備註為: " + (noteContent != null ? noteContent : "空"));
            return ResponseEntity.ok("備註更新成功");
        }
        return ResponseEntity.badRequest().body("更新失敗");
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateAdminStatus(@PathVariable int id, @RequestBody Map<String, String> data, HttpServletRequest request) {
        return adminService.getAdminById(id).map(admin -> {
            String oldStatus = admin.getStatus() != null ? admin.getStatus().name() : "未知";
            String newStatus = data.get("status");
            admin.setStatus(AdminStatus.valueOf(newStatus));
            adminService.updateAdmin(admin);
            
            String displayName = formatName(admin.getFullName(), admin.getUsername());
            logAdminAction(request, "UPDATE_STATUS", "ADMIN", id, displayName, "職員狀態從 " + oldStatus + " 改為 " + newStatus);
            
            return ResponseEntity.ok("狀態更新成功");
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/member/{id}/status")
    public ResponseEntity<?> updateMemberStatus(@PathVariable int id, @RequestBody Map<String, String> data, HttpServletRequest request) {
        return memberService.getMemberById(id).map(member -> {
            String oldStatus = member.getStatus().name();
            String newStatus = data.get("status");
            member.setStatus(MemberStatus.valueOf(newStatus));
            memberService.updateMemberByAdmin(member);
            
            String displayName = formatName(member.getFullName(), member.getUsername());
            logAdminAction(request, "UPDATE_STATUS", "MEMBER", id, displayName, "會員狀態從 " + oldStatus + " 改為 " + newStatus);
            
            return ResponseEntity.ok("狀態更新成功");
        }).orElse(ResponseEntity.notFound().build());
    }

    // JWT 登出（stateless，由前端刪除 token）
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        logAdminAction(request, "LOGOUT", "SYSTEM", null, null, "職員登出系統");
        return ResponseEntity.ok("已成功登出");
    }
}
