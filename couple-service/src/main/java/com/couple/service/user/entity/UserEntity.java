package com.couple.service.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 用户表实体，包含账户、伴侣关系和会员基础信息。 */
@TableName("`user`")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String coupleCode;
    private Long partnerId;
    private String partnerRemark;
    private String relationshipStatus;
    private String membershipPlan;
    private LocalDateTime membershipExpireAt;
    private LocalDateTime boundAt;
    private LocalDateTime unboundAt;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getCoupleCode() {
        return coupleCode;
    }

    public void setCoupleCode(String coupleCode) {
        this.coupleCode = coupleCode;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public String getPartnerRemark() {
        return partnerRemark;
    }

    public void setPartnerRemark(String partnerRemark) {
        this.partnerRemark = partnerRemark;
    }

    public String getRelationshipStatus() {
        return relationshipStatus;
    }

    public void setRelationshipStatus(String relationshipStatus) {
        this.relationshipStatus = relationshipStatus;
    }

    public String getMembershipPlan() {
        return membershipPlan;
    }

    public void setMembershipPlan(String membershipPlan) {
        this.membershipPlan = membershipPlan;
    }

    public LocalDateTime getMembershipExpireAt() {
        return membershipExpireAt;
    }

    public void setMembershipExpireAt(LocalDateTime membershipExpireAt) {
        this.membershipExpireAt = membershipExpireAt;
    }

    public LocalDateTime getBoundAt() {
        return boundAt;
    }

    public void setBoundAt(LocalDateTime boundAt) {
        this.boundAt = boundAt;
    }

    public LocalDateTime getUnboundAt() {
        return unboundAt;
    }

    public void setUnboundAt(LocalDateTime unboundAt) {
        this.unboundAt = unboundAt;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
