package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunsie.common.api.PageResult;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.entity.QuestionMistake;
import com.yunsie.module.question.error.QuestionErrorCode;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionMistakeMapper;
import com.yunsie.module.question.service.MistakeService;
import com.yunsie.module.question.vo.MistakeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 错题本实现（H 模型）：uk(user_id, question_id, deleted) 幂等。
 */
@Service
@RequiredArgsConstructor
public class MistakeServiceImpl implements MistakeService {

    private final QuestionMistakeMapper mistakeMapper;
    private final QuestionMapper questionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordWrong(Long userId, Long questionId) {
        QuestionMistake existing = find(userId, questionId);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            QuestionMistake mistake = new QuestionMistake();
            mistake.setUserId(userId);
            mistake.setQuestionId(questionId);
            mistake.setMistakeCount(1);
            mistake.setLastMistakeTime(now);
            mistake.setStatus(1);
            try {
                mistakeMapper.insert(mistake);
            } catch (DuplicateKeyException e) {
                // 并发下唯一索引兜底：降级为累加更新
                incrementExisting(userId, questionId, now);
            }
        } else {
            existing.setMistakeCount(existing.getMistakeCount() == null ? 1 : existing.getMistakeCount() + 1);
            existing.setLastMistakeTime(now);
            existing.setStatus(1);
            mistakeMapper.updateById(existing);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordCorrect(Long userId, Long questionId) {
        QuestionMistake existing = find(userId, questionId);
        if (existing != null) {
            existing.setLastPracticeTime(LocalDateTime.now());
            mistakeMapper.updateById(existing);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long userId, Long mistakeId) {
        QuestionMistake mistake = mistakeMapper.selectById(mistakeId);
        if (mistake == null || !mistake.getUserId().equals(userId)) {
            throw new BizException(QuestionErrorCode.MISTAKE_NOT_FOUND);
        }
        mistake.setStatus(2);
        mistakeMapper.updateById(mistake);
    }

    @Override
    public PageResult<MistakeVO> page(Long userId, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<QuestionMistake> wrapper = new LambdaQueryWrapper<QuestionMistake>()
                .eq(QuestionMistake::getUserId, userId);
        if (status != null) {
            wrapper.eq(QuestionMistake::getStatus, status);
        }
        wrapper.orderByDesc(QuestionMistake::getLastMistakeTime);
        Page<QuestionMistake> page = mistakeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        List<Long> questionIds = page.getRecords().stream().map(QuestionMistake::getQuestionId).distinct().toList();
        Map<Long, Question> questions = questionIds.isEmpty() ? Map.of()
                : questionMapper.selectBatchIds(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));

        List<MistakeVO> list = page.getRecords().stream()
                .map(m -> {
                    Question q = questions.get(m.getQuestionId());
                    return new MistakeVO(m.getId(), m.getQuestionId(),
                            q == null ? null : q.getQuestionType(),
                            q == null ? null : q.getStem(),
                            m.getMistakeCount(), m.getStatus(),
                            m.getLastMistakeTime(), m.getLastPracticeTime());
                })
                .toList();
        return PageResult.of(list, page.getTotal());
    }

    private QuestionMistake find(Long userId, Long questionId) {
        return mistakeMapper.selectOne(new LambdaQueryWrapper<QuestionMistake>()
                .eq(QuestionMistake::getUserId, userId)
                .eq(QuestionMistake::getQuestionId, questionId));
    }

    private void incrementExisting(Long userId, Long questionId, LocalDateTime now) {
        QuestionMistake existing = find(userId, questionId);
        if (existing == null) {
            return;
        }
        existing.setMistakeCount(existing.getMistakeCount() == null ? 1 : existing.getMistakeCount() + 1);
        existing.setLastMistakeTime(now);
        existing.setStatus(1);
        mistakeMapper.updateById(existing);
    }
}
