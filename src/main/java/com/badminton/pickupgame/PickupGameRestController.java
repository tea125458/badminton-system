package com.badminton.pickupgame;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.badminton.config.AuthHolder;
import com.badminton.config.JwtUtil;
import com.badminton.member.Member;
import com.badminton.member.MemberService;
import com.badminton.pickupgame.PickupGameEmailService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/pickup-games")
public class PickupGameRestController {

	@Autowired
	private PickupGamesService pickupGamesService;
	
	@Autowired
	private PickupGameSignupsService signupsService;

	@Autowired
	private PickupGameEmailService pickupGameEmailService;

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private MemberService memberService;

	// GET /api/pickup-games
	@GetMapping
	public List<PickupGames> findAll() {
		return pickupGamesService.findAll();
	}

	// GET /api/pickup-games/3
	@GetMapping("/{id}")
	public PickupGames findById(@PathVariable Integer id) {
		return pickupGamesService.findById(id);
	}

	// POST /api/pickup-games  (Body: JSON)
	@PostMapping
	public PickupGames create(@RequestBody PickupGames game) {
		return pickupGamesService.save(game);
	}

	// PUT /api/pickup-games/3
	@PutMapping("/{id}")
	public PickupGames update(@PathVariable Integer id, @RequestBody PickupGames game, HttpServletRequest request) {
		PickupGames oldGame = pickupGamesService.findById(id);

		// 如果已經是取消狀態，且前端又傳來取消，直接忽略更新
		if (oldGame.getStatus() == PickupGameStatus.CANCELLED && game.getStatus() == PickupGameStatus.CANCELLED) {
			return oldGame;
		}

		// 若為「取消揪團」操作
		if (oldGame.getStatus() != PickupGameStatus.CANCELLED && game.getStatus() == PickupGameStatus.CANCELLED) {
			
			// 從 Token 解析身分（因為此 API 沒有在 Interceptor 的攔截範圍內，所以手動解析）
			Integer userId = null;
			String role = null;
			String authHeader = request.getHeader("Authorization");
			System.out.println("======== CANCEL GAME DEBUG ========");
			System.out.println("AuthHeader: " + authHeader);
			
			if (authHeader != null && authHeader.startsWith("Bearer ")) {
				try {
					Claims claims = jwtUtil.parseToken(authHeader.substring(7));
					userId = claims.get("userId", Integer.class);
					role = claims.get("role", String.class);
					System.out.println("Parsed from JWT - userId: " + userId + ", role: " + role);
				} catch (Exception e) {
					System.out.println("JWT Parse Exception: " + e.getMessage());
					e.printStackTrace();
				}
			}

			// 也可以回退到 AuthHolder (防萬一未來加入了 Interceptor)
			if (userId == null && AuthHolder.isLoggedIn()) {
				userId = AuthHolder.getUserId();
				role = AuthHolder.getRole();
				System.out.println("Got from AuthHolder - userId: " + userId + ", role: " + role);
			}

			// 安全性檢查：只有後台管理員，或原團主可以取消
			boolean isAuthorized = false;
			System.out.println("Game Host ID: " + (oldGame.getHost() != null ? oldGame.getHost().getMemberId() : "NULL"));
			
			if ("MANAGER".equals(role) || "STAFF".equals(role)) {
				isAuthorized = true; // 管理員放行
			} else if (userId != null && oldGame.getHost() != null 
					&& userId.equals(oldGame.getHost().getMemberId())) {
				isAuthorized = true; // 原團主放行
			}

			System.out.println("isAuthorized: " + isAuthorized);
			System.out.println("===================================");

			if (!isAuthorized) {
				throw new RuntimeException("權限不足：您不是該場揪團的團主或管理員");
			}

			return pickupGamesService.cancelGame(id);
		}

		// 一般更新操作
		game.setGameId(id);
		return pickupGamesService.save(game);
	}

	// DELETE /api/pickup-games/3
	@DeleteMapping("/{id}")
	public void delete(@PathVariable Integer id) {
		pickupGamesService.deleteById(id);
	}
	
	// 原本的回傳型態是 List<PickupGameSignups>，現在改成 List<PickupGameSignupResponseDTO>
	@GetMapping("/{gameId}/signups")
	public List<PickupGameSignupResponseDTO> findSignupsByGameId(@PathVariable Integer gameId){
		// 1. 從資料庫撈出原始的 Entity 列表
		List<PickupGameSignups> signups = signupsService.findByGameId(gameId);
		
		// 2. 利用 Java Stream 將每一個 Entity 轉換為 DTO（轉換過程中就會自動完成脫敏）
		return signups.stream()
				.map(PickupGameSignupResponseDTO::new)
				.collect(Collectors.toList());
	}

