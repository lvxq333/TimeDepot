package com.couple.service.annotation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 情绪信原文批注，引用区间使用 Java/JavaScript 一致的 UTF-16 前闭后开偏移。 */
@TableName("annotation")
public class AnnotationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long letterId;
    private Long authorId;
    private String quoteText;
    private Integer quoteStart;
    private Integer quoteEnd;
    private String content;
    private String templateType;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLetterId() { return letterId; }
    public void setLetterId(Long letterId) { this.letterId = letterId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public String getQuoteText() { return quoteText; }
    public void setQuoteText(String quoteText) { this.quoteText = quoteText; }
    public Integer getQuoteStart() { return quoteStart; }
    public void setQuoteStart(Integer quoteStart) { this.quoteStart = quoteStart; }
    public Integer getQuoteEnd() { return quoteEnd; }
    public void setQuoteEnd(Integer quoteEnd) { this.quoteEnd = quoteEnd; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
