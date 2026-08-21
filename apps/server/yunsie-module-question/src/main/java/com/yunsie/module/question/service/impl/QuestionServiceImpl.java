package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.certificate.error.CertErrorCode;
import com.yunsie.module.question.dto.QuestionCreateReq;
import com.yunsie.module.question.dto.QuestionUpdateReq;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.entity.QuestionKnowledgeNode;
import com.yunsie.module.question.entity.QuestionOption;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.question.enums.QuestionType;
import com.yunsie.module.question.error.QuestionErrorCode;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionOptionMapper;
import com.yunsie.module.question.service.GradingService;
import com.yunsie.module.question.service.QuestionService;
import com.yunsie.module.question.vo.OptionVO;
import com.yunsie.module.question.vo.QuestionDetailVO;
import com.yunsie.module.question.vo.QuestionVO;
import com.yunsie.module.subject.api.SubjectQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 题目管理实现。
 * 状态机：草稿→待审核→已发布→已下架；待审核→驳回→草稿。发布=approve（唯一通道）。
 * 知识点校验经 subject 域契约 SubjectQueryApi（不直连 subject 表）。
 */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final QuestionKnowledgeNodeMapper knowledgeNodeMapper;
    private final GradingService gradingService;
    private final SubjectQueryApi subjectQueryApi;
    private final CertificateQueryApi certificateQueryApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(QuestionCreateReq req) {
        requireCertificate(req.certificateId());
        QuestionType type = QuestionType.of(req.questionType());
        if (type == null) {
            throw new BizException(QuestionErrorCode.QUESTION_TYPE_INVALID);
        }
        Set<String> optionKeys = validateAndCollectOptions(type, req.options(), req.answer());
        validateNodes(req.certificateId(), req.nodeIds());
        checkDuplicateStem(req.certificateId(), req.stem(), null);

        Question question = new Question();
        question.setCertificateId(req.certificateId());
        question.setQuestionType(type.code());
        question.setStem(req.stem());
        question.setAnalysis(req.analysis());
        question.setAnswer(gradingService.normalizeStandardAnswer(type.code(), req.answer(), optionKeys));
        question.setDifficulty(req.difficulty() == null ? 2 : req.difficulty());
        question.setSource(req.source() == null ? 2 : req.source());
        question.setStatus(QuestionStatus.DRAFT.code());
        question.setContentVersion(1);
        question.setRejectReason("");
        questionMapper.insert(question);

        insertOptions(question.getId(), req.options());
        insertNodeLinks(question.getId(), req.nodeIds());
        return question.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, QuestionUpdateReq req) {
        Question question = requireQuestion(id);
        prepareEdit(question);
        QuestionType type = QuestionType.of(question.getQuestionType());
        Set<String> optionKeys = validateAndCollectOptions(type, req.options(), req.answer());
        checkDuplicateStem(question.getCertificateId(), req.stem(), id);

        question.setStem(req.stem());
        question.setAnalysis(req.analysis());
        question.setAnswer(gradingService.normalizeStandardAnswer(type.code(), req.answer(), optionKeys));
        question.setDifficulty(req.difficulty());
        question.setSource(req.source());
        questionMapper.updateById(question);

        // 选项整体替换
        optionMapper.delete(new LambdaQueryWrapper<QuestionOption>().eq(QuestionOption::getQuestionId, id));
        insertOptions(id, req.options());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledgeNodes(Long id, List<Long> nodeIds) {
        Question question = requireQuestion(id);
        prepareEdit(question);
        validateNodes(question.getCertificateId(), nodeIds);
        knowledgeNodeMapper.delete(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                .eq(QuestionKnowledgeNode::getQuestionId, id));
        insertNodeLinks(id, nodeIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Question question = requireQuestion(id);
        QuestionStatus status = QuestionStatus.of(question.getStatus());
        // Stage 2.3B：已发布 → 回收站（历史考试快照独立存于 exam 域，不受影响）；
        // 审核中 → 先撤回（30318）；其余状态走逻辑删除（2.3A 语义不变）。
        if (status == QuestionStatus.PUBLISHED) {
            question.setStatus(QuestionStatus.RECYCLED.code());
            questionMapper.updateById(question);
            return;
        }
        if (status == QuestionStatus.PENDING_REVIEW) {
            throw new BizException(QuestionErrorCode.QUESTION_DELETE_PENDING_REVIEW);
        }
        optionMapper.delete(new LambdaQueryWrapper<QuestionOption>().eq(QuestionOption::getQuestionId, id));
        knowledgeNodeMapper.delete(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                .eq(QuestionKnowledgeNode::getQuestionId, id));
        questionMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id) {
        Question question = requireQuestion(id);
        if (QuestionStatus.of(question.getStatus()) != QuestionStatus.RECYCLED) {
            throw new BizException(QuestionErrorCode.QUESTION_NOT_RECYCLED);
        }
        // 恢复 → 草稿：发布唯一通道仍是审核通过（question-bank 铁律）
        question.setStatus(QuestionStatus.DRAFT.code());
        questionMapper.updateById(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long id) {
        Question question = requireQuestion(id);
        QuestionStatus status = QuestionStatus.of(question.getStatus());
        if (status != QuestionStatus.DRAFT && status != QuestionStatus.REJECTED && status != QuestionStatus.OFFLINE) {
            throw new BizException(QuestionErrorCode.QUESTION_STATUS_INVALID_ACTION);
        }
        question.setStatus(QuestionStatus.PENDING_REVIEW.code());
        question.setRejectReason("");
        questionMapper.updateById(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawReview(Long id) {
        Question question = requireQuestion(id);
        if (QuestionStatus.of(question.getStatus()) != QuestionStatus.PENDING_REVIEW) {
            throw new BizException(QuestionErrorCode.QUESTION_STATUS_INVALID_ACTION);
        }
        question.setStatus(QuestionStatus.DRAFT.code());
        questionMapper.updateById(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long auditorId) {
        Question question = requireQuestion(id);
        if (QuestionStatus.of(question.getStatus()) != QuestionStatus.PENDING_REVIEW) {
            throw new BizException(QuestionErrorCode.QUESTION_STATUS_INVALID_ACTION);
        }
        question.setStatus(QuestionStatus.PUBLISHED.code());
        question.setAuditBy(auditorId);
        question.setAuditTime(LocalDateTime.now());
        questionMapper.updateById(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason, Long auditorId) {
        Question question = requireQuestion(id);
        if (QuestionStatus.of(question.getStatus()) != QuestionStatus.PENDING_REVIEW) {
            throw new BizException(QuestionErrorCode.QUESTION_STATUS_INVALID_ACTION);
        }
        question.setStatus(QuestionStatus.REJECTED.code());
        question.setRejectReason(reason);
        question.setAuditBy(auditorId);
        question.setAuditTime(LocalDateTime.now());
        questionMapper.updateById(question);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id) {
        Question question = requireQuestion(id);
        if (QuestionStatus.of(question.getStatus()) != QuestionStatus.PUBLISHED) {
            throw new BizException(QuestionErrorCode.QUESTION_STATUS_INVALID_ACTION);
        }
        question.setStatus(QuestionStatus.OFFLINE.code());
        questionMapper.updateById(question);
    }

    @Override
    public QuestionDetailVO detail(Long id) {
        Question question = requireQuestion(id);
        List<OptionVO> options = optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, id)
                        .orderByAsc(QuestionOption::getSort).orderByAsc(QuestionOption::getOptionKey))
                .stream().map(o -> new OptionVO(o.getOptionKey(), o.getContent())).toList();
        List<Long> nodeIds = knowledgeNodeMapper.selectList(new LambdaQueryWrapper<QuestionKnowledgeNode>()
                        .eq(QuestionKnowledgeNode::getQuestionId, id))
                .stream().map(QuestionKnowledgeNode::getNodeId).toList();
        return new QuestionDetailVO(toVO(question), question.getAnalysis(), question.getAnswer(), options, nodeIds);
    }

    @Override
    public PageResult<QuestionVO> page(int pageNum, int pageSize, Long certificateId, Integer status,
                                       Integer questionType, Integer difficulty, String keyword) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (certificateId != null) {
            wrapper.eq(Question::getCertificateId, certificateId);
        }
        if (status != null) {
            wrapper.eq(Question::getStatus, status);
        } else {
            // Stage 2.3B：默认列表不含回收站（显式按 status=6 筛选可见）
            wrapper.ne(Question::getStatus, QuestionStatus.RECYCLED.code());
        }
        if (questionType != null) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Question::getStem, keyword);
        }
        wrapper.orderByDesc(Question::getId);
        Page<Question> page = questionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<QuestionVO> list = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(list, page.getTotal());
    }

    /** 编辑前处理：待审核不可编辑；已发布 → 内容版本+1 回草稿（重审） */
    private void prepareEdit(Question question) {
        QuestionStatus status = QuestionStatus.of(question.getStatus());
        if (status == QuestionStatus.PENDING_REVIEW) {
            throw new BizException(QuestionErrorCode.QUESTION_STATUS_NOT_EDITABLE);
        }
        if (status == QuestionStatus.PUBLISHED) {
            question.setContentVersion(question.getContentVersion() == null ? 2 : question.getContentVersion() + 1);
            question.setStatus(QuestionStatus.DRAFT.code());
            question.setRejectReason("");
        }
    }

    private void checkDuplicateStem(Long certificateId, String stem, Long excludeId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .eq(Question::getCertificateId, certificateId)
                .eq(Question::getStem, stem);
        if (excludeId != null) {
            wrapper.ne(Question::getId, excludeId);
        }
        if (questionMapper.selectCount(wrapper) > 0) {
            throw new BizException(QuestionErrorCode.DUPLICATE_QUESTION);
        }
    }

    private void requireCertificate(Long certificateId) {
        if (certificateQueryApi.findCertificate(certificateId) == null) {
            throw new BizException(CertErrorCode.CERT_NOT_FOUND);
        }
    }

    /** 校验知识点关联（F 模型）：存在/启用/知识点或子知识点/属于该证书，经 SubjectQueryApi 契约 */
    private void validateNodes(Long certificateId, List<Long> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            throw new BizException(QuestionErrorCode.NODE_IDS_REQUIRED);
        }
        for (Long nodeId : new LinkedHashSet<>(nodeIds)) {
            SubjectQueryApi.KnowledgeNodeView node = subjectQueryApi.findNode(nodeId);
            if (node == null || node.enabled() == null || node.enabled() != 1
                    || (node.nodeType() != 2 && node.nodeType() != 3)
                    || !certificateId.equals(node.certificateId())) {
                throw new BizException(QuestionErrorCode.NODE_ASSOC_INVALID);
            }
        }
    }

    /** 选项与答案一致性校验（D/E 模型），返回选项键集合 */
    private Set<String> validateAndCollectOptions(QuestionType type, List<QuestionCreateReq.OptionItem> options,
                                                  String rawAnswer) {
        Set<String> keys = new LinkedHashSet<>();
        if (type == QuestionType.TRUE_FALSE) {
            if (options != null && !options.isEmpty()) {
                throw new BizException(QuestionErrorCode.OPTION_INVALID);
            }
        } else {
            if (options == null || options.size() < 2) {
                throw new BizException(QuestionErrorCode.OPTION_INVALID);
            }
            for (QuestionCreateReq.OptionItem item : options) {
                String key = item.optionKey() == null ? "" : item.optionKey().trim().toUpperCase();
                if (key.length() != 1 || key.charAt(0) < 'A' || key.charAt(0) > 'Z' || !keys.add(key)) {
                    throw new BizException(QuestionErrorCode.OPTION_INVALID);
                }
            }
        }
        // 答案标准化 + 选项范围校验（GradingService 集中判分/校验策略）
        gradingService.normalizeStandardAnswer(type.code(), rawAnswer, keys);
        return keys;
    }

    private void insertOptions(Long questionId, List<QuestionCreateReq.OptionItem> options) {
        if (options == null) {
            return;
        }
        int sort = 0;
        for (QuestionCreateReq.OptionItem item : options) {
            QuestionOption option = new QuestionOption();
            option.setQuestionId(questionId);
            option.setOptionKey(item.optionKey().trim().toUpperCase());
            option.setContent(item.content());
            option.setSort(sort++);
            optionMapper.insert(option);
        }
    }

    private void insertNodeLinks(Long questionId, List<Long> nodeIds) {
        for (Long nodeId : new LinkedHashSet<>(nodeIds)) {
            QuestionKnowledgeNode link = new QuestionKnowledgeNode();
            link.setQuestionId(questionId);
            link.setNodeId(nodeId);
            knowledgeNodeMapper.insert(link);
        }
    }

    private Question requireQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BizException(QuestionErrorCode.QUESTION_NOT_FOUND);
        }
        return question;
    }

    private QuestionVO toVO(Question q) {
        return new QuestionVO(q.getId(), q.getCertificateId(), q.getQuestionType(), q.getStem(),
                q.getDifficulty(), q.getSource(), q.getStatus(), q.getContentVersion(),
                q.getRejectReason(), q.getAuditBy());
    }
}
