package com.yunsie.module.subject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.certificate.api.CertificateQueryApi;
import com.yunsie.module.subject.dto.NodeCreateReq;
import com.yunsie.module.subject.dto.NodeMoveReq;
import com.yunsie.module.subject.entity.ExamSubject;
import com.yunsie.module.subject.entity.KnowledgeNode;
import com.yunsie.module.subject.entity.SubjectVersion;
import com.yunsie.module.subject.enums.NodeType;
import com.yunsie.module.subject.error.SubjectErrorCode;
import com.yunsie.module.subject.mapper.ExamSubjectMapper;
import com.yunsie.module.subject.mapper.KnowledgeNodeMapper;
import com.yunsie.module.subject.mapper.SubjectVersionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识节点服务单元测试：层级规则 / path·level 计算 / 移动保护 / 删除保护。
 */
class KnowledgeNodeServiceTest {

    private KnowledgeNodeServiceImpl service(KnowledgeNodeMapper nodeMapper,
                                             SubjectVersionMapper versionMapper,
                                             ExamSubjectMapper subjectMapper,
                                             CertificateQueryApi certificateQueryApi) {
        return new KnowledgeNodeServiceImpl(nodeMapper, versionMapper, subjectMapper, certificateQueryApi,
                mock(com.yunsie.common.api.SubjectUsageProbe.class));
    }

    private KnowledgeNodeServiceImpl service(KnowledgeNodeMapper nodeMapper,
                                             com.yunsie.common.api.SubjectUsageProbe probe) {
        return new KnowledgeNodeServiceImpl(nodeMapper, mock(SubjectVersionMapper.class),
                mock(ExamSubjectMapper.class), mock(CertificateQueryApi.class), probe);
    }

    private SubjectVersion version(Long id, Long certificateId) {
        SubjectVersion v = new SubjectVersion();
        v.setId(id);
        v.setCertificateId(certificateId);
        return v;
    }

    private ExamSubject subject(Long id, Long certificateId) {
        ExamSubject s = new ExamSubject();
        s.setId(id);
        s.setCertificateId(certificateId);
        return s;
    }

    private KnowledgeNode node(Long id, Integer nodeType, Long versionId, Long subjectId, String path, int level) {
        KnowledgeNode n = new KnowledgeNode();
        n.setId(id);
        n.setNodeType(nodeType);
        n.setVersionId(versionId);
        n.setSubjectId(subjectId);
        n.setPath(path);
        n.setLevel(level);
        return n;
    }

    private NodeCreateReq createReq(int nodeType, Long parentId) {
        return new NodeCreateReq(1L, 10L, parentId, nodeType, "节点", "", "code-" + nodeType, 0, 1, 1, "");
    }

    @Test
    void create_kpUnderChapter_computesPathAndLevel() {
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        SubjectVersionMapper versionMapper = mock(SubjectVersionMapper.class);
        ExamSubjectMapper subjectMapper = mock(ExamSubjectMapper.class);
        when(versionMapper.selectById(1L)).thenReturn(version(1L, 100L));
        when(subjectMapper.selectById(10L)).thenReturn(subject(10L, 100L));
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(nodeMapper.selectById(5L))
                .thenReturn(node(5L, NodeType.CHAPTER.code(), 1L, 10L, "/5/", 1));
        doAnswer(inv -> {
            KnowledgeNode n = inv.getArgument(0);
            n.setId(50L);
            return 1;
        }).when(nodeMapper).insert(any(KnowledgeNode.class));

        Long id = service(nodeMapper, versionMapper, subjectMapper, mock(CertificateQueryApi.class))
                .create(createReq(NodeType.KNOWLEDGE_POINT.code(), 5L));

        assertEquals(50L, id);
        ArgumentCaptor<KnowledgeNode> captor = ArgumentCaptor.forClass(KnowledgeNode.class);
        verify(nodeMapper).updateById(captor.capture());
        assertEquals("/5/50/", captor.getValue().getPath());
        assertEquals(2, captor.getValue().getLevel());
    }

    @Test
    void create_childKpUnderChapter_invalidParentType() {
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        SubjectVersionMapper versionMapper = mock(SubjectVersionMapper.class);
        ExamSubjectMapper subjectMapper = mock(ExamSubjectMapper.class);
        when(versionMapper.selectById(1L)).thenReturn(version(1L, 100L));
        when(subjectMapper.selectById(10L)).thenReturn(subject(10L, 100L));
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(nodeMapper.selectById(5L))
                .thenReturn(node(5L, NodeType.CHAPTER.code(), 1L, 10L, "/5/", 1));

        BizException ex = assertThrows(BizException.class,
                () -> service(nodeMapper, versionMapper, subjectMapper, mock(CertificateQueryApi.class))
                        .create(createReq(NodeType.CHILD_KNOWLEDGE_POINT.code(), 5L)));
        assertEquals(SubjectErrorCode.NODE_PARENT_TYPE_INVALID.code(), ex.getCode());
    }

