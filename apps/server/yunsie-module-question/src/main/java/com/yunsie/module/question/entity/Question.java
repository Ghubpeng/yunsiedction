package com.yunsie.module.question.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yunsie.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 题目表（question_question）。
 * content_version 为业务内容版本（发布后修改 +1 并回草稿重审）；与 BaseEntity.version（乐观锁）严格分离。
 */
@Getter
@Setter
@TableName("question_question")
public class Question extends BaseEntity {

    /** 所属证书ID */
    private Long certificateId;

    /** 题型: 1-单选 2-多选 3-判断 */
    private Integer questionType;

    /** 题干 */
    private String stem;

    /** 解析(必填; 无解析不得发布) */
    private String analysis;

    /** 标准答案(标准化答案串: A|C / T / F) */
    private String answer;

    /** 难度: 1-易 2-中 3-难 */
    private Integer difficulty;

    /** 来源: 1-真题 2-模拟题 3-自编 */
    private Integer source;

    /** 状态: 1-草稿 2-待审核 3-驳回 4-已发布 5-已下架 */
    private Integer status;

    /** 题目内容版本(业务版本) */
    private Integer contentVersion;

    /** 驳回理由 */
    private String rejectReason;

    /** 审核人用户ID */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;
}
