package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.subject.api.SubjectQueryApi;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.exam.dto.ExamCreateReq;
import com.yunsie.module.exam.dto.ExamUpdateReq;
import com.yunsie.module.exam.entity.Exam;
import com.yunsie.module.exam.entity.ExamAttempt;
import com.yunsie.module.exam.entity.ExamPaper;
import com.yunsie.module.exam.entity.ExamPaperOption;
import com.yunsie.module.exam.entity.ExamPaperQuestion;
import com.yunsie.module.exam.enums.ExamStatus;
import com.yunsie.module.exam.enums.PaperStatus;
import com.yunsie.module.exam.error.ExamErrorCode;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.mapper.ExamMapper;
import com.yunsie.module.exam.mapper.ExamPaperMapper;
import com.yunsie.module.exam.mapper.ExamPaperOptionMapper;
import com.yunsie.module.exam.mapper.ExamPaperQuestionMapper;
import com.yunsie.module.exam.service.ExamService;
import com.yunsie.module.exam.service.PaperAssembler;
import com.yunsie.module.exam.vo.ExamVO;
import com.yunsie.module.exam.vo.PaperQuestionVO;
import com.yunsie.module.exam.vo.PaperSummaryVO;
import com.yunsie.module.exam.vo.SubmitResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 考试管理实现。
 * 状态机：草稿→已发布→已下架；已下架→草稿。已发布禁止编辑/组卷/删除。
 * 规则配置化（assemble_rule JSON）；发布前必须存在有效试卷。
 */
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamMapper examMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamPaperOptionMapper paperOptionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final PaperAssembler paperAssembler;
    private final CertificateQueryApi certificateQueryApi;
    private final SubjectQueryApi subjectQueryApi;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ExamCreateReq req) {
        requireCertificate(req.certificateId());
        Exam exam = new Exam();
        exam.setCertificateId(req.certificateId());
        exam.setSubjectId(resolveSubject(req.certificateId(), req.subjectId()));
        exam.setVersionId(resolveVersion(req.certificateId(), req.versionId()));
        exam.setName(req.name());
        exam.setDurationMinutes(req.durationMinutes());
        exam.setPassScore(req.passScore());
        exam.setValidFrom(req.validFrom());
        exam.setValidUntil(req.validUntil());
        exam.setStatus(ExamStatus.DRAFT.code());
        exam.setTotalScore(java.math.BigDecimal.ZERO);
        exam.setAssembleRule(toRuleJson(req.rule()));
        examMapper.insert(exam);
        return exam.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ExamUpdateReq req) {
        Exam exam = requireExam(id);
        requireEditable(exam);
        exam.setName(req.name());
        exam.setDurationMinutes(req.durationMinutes());
        exam.setPassScore(req.passScore());
        exam.setValidFrom(req.validFrom());
        exam.setValidUntil(req.validUntil());
        exam.setAssembleRule(toRuleJson(req.rule()));
        examMapper.updateById(exam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Exam exam = requireExam(id);
        ExamStatus status = ExamStatus.of(exam.getStatus());
        if (status == ExamStatus.PUBLISHED) {
            throw new BizException(ExamErrorCode.EXAM_PUBLISHED_NOT_DELETABLE);
        }
        Long attempts = attemptMapper.selectCount(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getExamId, id));
        if (attempts != null && attempts > 0) {
            throw new BizException(ExamErrorCode.EXAM_HAS_ATTEMPTS);
        }
        examMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long assemble(Long id) {
        Exam exam = requireExam(id);
        ExamStatus status = ExamStatus.of(exam.getStatus());
        if (status == ExamStatus.PUBLISHED) {
            throw new BizException(ExamErrorCode.EXAM_PUBLISHED_NOT_ASSEMBLE);
        }
        ExamCreateReq.AssembleRule rule = parseRule(exam.getAssembleRule());
        Long paperId = paperAssembler.assemble(exam, rule);
        exam.setTotalScore(java.math.BigDecimal.valueOf(rule.questionCount()));
        examMapper.updateById(exam);
        return paperId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Exam exam = requireExam(id);
        ExamStatus status = ExamStatus.of(exam.getStatus());
        if (status != ExamStatus.DRAFT && status != ExamStatus.OFFLINE) {
            throw new BizException(ExamErrorCode.EXAM_STATUS_INVALID_ACTION);
        }
        requireValidPaper(id);
        exam.setStatus(ExamStatus.PUBLISHED.code());
        examMapper.updateById(exam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id) {
        Exam exam = requireExam(id);
        if (ExamStatus.of(exam.getStatus()) != ExamStatus.PUBLISHED) {
            throw new BizException(ExamErrorCode.EXAM_STATUS_INVALID_ACTION);
        }
        exam.setStatus(ExamStatus.OFFLINE.code());
        examMapper.updateById(exam);
    }

    @Override
    public ExamVO detail(Long id) {
        Exam exam = requireExam(id);
        return toVO(exam);
    }

    @Override
    public PageResult<ExamVO> page(int pageNum, int pageSize, Long certificateId, Integer status, String keyword) {
        LambdaQueryWrapper<Exam> wrapper = new LambdaQueryWrapper<>();
        if (certificateId != null) {
            wrapper.eq(Exam::getCertificateId, certificateId);
        }
        if (status != null) {
            wrapper.eq(Exam::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Exam::getName, keyword);
        }
        wrapper.orderByDesc(Exam::getId);
        Page<Exam> page = examMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(), page.getTotal());
    }

    @Override
    public PaperSummaryVO paperSummary(Long examId) {
        Exam exam = requireExam(examId);
        ExamPaper paper = validPaper(examId);
        if (paper == null) {
            return null;
        }
        return new PaperSummaryVO(paper.getId(), paper.getTitle(), paper.getDurationMinutes(),
                paper.getTotalScore(), paper.getQuestionCount(), paper.getStatus(),
                paper.getAssembleSeed(), paper.getCreateTime());
    }

    @Override
    public List<PaperQuestionVO> paperQuestions(Long examId) {
        requireExam(examId);
        ExamPaper paper = validPaper(examId);
        if (paper == null) {
            throw new BizException(ExamErrorCode.PAPER_NOT_FOUND);
        }
        List<ExamPaperQuestion> questions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, paper.getId())
                        .orderByAsc(ExamPaperQuestion::getSort));
        List<Long> pqIds = questions.stream().map(ExamPaperQuestion::getId).toList();
        var optionsByPq = pqIds.isEmpty() ? java.util.Map.<Long, List<ExamPaperOption>>of()
                : paperOptionMapper.selectList(new LambdaQueryWrapper<ExamPaperOption>()
                        .in(ExamPaperOption::getPaperQuestionId, pqIds)
                        .orderByAsc(ExamPaperOption::getSort).orderByAsc(ExamPaperOption::getOptionKey))
                        .stream().collect(java.util.stream.Collectors.groupingBy(ExamPaperOption::getPaperQuestionId));
        return questions.stream()
                .map(q -> new PaperQuestionVO(q.getId(), q.getQuestionId(), q.getSort(), q.getScore(),
                        q.getQuestionType(), q.getStem(), q.getAnalysis(), q.getStandardAnswer(),
                        q.getDifficulty(), q.getSource(), q.getContentVersion(), q.getNodeIds(),
                        optionsByPq.getOrDefault(q.getId(), List.of()).stream()
                                .map(o -> new PaperQuestionVO.OptionVO(o.getOptionKey(), o.getContent()))
                                .toList()))
                .toList();
    }

    @Override
    public PageResult<SubmitResultVO> resultsPage(Long examId, int pageNum, int pageSize) {
        requireExam(examId);
        Page<ExamAttempt> page = attemptMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getExamId, examId)
                        .eq(ExamAttempt::getStatus, 3)
                        .orderByDesc(ExamAttempt::getId));
        List<SubmitResultVO> list = page.getRecords().stream()
                .map(a -> new SubmitResultVO(a.getId(), a.getStatus(), a.getScore(), a.getCorrectCount(),
                        a.getQuestionCount(), null, null, a.getSubmittedAt()))
                .toList();
        return PageResult.of(list, page.getTotal());
    }

    // ---------- helpers ----------

    private void requireEditable(Exam exam) {
        if (ExamStatus.of(exam.getStatus()) == ExamStatus.PUBLISHED) {
            throw new BizException(ExamErrorCode.EXAM_PUBLISHED_NOT_EDITABLE);
        }
    }

    private Exam requireExam(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) {
            throw new BizException(ExamErrorCode.EXAM_NOT_FOUND);
        }
        return exam;
    }

    private void requireCertificate(Long certificateId) {
        if (certificateQueryApi.findCertificate(certificateId) == null) {
            throw new BizException(CertErrorCode.CERT_NOT_FOUND);
        }
    }

    /** 校验科目存在、启用且属于该证书；非法抛 subject 域错误码（优先复用） */
    private Long resolveSubject(Long certificateId, Long subjectId) {
        if (subjectId == null) {
            return null;
        }
        SubjectQueryApi.SubjectView subject = subjectQueryApi.findSubject(subjectId);
        if (subject == null || subject.enabled() == null || subject.enabled() != 1
                || !certificateId.equals(subject.certificateId())) {
            throw new BizException(SubjectErrorCode.SUBJECT_NOT_FOUND);
        }
        return subjectId;
    }

    /** versionId 空且证书非空 → 回填证书当前版本（无当前版本返回 null，保持可空语义） */
    private Long resolveVersion(Long certificateId, Long versionId) {
        if (versionId != null) {
            return versionId;
        }
        if (certificateId == null) {
            return null;
        }
        return subjectQueryApi.findCurrentVersionId(certificateId);
    }

    private ExamPaper validPaper(Long examId) {
        return paperMapper.selectOne(new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getExamId, examId)
                .eq(ExamPaper::getStatus, PaperStatus.VALID.code())
                .last("LIMIT 1"));
    }

    private void requireValidPaper(Long examId) {
        if (validPaper(examId) == null) {
            throw new BizException(ExamErrorCode.PAPER_NOT_FOUND);
        }
    }

    private String toRuleJson(ExamCreateReq.AssembleRule rule) {
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            throw new BizException(ExamErrorCode.ASSEMBLE_RULE_INVALID);
        }
    }

    private ExamCreateReq.AssembleRule parseRule(String json) {
        if (!StringUtils.hasText(json)) {
            throw new BizException(ExamErrorCode.ASSEMBLE_RULE_INVALID);
        }
        try {
            return objectMapper.readValue(json, ExamCreateReq.AssembleRule.class);
        } catch (JsonProcessingException e) {
            throw new BizException(ExamErrorCode.ASSEMBLE_RULE_INVALID);
        }
    }

    private ExamVO toVO(Exam exam) {
        ExamCreateReq.AssembleRule rule = null;
        try {
            rule = parseRule(exam.getAssembleRule());
        } catch (BizException ignored) {
            // 规则缺失/非法时返回空规则（历史脏数据兜底展示）
        }
        ExamVO.AssembleRuleVO ruleVO = rule == null ? null
                : new ExamVO.AssembleRuleVO(rule.questionCount(), rule.questionTypes(), rule.nodeIds(), rule.difficulty());
        return new ExamVO(exam.getId(), exam.getCertificateId(), exam.getSubjectId(), exam.getVersionId(),
                exam.getName(), exam.getDurationMinutes(),
                exam.getTotalScore(), exam.getPassScore(), exam.getValidFrom(), exam.getValidUntil(),
                exam.getStatus(), ruleVO);
    }
}
