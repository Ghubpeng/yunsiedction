package com.yunsie.module.subject.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 考试科目 VO。
 */
public record SubjectVO(
        Long id,
        Long certificateId,
        String name,
        String code,
        Integer sort,
        Integer enabled,
        Integer source,
        String description,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8") LocalDateTime createTime) {
}
