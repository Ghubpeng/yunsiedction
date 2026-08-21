package com.yunsie.module.question.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.question.api.QuestionCommandApi;
import com.yunsie.module.question.entity.Question;
import com.yunsie.module.question.entity.QuestionKnowledgeNode;
import com.yunsie.module.question.entity.QuestionOption;
import com.yunsie.module.question.enums.QuestionStatus;
import com.yunsie.module.question.mapper.QuestionKnowledgeNodeMapper;
import com.yunsie.module.question.mapper.QuestionMapper;
import com.yunsie.module.question.mapper.QuestionOptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 题库复制实现（Stage 2.3B）：
 * 复制已发布题目为草稿（审核通道铁律：发布不可绕过审核）；
 * 选项与知识点关联一并复制，知识点按 nodeIdMap 映射（无法映射的题目跳过）。
 */
@Service
@RequiredArgsConstructor
public class QuestionCommandApiImpl implements QuestionCommandApi {

    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper optionMapper;
    private final QuestionKnowledgeNodeMapper knowledgeNodeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int copyPublishedQuestions(Long sourceCertId, Long targetCertId, Map<Long, Long> nodeIdMap) {
        List<Question> sources = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .eq(Question::getCertificateId, sourceCertId)
                .eq(Question::getStatus, QuestionStatus.PUBLISHED.code())
                .orderByAsc(Question::getId));
        int copied = 0;
        for (Question source : sources) {
            List<Long> sourceNodeIds = knowledgeNodeMapper.selectList(
                            new LambdaQueryWrapper<QuestionKnowledgeNode>()
                                    .eq(QuestionKnowledgeNode::getQuestionId, source.getId()))
                    .stream().map(QuestionKnowledgeNode::getNodeId).toList();
            List<Long> mappedNodeIds = sourceNodeIds.stream()
                    .map(nodeIdMap::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (mappedNodeIds.isEmpty()) {
                continue; // 知识点全部未映射 → 跳过（不允许无知识点题目）
            }
            Question copy = new Question();
            copy.setCertificateId(targetCertId);
            copy.setQuestionType(source.getQuestionType());
            copy.setStem(source.getStem());
            copy.setAnalysis(source.getAnalysis());
            copy.setAnswer(source.getAnswer());
            copy.setDifficulty(source.getDifficulty());
            copy.setSource(source.getSource());
            copy.setStatus(QuestionStatus.DRAFT.code());
            copy.setContentVersion(1);
            questionMapper.insert(copy);
            for (QuestionOption o : optionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                    .eq(QuestionOption::getQuestionId, source.getId()))) {
                QuestionOption co = new QuestionOption();
                co.setQuestionId(copy.getId());
                co.setOptionKey(o.getOptionKey());
                co.setContent(o.getContent());
                co.setSort(o.getSort());
                optionMapper.insert(co);
            }
            for (Long nodeId : mappedNodeIds) {
                QuestionKnowledgeNode link = new QuestionKnowledgeNode();
                link.setQuestionId(copy.getId());
                link.setNodeId(nodeId);
                knowledgeNodeMapper.insert(link);
            }
            copied++;
        }
        return copied;
    }
}
