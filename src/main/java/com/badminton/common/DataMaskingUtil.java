package com.badminton.common;

public class DataMaskingUtil {

    /**
     * 姓名脫敏 (保留前後字，中間變星號)
     * 例如：王大明 -> 王*明, 陳奕迅 -> 陳*迅, 林志玲 -> 林*玲, 王傑 -> 王*
     */
    public static String maskName(String fullName) {
        // 1. 防呆機制
        if (fullName == null || fullName.trim().isEmpty()) {
            return fullName;
        }
        
        int length = fullName.length();
        
        // 2. 依長度決定遮蔽邏輯
        if (length == 2) {
            return fullName.substring(0, 1) + "*"; // 兩個字：保留第一個字，第二個字變星號
        } else if (length > 2) {
            // 三個字以上：保留頭尾，中間全部替換為星號
            return fullName.substring(0, 1) + "*".repeat(length - 2) + fullName.substring(length - 1);
        }
        
        return fullName; // 長度為1則不變
    }

    /**
     * 電話脫敏 (支援手機與含 '-' 的市話)
     * 例如手機：0912345678 -> 0912***678
     * 例如市話：02-23456789 -> 02-***789
     */
    public static String maskPhone(String phone) {
        // 1. 防呆機制：如果是 null 或全空白，直接退回
        if (phone == null || phone.trim().isEmpty()) {
            return phone;
        }
        
        // 去除前後多餘空白
        phone = phone.trim();

        // 2. 手機邏輯：09開頭且長度剛好10碼
        if (phone.startsWith("09") && phone.length() == 10) {
            return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 3);
        } 
        // 3. 市話邏輯：包含 '-' 符號
        else if (phone.contains("-")) {
            int dashIndex = phone.indexOf("-"); 
            // 確保 '-' 後面還有足夠的號碼才處理
            if (phone.length() - dashIndex > 4) {
                return phone.substring(0, dashIndex + 1) + "***" + phone.substring(phone.length() - 3);
            }
        }

        // 4. 其他不符合規則的格式，原樣退回
        return phone;
    }
}