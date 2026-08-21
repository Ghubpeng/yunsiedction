package com.yunsie.module.subject.service;

import com.yunsie.module.subject.dto.VersionCreateReq;
import com.yunsie.module.subject.dto.VersionUpdateReq;
import com.yunsie.module.subject.vo.VersionVO;

import java.util.List;

/**
 * 知识体系版本服务。每证书至多一个「当前」版本（应用层保证）。
 */
public interface SubjectVersionService {

    Long create(VersionCreateReq req);

    void update(Long id, VersionUpdateReq req);

    /** 设为当前版本：同证书原当前版本自动归档 */
    void setCurrent(Long id);

    /** 归档（当前版本可归档，归档后该证书可能无当前版本，由调用方决策） */
    void archive(Long id);

    /** 删除保护：版本下存在知识节点时禁止删除 */
    void delete(Long id);

    VersionVO get(Long id);

    List<VersionVO> listByCertificate(Long certificateId);

    VersionVO currentOfCertificate(Long certificateId);
}
