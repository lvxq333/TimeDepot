package com.couple.service.memo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一个情绪信话题对应的共识备忘录。 */
@TableName("consensus_memo")
public class ConsensusMemoEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String threadId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rootCause;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String commonGround;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String secretCode;
    private String status;
    private Long participantAId;
    private Long participantBId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long submittedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime participantAConfirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime participantBConfirmedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long resolutionRequestedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long currentRevisionId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long pendingRevisionId;
    private Integer version;
    private Long createdBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getCommonGround() { return commonGround; }
    public void setCommonGround(String commonGround) { this.commonGround = commonGround; }
    public String getSecretCode() { return secretCode; }
    public void setSecretCode(String secretCode) { this.secretCode = secretCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getParticipantAId() { return participantAId; }
    public void setParticipantAId(Long participantAId) { this.participantAId = participantAId; }
    public Long getParticipantBId() { return participantBId; }
    public void setParticipantBId(Long participantBId) { this.participantBId = participantBId; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getParticipantAConfirmedAt() { return participantAConfirmedAt; }
    public void setParticipantAConfirmedAt(LocalDateTime value) { this.participantAConfirmedAt = value; }
    public LocalDateTime getParticipantBConfirmedAt() { return participantBConfirmedAt; }
    public void setParticipantBConfirmedAt(LocalDateTime value) { this.participantBConfirmedAt = value; }
    public Long getResolutionRequestedBy() { return resolutionRequestedBy; }
    public void setResolutionRequestedBy(Long value) { this.resolutionRequestedBy = value; }
    public Long getCurrentRevisionId() { return currentRevisionId; }
    public void setCurrentRevisionId(Long value) { this.currentRevisionId = value; }
    public Long getPendingRevisionId() { return pendingRevisionId; }
    public void setPendingRevisionId(Long value) { this.pendingRevisionId = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
