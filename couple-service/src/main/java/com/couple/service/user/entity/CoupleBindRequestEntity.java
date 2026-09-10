package com.couple.service.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 情侣绑定申请，只有接收方同意后才会建立伴侣关系。 */
@TableName("couple_bind_request")
public class CoupleBindRequestEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterId;
    private Long receiverId;
    private String status;
    private String activePairKey;
    private Long activeRequesterId;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRequesterId() { return requesterId; }
    public void setRequesterId(Long requesterId) { this.requesterId = requesterId; }
    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActivePairKey() { return activePairKey; }
    public void setActivePairKey(String activePairKey) { this.activePairKey = activePairKey; }
    public Long getActiveRequesterId() { return activeRequesterId; }
    public void setActiveRequesterId(Long activeRequesterId) { this.activeRequesterId = activeRequesterId; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
