package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.exam.api.ExamFinishedEvent;
import com.yunsie.module.exam.dto.SaveAnswersReq;
import com.yunsie.module.exam.entity.Exam;
import com.yunsie.module.exam.entity.ExamAnswer;
import com.yunsie.module.exam.entity.ExamAttempt;
import com.yunsie.module.exam.entity.ExamPaper;
import com.yunsie.module.exam.entity.ExamPaperOption;
import com.yunsie.module.exam.entity.ExamPaperQuestion;
import com.yunsie.module.exam.enums.AttemptStatus;
import com.yunsie.module.exam.enums.ExamStatus;
import com.yunsie.module.exam.enums.PaperStatus;
import com.yunsie.module.exam.error.ExamErrorCode;
import com.yunsie.module.exam.mapper.ExamAnswerMapper;
import com.yunsie.module.exam.mapper.ExamAttemptMapper;
import com.yunsie.module.exam.mapper.ExamMapper;
import com.yunsie.module.exam.mapper.ExamPaperMapper;
import com.yunsie.module.exam.mapper.ExamPaperOptionMapper;
import com.yunsie.module.exam.mapper.ExamPaperQuestionMapper;
import com.yunsie.module.exam.service.AttemptService;
import com.yunsie.module.exam.vo.AttemptQuestionVO;
import com.yunsie.module.exam.vo.AttemptResultVO;
import com.yunsie.module.exam.vo.AttemptStartVO;
import com.yunsie.module.exam.vo.AvailableExamVO;
import com.yunsie.module.exam.vo.SubmitResultVO;
import com.yunsie.module.question.api.QuestionQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考试实例实现。
 * 核心保证（exam-engine）：
 *   - 计时唯一依据=服务端 expired_at；客户端时间不参与判定
 *   - 交卷幂等：条件更新 WHERE status=2，行数=0 → 返回既有成绩，不重复计分
 *   - 判分只经 QuestionQueryApi.gradeSubmission（不复制题型判分）
 *   - attempt 严格本人数据（ATTEMPT_FORBIDDEN）
 */
@Service
@RequiredArgsConstructor
public class AttemptServiceImpl implements AttemptService {

    private static final BigDecimal ONE_POINT = new BigDecimal("1.00");
    private static final BigDecimal ZERO_POINT = new BigDecimal("0.00");

