package com.yunsie.boot.ops;

import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.certificate.dto.CertCreateReq;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.certificate.service.CertificateService;
import com.yunsie.module.course.api.CourseCommandApi;
import com.yunsie.module.question.api.QuestionCommandApi;
import com.yunsie.module.subject.api.SubjectCommandApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 内容复制编排（boot 装配点，Stage 2.3B）：
 * 复制证书体系 = 新证书 + 知识体系（版本/科目/章节/知识点）+ 课程结构（章/节/知识点关联）+ 可选题库。
 * 跨域编排只经各域 api 契约（SubjectCommandApi/CourseCommandApi/QuestionCommandApi），无反向依赖。
 * 复制产物遵循铁律：课程=草稿、题目=草稿（发布唯一通道不变）；视频不复制（不伪造媒体）。
 */
@Service
@RequiredArgsConstructor
public class ContentCopyService {

    private final CertificateQueryApi certificateQueryApi;
    private final CertificateService certificateService;
    private final SubjectCommandApi subjectCommandApi;
    private final CourseCommandApi courseCommandApi;
    private final QuestionCommandApi questionCommandApi;

    @Transactional(rollbackFor = Exception.class)
    public CopySummaryVO copy(Long sourceCertId, CopyCertificateReq req) {
        CertificateQueryApi.CertificateView source = certificateQueryApi.findCertificate(sourceCertId);
        if (source == null) {
            throw new BizException(CertErrorCode.CERT_NOT_FOUND);
        }
        Long targetCertId = certificateService.create(new CertCreateReq(
                req.categoryId() != null ? req.categoryId() : source.categoryId(),
                req.name(), req.code(), source.shortName(),
                req.description() == null ? "" : req.description(), 0, 1));

        SubjectCommandApi.CopyResult system = subjectCommandApi.copySystem(sourceCertId, targetCertId);
        int courseCount = courseCommandApi.copyCourseStructure(sourceCertId, targetCertId,
                system.newVersionId(), system.subjectIdMap(), system.nodeIdMap());
        int questionCount = 0;
        if (Boolean.TRUE.equals(req.copyQuestions())) {
            questionCount = questionCommandApi.copyPublishedQuestions(sourceCertId, targetCertId, system.nodeIdMap());
        }
        return new CopySummaryVO(targetCertId, system.subjectCount(), system.nodeCount(),
                courseCount, questionCount);
    }
}
