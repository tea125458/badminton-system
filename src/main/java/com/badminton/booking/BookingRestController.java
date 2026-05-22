package com.badminton.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.badminton.common.SystemLogService;
import com.badminton.member.Member;
import com.badminton.member.MemberRepository;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/bookings")
public class BookingRestController {
	
	@Autowired
	private BookingService bookingService;
	
	@Autowired
	private MemberRepository memberRepo;
	
	@Autowired
	private SystemLogService systemLogService;
	
	// GET /api/bookings
	@GetMapping
	public List<Booking> findAll(){
		return bookingService.findAll();
	}
	
	// GET /api/bookings/3
	@GetMapping("/{id}")
	public Booking findById(@PathVariable Integer id) {
		return bookingService.findById(id);
				
	}
	
	// POST /api/bookings — 建立預約（從 JWT 取得會員身份，防止偽造）
	@PostMapping
	public ResponseEntity<?> create(@RequestBody Booking booking, HttpServletRequest request) {
		try {
			// 從 JWT 取得已驗證的使用者資訊（由 MemberAuthInterceptor 設定）
			Integer jwtUserId = (Integer) request.getAttribute("jwtUserId");
			String jwtRole = (String) request.getAttribute("jwtRole");
			if (jwtUserId == null) {
				return ResponseEntity.status(401).body(Map.of("message", "請先登入"));
			}
			
			if ("MANAGER".equals(jwtRole) || "STAFF".equals(jwtRole)) {
				// 管理員：允許指定 memberId（後台幫會員建預約）
				if (booking.getMember() != null && booking.getMember().getMemberId() != 0) {
					Member member = memberRepo.findById(booking.getMember().getMemberId())
							.orElseThrow(() -> new RuntimeException("找不到指定的會員"));
					booking.setMember(member);
				} else {
					// 管理員透過前台自己預約時，沒帶 member → 用自己的 JWT userId
					Member member = memberRepo.findById(jwtUserId)
							.orElseThrow(() -> new RuntimeException("找不到會員資料"));
					booking.setMember(member);
				}
			} else {
				// 一般會員：強制使用 JWT 的 userId，防止冒用他人身份
				Member member = memberRepo.findById(jwtUserId)
						.orElseThrow(() -> new RuntimeException("找不到會員資料"));
				booking.setMember(member);
			}
			
			return ResponseEntity.ok(bookingService.save(booking));
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
	
	// PUT /api/bookings/3 — 編輯預約（僅限管理員）
	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable Integer id,
			@RequestBody Booking updated, HttpServletRequest request) {
		try {
			Integer jwtUserId = (Integer) request.getAttribute("jwtUserId");
			String jwtRole = (String) request.getAttribute("jwtRole");
			String jwtUsername = (String) request.getAttribute("jwtUsername");
			if (jwtUserId == null) {
				return ResponseEntity.status(401).body(Map.of("message", "請先登入"));
			}
			
			// 僅限管理員操作
			if (!"MANAGER".equals(jwtRole) && !"STAFF".equals(jwtRole)) {
				return ResponseEntity.status(403).body(Map.of("message", "僅限管理員編輯預約"));
			}
			
			// 記錄修改前的狀態（用於日誌）
			Booking before = bookingService.findById(id);
			String beforeInfo = String.format("%s %s %s~%s",
					before.getCourt() != null ? before.getCourt().getCourtName() : "",
					before.getBookingDate(),
					before.getStartTime(), before.getEndTime());
			
			// 執行更新
			Booking result = bookingService.update(id, updated);
			
			// 記錄修改後的狀態
			String afterInfo = String.format("%s %s %s~%s",
					result.getCourt() != null ? result.getCourt().getCourtName() : "",
					result.getBookingDate(),
					result.getStartTime(), result.getEndTime());
			
			// 寫入操作日誌
			String memberName = result.getMember() != null ? result.getMember().getFullName() : "未知";
			String details = String.format("修改前: %s → 修改後: %s", beforeInfo, afterInfo);
			if (updated.getNote() != null && !updated.getNote().isEmpty()) {
				details += ", 備註: " + updated.getNote();
			}
			systemLogService.log("ADMIN", jwtUserId, jwtUsername,
					"UPDATE_BOOKING", "BOOKING", id, memberName, details);
			
			return ResponseEntity.ok(result);
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
	
	// DELETE /api/bookings/3 — 刪除預約（僅限管理員）
	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable Integer id, HttpServletRequest request) {
		try {
			Integer jwtUserId = (Integer) request.getAttribute("jwtUserId");
			String jwtRole = (String) request.getAttribute("jwtRole");
			String jwtUsername = (String) request.getAttribute("jwtUsername");
			if (jwtUserId == null) {
				return ResponseEntity.status(401).body(Map.of("message", "請先登入"));
			}
			if (!"MANAGER".equals(jwtRole) && !"STAFF".equals(jwtRole)) {
				return ResponseEntity.status(403).body(Map.of("message", "僅限管理員刪除預約"));
			}
			
			// 記錄刪除日誌（刪除前先撈資料）
			Booking booking = bookingService.findById(id);
			String memberName = booking.getMember() != null ? booking.getMember().getFullName() : "未知";
			String details = String.format("刪除預約: %s %s %s~%s, 金額: %s",
					booking.getCourt() != null ? booking.getCourt().getCourtName() : "",
					booking.getBookingDate(),
					booking.getStartTime(), booking.getEndTime(),
					booking.getTotalAmount());
			
			bookingService.deleteById(id);
			
			systemLogService.log("ADMIN", jwtUserId, jwtUsername,
					"DELETE_BOOKING", "BOOKING", id, memberName, details);
			
			return ResponseEntity.ok(Map.of("message", "刪除成功"));
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
	
	// PATCH /api/bookings/3/status  (Body: {"status":"CONFIRMED"})
	@PatchMapping("/{id}/status")
	public ResponseEntity<?> updateStatus(@PathVariable Integer id,
			@RequestBody Map<String, String> body, HttpServletRequest request) {
		try {
			Integer jwtUserId = (Integer) request.getAttribute("jwtUserId");
			String jwtRole = (String) request.getAttribute("jwtRole");
			if (jwtUserId == null) {
				return ResponseEntity.status(401).body(Map.of("message", "請先登入"));
			}
			
			BookingStatus newStatus = BookingStatus.valueOf(body.get("status"));
			Booking booking = bookingService.findById(id);
			
			if ("MANAGER".equals(jwtRole) || "STAFF".equals(jwtRole)) {
				// 管理員：可變更任何預約的任何狀態
				return ResponseEntity.ok(bookingService.updateStatus(id, newStatus));
			} else {
				// 一般會員：只能取消「自己的」預約
				if (booking.getMember() == null || booking.getMember().getMemberId() != jwtUserId) {
					return ResponseEntity.status(403).body(Map.of("message", "您只能操作自己的預約"));
				}
				if (newStatus != BookingStatus.CANCELLED) {
					return ResponseEntity.status(403).body(Map.of("message", "您只能取消預約"));
				}
				// 只有 PENDING / CONFIRMED 狀態才能取消
				BookingStatus currentStatus = booking.getStatus();
				if (currentStatus != BookingStatus.PENDING && currentStatus != BookingStatus.CONFIRMED) {
					return ResponseEntity.badRequest().body(Map.of("message", 
							"此預約狀態為「" + currentStatus + "」，無法取消"));
				}
				// ★ 取消時間限制：預約日前一天 23:59 前可取消，當天禁止
				LocalDate today = LocalDate.now();
				if (!booking.getBookingDate().isAfter(today)) {
					return ResponseEntity.badRequest().body(Map.of("message", 
							"預約當天無法取消，請於預約日前一天 23:59 前取消"));
				}
				return ResponseEntity.ok(bookingService.updateStatus(id, newStatus));
			}
		} catch (RuntimeException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}
	
	// GET /api/bookings/search?keyword=總館
	@GetMapping("/search")
	public List<Booking> search(@RequestParam(required = false) String keyword){
		return bookingService.searchByKeyword(keyword);
		
	}
	
	// GET /api/bookings/court/5/date/2026-05-15
	@GetMapping("/court/{courtId}/date/{date}")
	public List<Booking> findByCourtAndDate(
	        @PathVariable Integer courtId,
	        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
	    return bookingService.findByCourtAndDate(courtId, date);
	}

	// GET /api/bookings/my-bookings — 取得自己未來已確認的預約（前台發起揪團用）
	@GetMapping("/my-bookings")
	public ResponseEntity<?> getMyBookings(HttpServletRequest request) {
		Integer userId = (Integer) request.getAttribute("jwtUserId");
		if (userId == null) {
			return ResponseEntity.status(401).body("請先登入");
		}
		return ResponseEntity.ok(bookingService.findMyUpcomingBookings(userId));
	}

	// GET /api/bookings/my-all-bookings — 取得自己的所有預約紀錄（會員中心用）
	@GetMapping("/my-all-bookings")
	public ResponseEntity<?> getMyAllBookings(HttpServletRequest request) {
		Integer userId = (Integer) request.getAttribute("jwtUserId");
		if (userId == null) {
			return ResponseEntity.status(401).body("請先登入");
		}
		return ResponseEntity.ok(bookingService.findAllByMemberId(userId));
	}

}