    private final ExamMapper examMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamPaperOptionMapper paperOptionMapper;
    private final ExamAttemptMapper attemptMapper;
    private final ExamAnswerMapper answerMapper;
    private final QuestionQueryApi questionQueryApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttemptStartVO start(Long userId, Long examId) {
        Exam exam = requireAvailableExam(examId);
        ExamPaper paper = requireValidPaper(examId);

        // 幂等：存在进行中 attempt → 直接返回（不创建第二条）
        ExamAttempt existing = attemptMapper.selectOne(new LambdaQueryWrapper<ExamAttempt>()
                .eq(ExamAttempt::getExamId, examId)
                .eq(ExamAttempt::getUserId, userId)
                .eq(ExamAttempt::getStatus, AttemptStatus.IN_PROGRESS.code())
                .last("LIMIT 1"));
        if (existing != null) {
            return buildStartVO(existing, exam);
        }

        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setExamId(examId);
        attempt.setPaperId(paper.getId());
        attempt.setUserId(userId);
        attempt.setStatus(AttemptStatus.IN_PROGRESS.code());
        attempt.setStartedAt(now);
        attempt.setExpiredAt(now.plusMinutes(paper.getDurationMinutes()));
        attempt.setQuestionCount(paper.getQuestionCount());
        attemptMapper.insert(attempt);
        return buildStartVO(attempt, exam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttemptStartVO getAttempt(Long userId, Long attemptId) {
        ExamAttempt attempt = requireOwnAttempt(userId, attemptId);
        if (isExpired(attempt)) {
            autoSubmit(attempt.getId());
            attempt = requireOwnAttempt(userId, attemptId);
        }
        Exam exam = examMapper.selectById(attempt.getExamId());
        return buildStartVO(attempt, exam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAnswers(Long userId, Long attemptId, SaveAnswersReq req) {
        ExamAttempt attempt = requireOwnAttempt(userId, attemptId);
        if (AttemptStatus.of(attempt.getStatus()) != AttemptStatus.IN_PROGRESS) {
            if (AttemptStatus.of(attempt.getStatus()) == AttemptStatus.SUBMITTED) {
                throw new BizException(ExamErrorCode.EXAM_ALREADY_SUBMITTED);
            }
            throw new BizException(ExamErrorCode.ATTEMPT_STATUS_INVALID);
        }
        if (isExpired(attempt)) {
            autoSubmit(attempt.getId());
            throw new BizException(ExamErrorCode.EXAM_EXPIRED);
        }
        List<ExamPaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>().eq(ExamPaperQuestion::getPaperId, attempt.getPaperId()));
        Map<Long, ExamPaperQuestion> byId = paperQuestions.stream()
                .collect(Collectors.toMap(ExamPaperQuestion::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        for (SaveAnswersReq.AnswerItem item : req.answers()) {
            ExamPaperQuestion pq = byId.get(item.paperQuestionId());
            if (pq == null) {
                throw new BizException(ExamErrorCode.PAPER_QUESTION_NOT_FOUND);
            }
            ExamAnswer existing = answerMapper.selectOne(new LambdaQueryWrapper<ExamAnswer>()
                    .eq(ExamAnswer::getAttemptId, attemptId)
                    .eq(ExamAnswer::getPaperQuestionId, item.paperQuestionId()));
            if (existing == null) {
                ExamAnswer answer = new ExamAnswer();
                answer.setAttemptId(attemptId);
                answer.setPaperQuestionId(item.paperQuestionId());
                answer.setSubmittedAnswer(item.answer().trim());
                answer.setAnsweredAt(now);
                answerMapper.insert(answer);
            } else {
                existing.setSubmittedAnswer(item.answer().trim());
                existing.setAnsweredAt(now);
                answerMapper.updateById(existing);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResultVO submit(Long userId, Long attemptId) {
        ExamAttempt attempt = requireOwnAttempt(userId, attemptId);
        AttemptStatus status = AttemptStatus.of(attempt.getStatus());
        if (status == AttemptStatus.SUBMITTED) {
            // 幂等：已交卷直接返回既有成绩
            return toSubmitVO(attempt);
        }
        if (status != AttemptStatus.IN_PROGRESS) {
            throw new BizException(ExamErrorCode.ATTEMPT_STATUS_INVALID);
        }
        return finalizeAttempt(attempt);
    }

    @Override
    public AttemptResultVO result(Long userId, Long attemptId) {
        ExamAttempt attempt = requireOwnAttempt(userId, attemptId);
        if (AttemptStatus.of(attempt.getStatus()) == AttemptStatus.IN_PROGRESS) {
            if (isExpired(attempt)) {
                autoSubmit(attempt.getId());
                attempt = requireOwnAttempt(userId, attemptId);
            } else {
                throw new BizException(ExamErrorCode.ATTEMPT_STATUS_INVALID);
            }
        }
        Exam exam = examMapper.selectById(attempt.getExamId());
        return buildResultVO(attempt, exam);
    }

    @Override
    public PageResult<SubmitResultVO> myResults(Long userId, int pageNum, int pageSize) {
        Page<ExamAttempt> page = attemptMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getUserId, userId)
                        .eq(ExamAttempt::getStatus, AttemptStatus.SUBMITTED.code())
                        .orderByDesc(ExamAttempt::getId));
        List<SubmitResultVO> list = page.getRecords().stream().map(this::toSubmitVO).toList();
        return PageResult.of(list, page.getTotal());
    }

    @Override
    public List<AvailableExamVO> available(Long userId) {
        return available(userId, null);
    }

    @Override
    public List<AvailableExamVO> available(Long userId, Long certificateId) {
        LambdaQueryWrapper<Exam> examWrapper = new LambdaQueryWrapper<Exam>()
                .eq(Exam::getStatus, ExamStatus.PUBLISHED.code());
        if (certificateId != null) {
            examWrapper.eq(Exam::getCertificateId, certificateId);
        }
        List<Exam> published = examMapper.selectList(examWrapper.orderByDesc(Exam::getId));
        LocalDateTime now = LocalDateTime.now();
        Map<Long, ExamPaper> validPapers = paperMapper.selectList(new LambdaQueryWrapper<ExamPaper>()
                        .eq(ExamPaper::getStatus, PaperStatus.VALID.code())
                        .in(ExamPaper::getExamId, published.stream().map(Exam::getId).toList()))
                .stream().collect(Collectors.toMap(ExamPaper::getExamId, Function.identity(), (a, b) -> a));
        Map<Long, Long> inProgressByExam = attemptMapper.selectList(new LambdaQueryWrapper<ExamAttempt>()
                        .eq(ExamAttempt::getUserId, userId)
                        .eq(ExamAttempt::getStatus, AttemptStatus.IN_PROGRESS.code()))
                .stream().collect(Collectors.toMap(ExamAttempt::getExamId, ExamAttempt::getId, (a, b) -> a));
        return published.stream()
                .filter(exam -> validPapers.containsKey(exam.getId()))
                .filter(exam -> inWindow(exam, now))
                .map(exam -> {
                    ExamPaper paper = validPapers.get(exam.getId());
                    return new AvailableExamVO(exam.getId(), exam.getName(), exam.getDurationMinutes(),
                            paper.getQuestionCount(), paper.getTotalScore(), exam.getPassScore(),
                            exam.getValidFrom(), exam.getValidUntil(), inProgressByExam.get(exam.getId()));
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoSubmit(Long attemptId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null || AttemptStatus.of(attempt.getStatus()) != AttemptStatus.IN_PROGRESS) {
            return;
        }
        if (!isExpired(attempt)) {
            return;
        }
        finalizeAttempt(attempt);
    }

    // ---------- 判分与交卷（唯一结算路径） ----------

    /**
     * 结算：逐题判分（经 QuestionQueryApi 委托）→ 写 exam_answer → 条件更新 attempt。
     * 条件更新行数=0 表示已被并发交卷：不重复计分，直接返回既有成绩。
     */
    private SubmitResultVO finalizeAttempt(ExamAttempt attempt) {
        List<ExamPaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, attempt.getPaperId())
                        .orderByAsc(ExamPaperQuestion::getSort));
        Map<Long, ExamAnswer> answersByPq = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getAttemptId, attempt.getId()))
                .stream().collect(Collectors.toMap(ExamAnswer::getPaperQuestionId, Function.identity(), (a, b) -> a));

        BigDecimal score = BigDecimal.ZERO;
        int correctCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ExamPaperQuestion pq : paperQuestions) {
            ExamAnswer answer = answersByPq.get(pq.getId());
            String submitted = answer == null ? "" : answer.getSubmittedAnswer() == null ? "" : answer.getSubmittedAnswer();
            boolean correct = gradeSafely(pq, submitted);
            if (correct) {
                correctCount++;
                score = score.add(pq.getScore());
            }
            if (answer == null) {
                ExamAnswer newAnswer = new ExamAnswer();
                newAnswer.setAttemptId(attempt.getId());
                newAnswer.setPaperQuestionId(pq.getId());
                newAnswer.setSubmittedAnswer(submitted);
                newAnswer.setCorrect(correct ? 1 : 0);
                newAnswer.setScore(correct ? pq.getScore() : ZERO_POINT);
                newAnswer.setAnsweredAt(now);
                answerMapper.insert(newAnswer);
            } else {
                answer.setCorrect(correct ? 1 : 0);
                answer.setScore(correct ? pq.getScore() : ZERO_POINT);
                answerMapper.updateById(answer);
            }
        }

        // 条件更新：仅 进行中 → 已交卷 成功者计分；重复/并发交卷不重复计分
        ExamAttempt update = new ExamAttempt();
        update.setStatus(AttemptStatus.SUBMITTED.code());
        update.setSubmittedAt(now);
        update.setScore(score);
        update.setCorrectCount(correctCount);
        int rows = attemptMapper.update(update, new LambdaUpdateWrapper<ExamAttempt>()
                .eq(ExamAttempt::getId, attempt.getId())
                .eq(ExamAttempt::getStatus, AttemptStatus.IN_PROGRESS.code()));
        if (rows == 1) {
            eventPublisher.publishEvent(new ExamFinishedEvent(attempt.getId(), attempt.getUserId(),
                    attempt.getExamId(), score, correctCount, paperQuestions.size(), now));
        }
        ExamAttempt finalAttempt = attemptMapper.selectById(attempt.getId());
        return toSubmitVO(finalAttempt);
    }

    /** 判分委托：题型判分唯一实现在 question 域；非法答案按错误处理（不阻断交卷） */
    private boolean gradeSafely(ExamPaperQuestion pq, String submitted) {
        try {
            return questionQueryApi.gradeSubmission(pq.getQuestionType(), pq.getStandardAnswer(), submitted);
        } catch (BizException e) {
            return false;
        }
    }

    // ---------- VO 构建 ----------

    private AttemptStartVO buildStartVO(ExamAttempt attempt, Exam exam) {
        List<ExamPaperQuestion> questions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, attempt.getPaperId())
                        .orderByAsc(ExamPaperQuestion::getSort));
        List<Long> pqIds = questions.stream().map(ExamPaperQuestion::getId).toList();
        Map<Long, List<ExamPaperOption>> optionsByPq = pqIds.isEmpty() ? Map.of()
                : paperOptionMapper.selectList(new LambdaQueryWrapper<ExamPaperOption>()
                        .in(ExamPaperOption::getPaperQuestionId, pqIds)
                        .orderByAsc(ExamPaperOption::getSort).orderByAsc(ExamPaperOption::getOptionKey))
                        .stream().collect(Collectors.groupingBy(ExamPaperOption::getPaperQuestionId));
        Map<Long, ExamAnswer> answersByPq = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getAttemptId, attempt.getId()))
                .stream().collect(Collectors.toMap(ExamAnswer::getPaperQuestionId, Function.identity(), (a, b) -> a));
        boolean inProgress = AttemptStatus.of(attempt.getStatus()) == AttemptStatus.IN_PROGRESS;
        List<AttemptQuestionVO> questionVOs = questions.stream()
                .map(q -> new AttemptQuestionVO(q.getId(), q.getSort(), q.getQuestionType(), q.getStem(),
                        optionsByPq.getOrDefault(q.getId(), List.of()).stream()
                                .map(o -> new AttemptQuestionVO.AttemptOptionVO(o.getOptionKey(), o.getContent()))
                                .toList(),
                        inProgress ? answersByPq.getOrDefault(q.getId(), new ExamAnswer()).getSubmittedAnswer() : null))
                .toList();
        return new AttemptStartVO(attempt.getId(), attempt.getExamId(), attempt.getStatus(),
                exam == null ? null : exam.getName(),
                paperDuration(attempt), attempt.getQuestionCount(),
                attempt.getStartedAt(), attempt.getExpiredAt(), questionVOs);
    }

    private Integer paperDuration(ExamAttempt attempt) {
        ExamPaper paper = paperMapper.selectById(attempt.getPaperId());
        return paper == null ? null : paper.getDurationMinutes();
    }

    private SubmitResultVO toSubmitVO(ExamAttempt attempt) {
        Exam exam = examMapper.selectById(attempt.getExamId());
        Boolean passed = null;
        if (attempt.getScore() != null && exam != null && exam.getPassScore() != null) {
            passed = attempt.getScore().compareTo(exam.getPassScore()) >= 0;
        }
        return new SubmitResultVO(attempt.getId(), attempt.getStatus(), attempt.getScore(),
                attempt.getCorrectCount(), attempt.getQuestionCount(),
                exam == null ? null : exam.getPassScore(), passed, attempt.getSubmittedAt());
    }

    private AttemptResultVO buildResultVO(ExamAttempt attempt, Exam exam) {
        List<ExamPaperQuestion> questions = paperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, attempt.getPaperId())
                        .orderByAsc(ExamPaperQuestion::getSort));
        List<Long> pqIds = questions.stream().map(ExamPaperQuestion::getId).toList();
        Map<Long, List<ExamPaperOption>> optionsByPq = pqIds.isEmpty() ? Map.of()
                : paperOptionMapper.selectList(new LambdaQueryWrapper<ExamPaperOption>()
                        .in(ExamPaperOption::getPaperQuestionId, pqIds)
                        .orderByAsc(ExamPaperOption::getSort).orderByAsc(ExamPaperOption::getOptionKey))
                        .stream().collect(Collectors.groupingBy(ExamPaperOption::getPaperQuestionId));
        Map<Long, ExamAnswer> answersByPq = answerMapper.selectList(new LambdaQueryWrapper<ExamAnswer>()
                        .eq(ExamAnswer::getAttemptId, attempt.getId()))
                .stream().collect(Collectors.toMap(ExamAnswer::getPaperQuestionId, Function.identity(), (a, b) -> a));
        Boolean passed = null;
        if (attempt.getScore() != null && exam != null && exam.getPassScore() != null) {
            passed = attempt.getScore().compareTo(exam.getPassScore()) >= 0;
        }
        List<AttemptResultVO.ResultItemVO> items = questions.stream()
                .map(q -> {
                    ExamAnswer answer = answersByPq.get(q.getId());
                    return new AttemptResultVO.ResultItemVO(q.getId(), q.getSort(), q.getQuestionType(),
                            q.getStem(), q.getStandardAnswer(),
                            answer == null ? "" : answer.getSubmittedAnswer(),
                            answer == null ? null : answer.getCorrect() != null && answer.getCorrect() == 1,
                            answer == null ? null : answer.getScore(),
                            q.getAnalysis(),
                            optionsByPq.getOrDefault(q.getId(), List.of()).stream()
                                    .map(o -> new AttemptQuestionVO.AttemptOptionVO(o.getOptionKey(), o.getContent()))
                                    .toList());
                })
                .toList();
        return new AttemptResultVO(attempt.getId(), attempt.getExamId(),
                exam == null ? null : exam.getName(),
                attempt.getScore(), attempt.getCorrectCount(), attempt.getQuestionCount(),
                exam == null ? null : exam.getPassScore(), passed,
                paperDuration(attempt), attempt.getStartedAt(), attempt.getSubmittedAt(), items);
    }

    // ---------- 校验 ----------

    private Exam requireAvailableExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BizException(ExamErrorCode.EXAM_NOT_FOUND);
        }
        if (ExamStatus.of(exam.getStatus()) != ExamStatus.PUBLISHED || !inWindow(exam, LocalDateTime.now())) {
            throw new BizException(ExamErrorCode.EXAM_NOT_AVAILABLE);
        }
        return exam;
    }

    private ExamPaper requireValidPaper(Long examId) {
        ExamPaper paper = paperMapper.selectOne(new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getExamId, examId)
                .eq(ExamPaper::getStatus, PaperStatus.VALID.code())
                .last("LIMIT 1"));
        if (paper == null) {
            throw new BizException(ExamErrorCode.PAPER_NOT_FOUND);
        }
        return paper;
    }

    private boolean inWindow(Exam exam, LocalDateTime now) {
        if (exam.getValidFrom() != null && now.isBefore(exam.getValidFrom())) {
            return false;
        }
        if (exam.getValidUntil() != null && now.isAfter(exam.getValidUntil())) {
            return false;
        }
        return true;
    }

    private boolean isExpired(ExamAttempt attempt) {
        return AttemptStatus.of(attempt.getStatus()) == AttemptStatus.IN_PROGRESS
                && attempt.getExpiredAt() != null
                && !LocalDateTime.now().isBefore(attempt.getExpiredAt());
    }

    private ExamAttempt requireOwnAttempt(Long userId, Long attemptId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BizException(ExamErrorCode.ATTEMPT_NOT_FOUND);
        }
        if (!attempt.getUserId().equals(userId)) {
            throw new BizException(ExamErrorCode.ATTEMPT_FORBIDDEN);
        }
        return attempt;
    }
}
