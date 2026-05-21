package com.badminton.pickupgame;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PickupGamesService {

	@Autowired
	private PickupGameRepository pickupGameRepo;

	@Autowired
	private PickupGameSignupsRepository signupsRepo;

	@Autowired
	private PickupGameEmailService pickupGameEmailService;

	// ===== 查詢 =====

	/** 查詢全部揪團（同時校正 currentPlayers，確保與報名紀錄一致） */
	public List<PickupGames> findAll() {
		List<PickupGames> games = pickupGameRepo.findAllByOrderByGameDateAscStartTimeAsc();
		// 每一場揪團都即時校正人數
		games.forEach(this::syncCurrentPlayers);
		return games;
	}

	/** 根據 ID 查詢單一揪團 */
	public PickupGames findById(Integer id) {
		PickupGames game = pickupGameRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("找不到揪團 ID: " + id));
		syncCurrentPlayers(game);
		return game;
	}

	// ===== 新增 / 更新 =====

	/** 新增或更新揪團（有 ID 就 UPDATE，沒有就 INSERT） */
	public PickupGames save(PickupGames game) {
		return pickupGameRepo.save(game);
	}

	// ===== 刪除 =====

	/** 根據 ID 刪除揪團 */
	public void deleteById(Integer id) {
		pickupGameRepo.deleteById(id);
	}

	/** 取消揪團並自動發信通知球友 */
	public PickupGames cancelGame(Integer gameId) {
		PickupGames game = findById(gameId);
		
		// 如果已經取消過了，就直接回傳
		if (game.getStatus() == PickupGameStatus.CANCELLED) {
			return game;
		}

		// 1. 更新狀態為已取消
		game.setStatus(PickupGameStatus.CANCELLED);
		PickupGames savedGame = pickupGameRepo.save(game);

		// 2. 撈出所有已報名的球友 Email
		List<PickupGameSignups> signups = signupsRepo.findByGame_GameId(gameId);
		List<String> emails = signups.stream()
				.filter(s -> s.getStatus() == SignupStatus.JOINED)
				.filter(s -> s.getMember() != null && s.getMember().getEmail() != null)
				.map(s -> s.getMember().getEmail())
				.filter(email -> !email.trim().isEmpty())
				.collect(java.util.stream.Collectors.toList());

		// 3. 批次寄送取消通知信
		if (!emails.isEmpty()) {
			String hostName = game.getHost() != null ? game.getHost().getFullName() : "團主";
			String hostPhone = game.getHost() != null ? game.getHost().getPhone() : null;
			String gameInfo = game.getGameDate() + " " + game.getStartTime() + "-" + game.getEndTime();
			pickupGameEmailService.sendCancellationNotice(emails, hostName, hostPhone, gameInfo);
		}

		return savedGame;
	}

	// ===== 私有方法：校正 currentPlayers =====

	/**
	 * 從報名表 COUNT 出 JOINED 狀態的實際人數，
	 * 加上主揪 1 人，寫回 currentPlayers。
	 * 如果跟 DB 值不同才做 UPDATE，避免無謂寫入。
	 */
	private void syncCurrentPlayers(PickupGames game) {
		int joinedCount = signupsRepo.countByGame_GameIdAndStatus(
				game.getGameId(), SignupStatus.JOINED);
		int realTotal = 1 + joinedCount; // 主揪 + 已加入的報名

		if (game.getCurrentPlayers() != realTotal) {
			game.setCurrentPlayers(realTotal);
			pickupGameRepo.save(game);
		}
	}
}

