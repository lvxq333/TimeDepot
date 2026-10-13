package com.couple.service.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_recovery_question")
public class RecoveryQuestionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer position;
    private String question;
    private String answerHash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswerHash() { return answerHash; }
    public void setAnswerHash(String answerHash) { this.answerHash = answerHash; }
}
