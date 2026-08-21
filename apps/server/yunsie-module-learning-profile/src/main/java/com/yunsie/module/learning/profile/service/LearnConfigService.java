package com.yunsie.module.learning.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.module.learning.profile.config.MasteryParams;
import com.yunsie.module.learning.profile.config.MasteryParams.PredictionParams;
import com.yunsie.module.learning.profile.entity.LearnConfig;
import com.yunsie.module.learning.profile.mapper.LearnConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习档案配置服务（learn_config，CONFLICTS #19 域内自持）。
 * 掌握度/预测参数统一经本服务读取；算法禁止散落硬编码。
 */
@Service
@RequiredArgsConstructor
public class LearnConfigService {

    private final LearnConfigMapper configMapper;

    /** 加载全部启用配置（config_key -> config_value） */
    public Map<String, String> loadAll() {
        return configMapper.selectList(new LambdaQueryWrapper<LearnConfig>()).stream()
                .collect(Collectors.toMap(LearnConfig::getConfigKey, LearnConfig::getConfigValue, (a, b) -> a));
    }

    public MasteryParams masteryParams() {
        return MasteryParams.from(loadAll());
    }

    public PredictionParams predictionParams() {
        return PredictionParams.from(loadAll());
    }

    /** 薄弱点默认返回条数（1~50 兜底） */
    public int weaknessDefaultLimit() {
        Map<String, String> config = loadAll();
        try {
            int limit = Integer.parseInt(config.getOrDefault("learn.weakness.default.limit", "10"));
            return Math.max(1, Math.min(50, limit));
        } catch (NumberFormatException e) {
            return 10;
        }
    }
}
