package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateDeletingEvent;
import com.yunsie.module.subject.entity.ExamSubject;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 证书删除完整性监听（subject 域）：
 * 证书删除前检查其下是否存在考试科目，存在则抛业务异常否决删除（同一事务内同步执行并回滚）。
 * 使用同步 {@link EventListener}（而非 BEFORE_COMMIT）保证测试事务内语义一致、生产事务内立即否决。
 * 依赖方向：subject → certificate（事件类位于 certificate.api），无环（docs/ARCHITECTURE.md D 表）。
 */
@Component
@RequiredArgsConstructor
public class CertificateIntegrityListener {

    private final ExamSubjectMapper subjectMapper;

    @EventListener
    public void vetoDelete(CertificateDeletingEvent event) {
        Long count = subjectMapper.selectCount(new LambdaQueryWrapper<ExamSubject>()
                .eq(ExamSubject::getCertificateId, event.certificateId()));
        if (count != null && count > 0) {
            throw new BizException(SubjectErrorCode.SUBJECT_EXISTS_BLOCK_CERT_DELETE);
        }
    }
}