	// ============================
	// 🌟 POST /api/pickup-games/{gameId}/broadcast
	// 團主群發公告 Email 給所有已報名球友
	// ============================
	@PostMapping("/{gameId}/broadcast")
	public ResponseEntity<Map<String, Object>> broadcast(
			@PathVariable Integer gameId,
			@RequestBody Map<String, String> body) {

		String message = body.get("message");
		if (message == null || message.trim().isEmpty()) {
			return ResponseEntity.badRequest()
					.body(Map.of("success", false, "error", "公告內容不可為空"));
		}

		// ① 查出揪團資訊（用於信件內文的球局摘要）
		PickupGames game = pickupGamesService.findById(gameId);
		String hostName = game.getHost() != null ? game.getHost().getFullName() : "團主";
		String gameInfo = game.getGameDate() + " " + game.getStartTime() + "-" + game.getEndTime();

		// ② 撈出所有已報名球友的 Email（過濾空值）
		List<PickupGameSignups> signups = signupsService.findByGameId(gameId);
		List<String> emails = signups.stream()
				.filter(s -> s.getMember() != null && s.getMember().getEmail() != null)
				.map(s -> s.getMember().getEmail())
				.filter(email -> !email.trim().isEmpty())
				.collect(Collectors.toList());

		if (emails.isEmpty()) {
			return ResponseEntity.ok(
					Map.of("success", true, "sent", 0, "message", "目前沒有已報名的球友 Email"));
		}

		// ③ 群發 Email
		int sent = pickupGameEmailService.sendBroadcast(emails, hostName, gameInfo, message.trim());

		return ResponseEntity.ok(
				Map.of("success", true, "sent", sent, "total", emails.size()));
	}

	// ============================
	// 🌟 POST /api/pickup-games/{gameId}/contact-host
	// 球友透過系統寄送 Email 聯絡主揪
	// ============================
	@PostMapping("/{gameId}/contact-host")
	public ResponseEntity<Map<String, Object>> contactHost(
			@PathVariable Integer gameId,
			@RequestBody Map<String, String> body,
			HttpServletRequest request) {
		
		String message = body.get("message");
		if (message == null || message.trim().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "error", "訊息內容不可為空"));
		}

		// 解析 JWT 取得寄件者身分
		Integer userId = null;
		String authHeader = request.getHeader("Authorization");
		System.out.println("======== CONTACT HOST DEBUG ========");
		System.out.println("authHeader: " + authHeader);
		String jwtErrorMsg = null;
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			try {
				Claims claims = jwtUtil.parseToken(authHeader.substring(7));
				userId = claims.get("userId", Integer.class);
				System.out.println("Parsed userId: " + userId);
			} catch (Exception e) {
				jwtErrorMsg = e.getMessage();
				System.out.println("JWT Parse Exception: " + jwtErrorMsg);
			}
		} else {
			System.out.println("authHeader is missing or invalid");
		}

		if (userId == null && AuthHolder.isLoggedIn()) {
			userId = AuthHolder.getUserId();
		}

		if (userId == null) {
			String errorReason = "未知錯誤";
			if (authHeader == null) errorReason = "沒有提供 Token";
			else if (authHeader.equals("Bearer null")) errorReason = "Token 為字串 null";
			else if (!authHeader.startsWith("Bearer ")) errorReason = "Token 格式錯誤 (不是 Bearer)";
			else errorReason = "JWT 錯誤: " + (jwtErrorMsg != null ? jwtErrorMsg : "未知解析失敗");
			System.out.println("Contact Host Failed: " + errorReason);
			return ResponseEntity.status(401).body(Map.of("success", false, "error", "請先登入系統 (" + errorReason + ")"));
		}

		// 取得揪團與主揪資訊
		PickupGames game = pickupGamesService.findById(gameId);
		if (game == null) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "error", "找不到該揪團"));
		}
		
		if (game.getHost() == null || game.getHost().getEmail() == null || game.getHost().getEmail().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("success", false, "error", "該揪團主尚未提供信箱"));
		}

		// 從資料庫查詢寄件者的會員姓名
		String memberName = "球友";
		try {
			java.util.Optional<Member> senderOpt = memberService.getMemberById(userId);
			if (senderOpt.isPresent()) {
				Member sender = senderOpt.get();
				memberName = sender.getFullName() != null ? sender.getFullName() : "球友";
			}
		} catch (Exception e) {
			System.err.println("查詢寄件者資料失敗: " + e.getMessage());
		}

		String hostName = game.getHost().getFullName();
		String gameInfo = game.getGameDate() + " " + game.getStartTime() + "-" + game.getEndTime();

		pickupGameEmailService.sendContactHostEmail(game.getHost().getEmail(), hostName, memberName, gameInfo, message.trim());

		return ResponseEntity.ok(Map.of("success", true, "message", "信件已發送成功"));
	}
}