    @Test
    void create_chapterWithParent_invalid() {
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        SubjectVersionMapper versionMapper = mock(SubjectVersionMapper.class);
        ExamSubjectMapper subjectMapper = mock(ExamSubjectMapper.class);
        when(versionMapper.selectById(1L)).thenReturn(version(1L, 100L));
        when(subjectMapper.selectById(10L)).thenReturn(subject(10L, 100L));
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(nodeMapper.selectById(5L))
                .thenReturn(node(5L, NodeType.CHAPTER.code(), 1L, 10L, "/5/", 1));

        BizException ex = assertThrows(BizException.class,
                () -> service(nodeMapper, versionMapper, subjectMapper, mock(CertificateQueryApi.class))
                        .create(createReq(NodeType.CHAPTER.code(), 5L)));
        assertEquals(SubjectErrorCode.NODE_PARENT_TYPE_INVALID.code(), ex.getCode());
    }

    @Test
    void move_intoDescendant_blocked() {
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        when(nodeMapper.selectById(2L)).thenReturn(node(2L, NodeType.KNOWLEDGE_POINT.code(), 1L, 10L, "/1/2/", 2));
        when(nodeMapper.selectById(3L)).thenReturn(node(3L, NodeType.CHILD_KNOWLEDGE_POINT.code(), 1L, 10L, "/1/2/3/", 3));

        BizException ex = assertThrows(BizException.class,
                () -> service(nodeMapper, mock(SubjectVersionMapper.class), mock(ExamSubjectMapper.class),
                        mock(CertificateQueryApi.class)).move(2L, new NodeMoveReq(3L, 0)));
        assertEquals(SubjectErrorCode.NODE_MOVE_INVALID.code(), ex.getCode());
    }

    @Test
    void delete_withChildren_blocked() {
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        when(nodeMapper.selectById(1L)).thenReturn(node(1L, NodeType.CHAPTER.code(), 1L, 10L, "/1/", 1));
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> service(nodeMapper, mock(SubjectVersionMapper.class), mock(ExamSubjectMapper.class),
                        mock(CertificateQueryApi.class)).delete(1L));
        assertEquals(SubjectErrorCode.NODE_HAS_CHILDREN.code(), ex.getCode());
    }

    @Test
    void delete_chapterWithCourses_30217() {
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        when(nodeMapper.selectById(1L)).thenReturn(node(1L, NodeType.CHAPTER.code(), 1L, 10L, "/1/", 1));
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        com.yunsie.common.api.SubjectUsageProbe probe = mock(com.yunsie.common.api.SubjectUsageProbe.class);
        when(probe.countCourseRefsByChapter(1L)).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> service(nodeMapper, probe).delete(1L));
        assertEquals(SubjectErrorCode.NODE_HAS_COURSES.code(), ex.getCode());
    }

    @Test
    void delete_nodeWithQuestions_30218() {
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        when(nodeMapper.selectById(2L)).thenReturn(node(2L, NodeType.KNOWLEDGE_POINT.code(), 1L, 10L, "/1/2/", 2));
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        com.yunsie.common.api.SubjectUsageProbe probe = mock(com.yunsie.common.api.SubjectUsageProbe.class);
        when(probe.countQuestionRefsByNode(2L)).thenReturn(2L);

        BizException ex = assertThrows(BizException.class, () -> service(nodeMapper, probe).delete(2L));
        assertEquals(SubjectErrorCode.NODE_HAS_QUESTIONS.code(), ex.getCode());
    }

    @Test
    void delete_leafNoRefs_success() {
        KnowledgeNodeMapper nodeMapper = mock(KnowledgeNodeMapper.class);
        KnowledgeNode kp = node(2L, NodeType.KNOWLEDGE_POINT.code(), 1L, 10L, "/1/2/", 2);
        kp.setCode("KP2");
        when(nodeMapper.selectById(2L)).thenReturn(kp);
        when(nodeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        com.yunsie.common.api.SubjectUsageProbe probe = mock(com.yunsie.common.api.SubjectUsageProbe.class);
        when(probe.countCourseRefsByChapter(2L)).thenReturn(0L);
        when(probe.countQuestionRefsByNode(2L)).thenReturn(0L);

        service(nodeMapper, probe).delete(2L);
        assertEquals("KP2#d2", kp.getCode());
        verify(nodeMapper).deleteById(2L);
    }
}
