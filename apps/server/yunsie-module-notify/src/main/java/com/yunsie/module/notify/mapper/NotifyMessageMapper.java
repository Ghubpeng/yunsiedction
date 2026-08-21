package com.yunsie.module.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunsie.module.notify.entity.NotifyMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内消息 Mapper（本人数据；uk 幂等由业务层 insert-catch-DuplicateKey 兜底）。
 */
@Mapper
public interface NotifyMessageMapper extends BaseMapper<NotifyMessage> {
}
