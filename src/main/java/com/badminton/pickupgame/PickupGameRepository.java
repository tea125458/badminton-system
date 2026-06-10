package com.badminton.pickupgame;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PickupGameRepository extends JpaRepository<PickupGames, Integer> {

	/** 依日期 → 開始時間 升冪排序 */
	List<PickupGames> findAllByOrderByGameDateAscStartTimeAsc();

	/** 排除壓力測試資料（description = '海量資料壓力測試產生'），只撈真實揪團 */
	@org.springframework.data.jpa.repository.Query("SELECT p FROM PickupGames p WHERE p.description IS NULL OR p.description != ?1 ORDER BY p.gameDate ASC, p.startTime ASC")
	List<PickupGames> findRealGames(String description);

	/** 查詢某位主揪的所有揪團（排除壓力測試資料） */
	@org.springframework.data.jpa.repository.Query("SELECT p FROM PickupGames p WHERE p.host.memberId = ?1 AND (p.description IS NULL OR p.description != ?2) ORDER BY p.gameDate ASC, p.startTime ASC")
	List<PickupGames> findRealGamesByHostId(Integer memberId, String description);

}
