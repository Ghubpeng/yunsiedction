package com.yunsie.module.learning.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习事件幂等去重表（learn_event_dedup）：append-only，不参与逻辑删除（无 deleted/version 列）。
 * uk(dedup_key) 保证同一事件只处理一次。
 */
@Getter
@Setter
@TableName("learn_event_dedup")
public class LearnEventDedup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件去重键(如 exam:{attemptId}) */
    private String dedupKey;

    /** 事件类型: EXAM_FINISHED */
    private String eventType;

    /** 处理时间 */
    private LocalDateTime processedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
