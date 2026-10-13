package com.couple.service.letter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一次持续沟通的事件状态；信件只负责投递状态，线程负责事件生命周期。 */
@TableName("letter_thread")
public class LetterThreadEntity {
    @TableId(type = IdType.INPUT)
    private String threadId;
    private String title;
    private Long participantAId;
    private Long participantBId;
    private String status;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime reopenedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getParticipantAId() { return participantAId; }
    public void setParticipantAId(Long participantAId) { this.participantAId = participantAId; }
    public Long getParticipantBId() { return participantBId; }
    public void setParticipantBId(Long participantBId) { this.participantBId = participantBId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Long resolvedBy) { this.resolvedBy = resolvedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public LocalDateTime getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(LocalDateTime reopenedAt) { this.reopenedAt = reopenedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
