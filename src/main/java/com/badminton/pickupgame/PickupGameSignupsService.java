package com.badminton.pickupgame;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PickupGameSignupsService {

	@Autowired
	private PickupGameSignupsRepository signupsRepo;
	
	@Autowired
	private PickupGameRepository pickupGameRepo;

	@Autowired
	private PickupGameEmailService pickupGameEmailService;

	// ===== 查詢 =====

	/** 查詢全部報名紀錄 */
	public List<PickupGameSignups> findAll() {
		return signupsRepo.findAll();
	}

	/** 根據 ID 查詢單一報名紀錄 */
	public PickupGameSignups findById(Integer id) {
		return signupsRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("找不到報名紀錄 ID: " + id));
	}

	/** 根據揪團 ID 查詢該場所有報名紀錄 */
	public List<PickupGameSignups> findByGameId(Integer gameId){
		return signupsRepo.findByGame_GameId(gameId);
	}

	/** 查詢某會員所有的報名紀錄 */
	public List<PickupGameSignups> findByMemberId(Integer memberId) {
		return signupsRepo.findByMember_MemberId(memberId);
	}
	
	// ===== 新增 / 更新 =====

	/** 新增或更新報名紀錄（有 ID 就 UPDATE，沒有就 INSERT），必須有驗證流程 */
	
	public PickupGameSignups save(PickupGameSignups signup) {
		
	// ====== ① 查出揪團，確認存在 ======
		Integer gameId = signup.getGame().getGameId();
		PickupGames game = pickupGameRepo.findById(gameId)
		.orElseThrow(()-> new RuntimeException("找不到揪團 ID: " + gameId));
		
	// ====== ② 檢查揪團狀態 ======
	// 如果狀態是 CLOSED (手動關閉) 或 CANCELLED (已取消)，則絕對不允許報名
	if (game.getStatus() == PickupGameStatus.CLOSED || game.getStatus() == PickupGameStatus.CANCELLED) {
		throw new RuntimeException("此揪團目前不開放報名");
	}
	
	// 如果狀態是 FULL，但目前人數其實還沒滿（例如管理員剛調高了人數上限），則允許放行！
	// 如果人數真的滿了，才擋下來。
	if (game.getCurrentPlayers() >= game.getMaxPlayers()) {
		throw new RuntimeException("此揪團名額已滿");
	}
	
	Integer memberId = signup.getMember().getMemberId();
	
	// ====== ③ 檢查是否重複報名，或是以前取消過要重新報名 ======
	java.util.Optional<PickupGameSignups> existingOpt = signupsRepo.findByGame_GameIdAndMember_MemberId(gameId, memberId);
	if (existingOpt.isPresent()) {
		PickupGameSignups existing = existingOpt.get();
		if (existing.getStatus() == SignupStatus.JOINED) {
			throw new RuntimeException("您已經報名過此揪團");
		} else {
			// 原本是 CANCELLED 或其他狀態，更新回 JOINED
			existing.setStatus(SignupStatus.JOINED);
			PickupGameSignups saved = signupsRepo.save(existing);
			syncGamePlayerCount(game);
			return saved;
		}
	}
	
	// ====== ④ 衝堂檢查 (防分身術) ======
	// 撈出此會員所有已加入的報名
	List<PickupGameSignups> mySignups = signupsRepo.findByMember_MemberId(memberId);
	for (PickupGameSignups s : mySignups) {
		if (s.getStatus() == SignupStatus.JOINED) {
			PickupGames existingGame = s.getGame();
			// 檢查是否同一天
			if (existingGame.getGameDate().equals(game.getGameDate())) {
				// 檢查時間是否有交集：(新開始 < 舊結束) AND (新結束 > 舊開始)
				if (game.getStartTime().isBefore(existingGame.getEndTime()) && 
					game.getEndTime().isAfter(existingGame.getStartTime())) {
					throw new RuntimeException("您在同一時段已經報名了另一場揪團，無法重複參加！");
				}
			}
		}
	}
	
	// ====== ⑤ 儲存全新報名 ======
	PickupGameSignups saved = signupsRepo.save(signup);
	syncGamePlayerCount(game);
	
	return saved;
	}


	// ===== 刪除 =====

	/** 根據 ID 刪除報名紀錄，同時更新揪團人數與狀態，並寄送移除通知 Email */
	public void deleteById(Integer id) {
		// ① 先查出報名紀錄，取得關聯的揪團與被移除的球友
		PickupGameSignups signup = signupsRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("找不到報名紀錄 ID: " + id));

		PickupGames game = signup.getGame();

		// 🌟 先暫存被移除球友的資料（刪除後就拿不到了）
		String memberName = signup.getMember() != null ? signup.getMember().getFullName() : "球友";
		String memberEmail = signup.getMember() != null ? signup.getMember().getEmail() : null;
		String hostName = game.getHost() != null ? game.getHost().getFullName() : "團主";
		String hostPhone = game.getHost() != null ? game.getHost().getPhone() : null;
		String gameInfo = game.getGameDate() + " " + game.getStartTime() + "-" + game.getEndTime();

		// ② 刪除報名紀錄
		signupsRepo.deleteById(id);

		// ③ 動態計算人數並同步揪團狀態
		syncGamePlayerCount(game);

		// 🌟 ④ 寄送移除通知 Email（刪除成功後才寄，避免誤發）
		if (memberEmail != null && !memberEmail.trim().isEmpty()) {
			pickupGameEmailService.sendRemovalNotice(memberEmail, memberName, gameInfo, hostName, hostPhone);
		}
	}

	/** 根據會員ID和揪團ID刪除報名紀錄 (前台退出揪團用) */
	public void deleteByMemberAndGame(Integer memberId, Integer gameId) {
		List<PickupGameSignups> signups = signupsRepo.findByMember_MemberId(memberId);
		for (PickupGameSignups s : signups) {
			if (s.getGame().getGameId().equals(gameId)) {
				deleteById(s.getSignupId());
				return;
			}
		}
		throw new RuntimeException("找不到對應的報名紀錄");
	}
	
	// ===== 共用：同步揪團人數與狀態 =====
	
	/**
	 * 從報名表動態 COUNT 出「JOINED」狀態的人數，
	 * 寫回揪團的 currentPlayers，並自動判斷 FULL / OPEN。
	 * 
	 * 主揪(host)算在 +1 裡面，所以公式是：
	 *   currentPlayers = 1(主揪) + COUNT(JOINED 的報名)
	 */
	private void syncGamePlayerCount(PickupGames game) {
		// 從 DB 即時 COUNT
		int joinedCount = signupsRepo.countByGame_GameIdAndStatus(
				game.getGameId(), SignupStatus.JOINED);
		
		// 主揪 + 報名人數
		int total = 1 + joinedCount;
		game.setCurrentPlayers(total);
		
		// 自動切換狀態：滿人 → FULL，有空位 → OPEN
		if (total >= game.getMaxPlayers()) {
			game.setStatus(PickupGameStatus.FULL);
		} else if (game.getStatus() == PickupGameStatus.FULL) {
			// 只有原本是 FULL 的才改回 OPEN（CANCELLED / CLOSED 不動）
			game.setStatus(PickupGameStatus.OPEN);
		}
		
		pickupGameRepo.save(game);
	}
}

