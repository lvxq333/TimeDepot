package com.couple.service.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 具有明确承诺人且只能通过新版本修改或取消的承诺。 */
@TableName("memo_commitment")
public class MemoCommitmentEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memoId;
    private Long ownerId;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long currentRevisionId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long pendingRevisionId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMemoId() { return memoId; }
    public void setMemoId(Long memoId) { this.memoId = memoId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCurrentRevisionId() { return currentRevisionId; }
    public void setCurrentRevisionId(Long value) { this.currentRevisionId = value; }
    public Long getPendingRevisionId() { return pendingRevisionId; }
    public void setPendingRevisionId(Long value) { this.pendingRevisionId = value; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
