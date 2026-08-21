package com.yunsie.module.subject.service;

import com.yunsie.module.subject.dto.SubjectCreateReq;
import com.yunsie.module.subject.dto.SubjectUpdateReq;
import com.yunsie.module.subject.vo.SubjectVO;

import java.util.List;

/**
 * 考试科目服务。考试科目 != 课程（课程为商业实体，course 域实现）。
 */
public interface ExamSubjectService {

    Long create(SubjectCreateReq req);

    void update(Long id, SubjectUpdateReq req);

    /** 删除保护：科目下存在知识节点时禁止删除 */
    void delete(Long id);

    SubjectVO get(Long id);

    List<SubjectVO> listByCertificate(Long certificateId);

    /** 用户公开端：仅启用科目（且证书启用） */
    List<SubjectVO> publicListByCertificate(Long certificateId);
}
