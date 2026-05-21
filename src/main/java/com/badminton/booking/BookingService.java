package com.badminton.booking;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.badminton.court.Court;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class BookingService {
	
	@Autowired
	private BookingRepository bookingRepo;
	
	// ===== 時段重疊判斷（私有工具方法）=====
	
	/**
	 * 判斷兩筆預約的時段是否重疊
	 * 核心公式：A開始 < B結束 且 A結束 > B開始 → 重疊
	 */
	private boolean isOverlapping(Booking a, Booking b) {
		return a.getStartTime().isBefore(b.getEndTime())
			&& a.getEndTime().isAfter(b.getStartTime());
	}
	
	// ===== 查詢 =====
	
	public List<Booking> findAll(){
		return bookingRepo.findAllByOrderByBookingDateDescStartTimeDesc();	
	}
	
	public Booking findById(Integer id) {
		return bookingRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("找不到預約 ID: " + id));
	}
	
	// ===== 新增 / (客戶預約不能更新!) =====
	
	// 場地每小時單價（未來可改為從 Court 或 Venue 的欄位讀取）
	private static final int HOURLY_RATE = 300;
	
	public Booking save(Booking booking) {
		// 1. 先撈出「同場地、同日期、未取消」的所有現有預約
		List<Booking> existingBookings = bookingRepo.findByCourtAndBookingDateAndStatusNot(
				booking.getCourt(),        // 同一個場地
				booking.getBookingDate(),  // 同一天
				BookingStatus.CANCELLED);  // 排除已取消的
		
		// 2. 逐筆檢查是否有時段重疊
		for (Booking existing : existingBookings) {
			if (isOverlapping(booking, existing)) {
				throw new RuntimeException("該時段已被預約！");
			}
		}
		
		// 3. ★ 伺服器端金額計算（忽略前端傳來的金額，防止篡改）
		if (booking.getStartTime() != null && booking.getEndTime() != null) {
			long hours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
			if (hours <= 0) {
				throw new RuntimeException("結束時間必須晚於開始時間");
			}
			java.math.BigDecimal serverAmount = java.math.BigDecimal.valueOf(hours * HOURLY_RATE);
			booking.setTotalAmount(serverAmount);
		}
		
		// 4. ★ 根據付款方式自動設定初始狀態
		if (booking.getStatus() == null && booking.getPaymentType() != null) {
			switch (booking.getPaymentType()) {
				case TRANSFER -> booking.setStatus(BookingStatus.PENDING);   // 轉帳 → 待確認
				default -> booking.setStatus(BookingStatus.CONFIRMED);       // 其餘 → 直接確認
			}
		} else if (booking.getStatus() == null) {
			booking.setStatus(BookingStatus.CONFIRMED); // 後台新增（無 paymentType）預設 CONFIRMED
		}
				
		return bookingRepo.save(booking);
	}
	
	
	// ===== 狀態更新 =====
	
	public Booking updateStatus(Integer id, BookingStatus newStatus) {
		// 1. 先從資料庫撈出這筆 Booking
		Booking booking = bookingRepo.findById(id)
				.orElseThrow(()-> new RuntimeException("找不到預約 ID:" + id));
		// 2. 修改 status 欄位
		booking.setStatus(newStatus);
		// 3. 如果取消預約，金額歸零
		if (newStatus == BookingStatus.CANCELLED) {
			booking.setTotalAmount(java.math.BigDecimal.ZERO);
		}
		// 4. 存回資料庫（Hibernate 會自動產生 UPDATE SQL）
		return bookingRepo.save(booking);
	}
	
	public List<Booking> searchByKeyword(String keyword){
		if(keyword == null || keyword.trim().isEmpty()) {
			return bookingRepo.findAll();
		}
		return bookingRepo.searchByKeyword(keyword);
	}
	
	// ===== 刪除預約（後台管理員用）=====
	public void deleteById(Integer id) {
		if (!bookingRepo.existsById(id)) {
			throw new RuntimeException("找不到預約 ID: " + id);
		}
		bookingRepo.deleteById(id);
	}
	
	public List<Booking> findByCourtAndDate(Integer courtId, LocalDate date) {
	    Court court = new Court();
	    court.setCourtId(courtId);
	    return bookingRepo.findByCourtAndBookingDateAndStatusNot(
	        court, date, BookingStatus.CANCELLED
	    );
	}

	// 查詢某會員未來已確認的預約（給前台「發起揪團」下拉選單用）
	public List<Booking> findMyUpcomingBookings(int memberId) {
		return bookingRepo.findByMember_MemberIdAndBookingDateGreaterThanEqualAndStatusOrderByBookingDateAscStartTimeAsc(
				memberId, LocalDate.now(), BookingStatus.CONFIRMED);
	}

	// 查詢某會員的所有預約紀錄（會員中心用，按日期降序）
	public List<Booking> findAllByMemberId(int memberId) {
		return bookingRepo.findByMember_MemberIdOrderByBookingDateDescStartTimeDesc(memberId);
	}

	// ===== 編輯預約（後台管理員用）=====
	
	public Booking update(Integer id, Booking updated) {
		Booking booking = bookingRepo.findById(id)
				.orElseThrow(() -> new RuntimeException("找不到預約 ID: " + id));
		
		// 更新可修改的欄位
		if (updated.getCourt() != null) {
			booking.setCourt(updated.getCourt());
		}
		if (updated.getBookingDate() != null) {
			booking.setBookingDate(updated.getBookingDate());
		}
		if (updated.getStartTime() != null) {
			booking.setStartTime(updated.getStartTime());
		}
		if (updated.getEndTime() != null) {
			booking.setEndTime(updated.getEndTime());
		}
		if (updated.getNote() != null) {
			booking.setNote(updated.getNote());
		}
		if (updated.getPaymentType() != null) {
			booking.setPaymentType(updated.getPaymentType());
		}
		if (updated.getTotalAmount() != null) {
			booking.setTotalAmount(updated.getTotalAmount());
		}
		
		// 檢查時段衝突（排除自己這筆）
		List<Booking> existingBookings = bookingRepo.findByCourtAndBookingDateAndStatusNot(
				booking.getCourt(), booking.getBookingDate(), BookingStatus.CANCELLED);
		for (Booking existing : existingBookings) {
			if (!existing.getBookingId().equals(id) && isOverlapping(booking, existing)) {
				throw new RuntimeException("修改後的時段與其他預約衝突！");
			}
		}
		
		// 伺服器端重算金額
		if (booking.getStartTime() != null && booking.getEndTime() != null) {
			long hours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
			if (hours <= 0) {
				throw new RuntimeException("結束時間必須晚於開始時間");
			}
			booking.setTotalAmount(java.math.BigDecimal.valueOf(hours * HOURLY_RATE));
		}
		
		return bookingRepo.save(booking);
	}

}
