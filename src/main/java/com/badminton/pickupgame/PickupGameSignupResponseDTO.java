package com.badminton.pickupgame;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import com.badminton.common.DataMaskingUtil; 
@Data
public class PickupGameSignupResponseDTO {
    
    private Integer signupId;
    private Integer memberId;
    private String memberName;     // 存放脫敏後的姓名
    private String memberPhone;    // 存放脫敏後的電話
    private String profilePicture; 
    private SignupStatus status;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signedUpAt;
    // 建構子：傳入 Entity，轉換出 DTO，並在這裡執行脫敏
    public PickupGameSignupResponseDTO(PickupGameSignups signup) {
        this.signupId = signup.getSignupId();
        this.memberId = signup.getMember().getMemberId();
        this.profilePicture = signup.getMember().getProfilePicture();
        this.status = signup.getStatus();
        this.signedUpAt = signup.getSignedUpAt();
        
        // 【核心邏輯】在這裡呼叫 Utils 將機敏資料替換為星號
        this.memberName = DataMaskingUtil.maskName(signup.getMember().getFullName());
        this.memberPhone = DataMaskingUtil.maskPhone(signup.getMember().getPhone());
    }
}