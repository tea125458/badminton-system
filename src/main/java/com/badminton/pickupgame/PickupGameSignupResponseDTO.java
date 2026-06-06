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
    // 建構子：傳入 Entity 與當前登入者 ID，決定是否脫敏
    public PickupGameSignupResponseDTO(PickupGameSignups signup, Integer currentUserId) {
        this.signupId = signup.getSignupId();
        this.memberId = signup.getMember().getMemberId();
        this.profilePicture = signup.getMember().getProfilePicture();
        this.status = signup.getStatus();
        this.signedUpAt = signup.getSignedUpAt();
        
        // 判斷當前使用者是不是這場球賽的主揪
        Integer hostId = signup.getGame().getHost().getMemberId();
        boolean isHost = currentUserId != null && currentUserId.equals(hostId);
        
        // 判斷當前使用者是不是自己看自己
        boolean isSelf = currentUserId != null && currentUserId.equals(this.memberId);
        
        // 如果是自己看自己：全部真實資料
        if (isSelf) {
            this.memberName = signup.getMember().getFullName();
            this.memberPhone = signup.getMember().getPhone();
        } 
        // 如果是主揪看別人：真實姓名，但是隱藏電話
        else if (isHost) {
            this.memberName = signup.getMember().getFullName();
            this.memberPhone = DataMaskingUtil.maskPhone(signup.getMember().getPhone());
        } 
        // 其他路人或球友看：隱藏姓名，隱藏電話
        else {
            this.memberName = DataMaskingUtil.maskName(signup.getMember().getFullName());
            this.memberPhone = DataMaskingUtil.maskPhone(signup.getMember().getPhone());
        }
    }

    // ====== 手動加入 Getter 與 Setter 避免 Lombok 失效導致 JSON 變成空物件 ======
    public Integer getSignupId() { return signupId; }
    public void setSignupId(Integer signupId) { this.signupId = signupId; }
    
    public Integer getMemberId() { return memberId; }
    public void setMemberId(Integer memberId) { this.memberId = memberId; }
    
    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }
    
    public String getMemberPhone() { return memberPhone; }
    public void setMemberPhone(String memberPhone) { this.memberPhone = memberPhone; }
    
    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
    
    public SignupStatus getStatus() { return status; }
    public void setStatus(SignupStatus status) { this.status = status; }
    
    public LocalDateTime getSignedUpAt() { return signedUpAt; }
    public void setSignedUpAt(LocalDateTime signedUpAt) { this.signedUpAt = signedUpAt; }
}