package com.yunsie.module.exam.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 可复现洗牌（exam-engine：随机种子可复现，便于复核与问题定位）。
 * 纯逻辑组件：同一种子 + 同一列表 → 同一顺序（可单元测试）。
 */
@Component
public class PaperShuffler {

    public <T> List<T> shuffle(List<T> items, long seed) {
        List<T> result = new ArrayList<>(items);
        Collections.shuffle(result, new Random(seed));
        return result;
    }
}
