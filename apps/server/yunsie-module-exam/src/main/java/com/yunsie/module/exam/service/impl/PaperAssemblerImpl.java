package com.yunsie.module.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.exam.dto.ExamCreateReq;
import com.yunsie.module.exam.entity.Exam;
import com.yunsie.module.exam.entity.ExamPaper;
import com.yunsie.module.exam.entity.ExamPaperOption;
import com.yunsie.module.exam.entity.ExamPaperQuestion;
import com.yunsie.module.exam.enums.PaperStatus;
import com.yunsie.module.exam.error.ExamErrorCode;
import com.yunsie.module.exam.mapper.ExamPaperMapper;
import com.yunsie.module.exam.mapper.ExamPaperOptionMapper;
import com.yunsie.module.exam.mapper.ExamPaperQuestionMapper;
import com.yunsie.module.exam.service.PaperAssembler;
import com.yunsie.module.exam.service.PaperShuffler;
import com.yunsie.module.question.api.QuestionQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 组卷装配器实现。
 * 关键规则（exam-engine）：
 *   - 试卷快照不可变（题干/选项/答案/解析/版本/知识点全部复制）
 *   - 随机种子落库可复现；同卷 question_id 不重复
 *   - 候选题不足明确失败，禁止静默降级/塞题
 *   - 每题 MVP 1 分；题号 sort 从 1 开始
 */
@Component
@RequiredArgsConstructor
public class PaperAssemblerImpl implements PaperAssembler {

    private static final BigDecimal ONE_POINT = new BigDecimal("1.00");
    private static final int CANDIDATE_LIMIT = 200;

    private final ExamPaperMapper paperMapper;
    private final ExamPaperQuestionMapper paperQuestionMapper;
    private final ExamPaperOptionMapper paperOptionMapper;
    private final QuestionQueryApi questionQueryApi;
    private final PaperShuffler paperShuffler;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long assemble(Exam exam, ExamCreateReq.AssembleRule rule) {
        // 1. 旧有效卷作废（字符串列名，兼容无 lambda 元数据的环境）
        paperMapper.update(null, new UpdateWrapper<ExamPaper>()
                .eq("exam_id", exam.getId())
                .eq("status", PaperStatus.VALID.code())
                .set("status", PaperStatus.VOID.code()));

        // 2. 抽题：证书 + 题型 + 知识点范围（经 QuestionQueryApi，仅已发布）
        List<QuestionQueryApi.QuestionSnapshot> candidates = questionQueryApi.findPublishedQuestions(
                exam.getCertificateId(), rule.questionTypes(), rule.nodeIds(), CANDIDATE_LIMIT);

        // 3. 难度过滤（规则指定时）
        if (rule.difficulty() != null) {
            candidates = candidates.stream()
                    .filter(c -> rule.difficulty().equals(c.difficulty()))
                    .toList();
        }
        if (candidates.size() < rule.questionCount()) {
            throw new BizException(ExamErrorCode.EXAMS_INSUFFICIENT_NEED.code(),
                    String.format(ExamErrorCode.EXAMS_INSUFFICIENT_NEED.message(),
                            rule.questionCount(), candidates.size()));
        }

        // 4. 可复现随机种子 + 洗牌（同卷不重复：question_id 天然唯一）
        long seed = secureRandom.nextLong();
        List<QuestionQueryApi.QuestionSnapshot> shuffled = paperShuffler.shuffle(candidates, seed);
        List<QuestionQueryApi.QuestionSnapshot> picked = shuffled.subList(0, rule.questionCount());

        // 5. 试卷快照容器
        ExamPaper paper = new ExamPaper();
        paper.setExamId(exam.getId());
        paper.setTitle(exam.getName());
        paper.setDurationMinutes(exam.getDurationMinutes());
        paper.setTotalScore(BigDecimal.valueOf(rule.questionCount()));
        paper.setQuestionCount(rule.questionCount());
        paper.setStatus(PaperStatus.VALID.code());
        paper.setAssembleSeed(seed);
        paperMapper.insert(paper);

        // 6. 题目快照 + 选项快照
        int sort = 1;
        for (QuestionQueryApi.QuestionSnapshot source : picked) {
            ExamPaperQuestion pq = new ExamPaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(source.id());
            pq.setSort(sort++);
            pq.setScore(ONE_POINT);
            pq.setQuestionType(source.questionType());
            pq.setStem(source.stem());
            pq.setAnalysis(source.analysis());
            pq.setStandardAnswer(source.answer());
            pq.setDifficulty(source.difficulty());
            pq.setSource(source.source());
            pq.setContentVersion(source.contentVersion());
            pq.setNodeIds(source.nodeIds() == null ? "" :
                    source.nodeIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
            paperQuestionMapper.insert(pq);

            int optionSort = 0;
            for (QuestionQueryApi.OptionSnapshot option : source.options()) {
                ExamPaperOption po = new ExamPaperOption();
                po.setPaperQuestionId(pq.getId());
                po.setOptionKey(option.optionKey());
                po.setContent(option.content());
                po.setSort(optionSort++);
                paperOptionMapper.insert(po);
            }
        }
        return paper.getId();
    }
}
