package com.yunsie.module.question.service;

import com.yunsie.module.question.enums.QuestionType;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 答案标准化（E 判分模型）：统一大小写/分隔符/排序，避免选项顺序造成错误判分。
 * 判断：T/F/TRUE/FALSE/对/错/√/× → T/F；选择：字母键去重排序 join "|"。
 */
public final class QuestionAnswerUtils {

    private QuestionAnswerUtils() {
    }

    /** 标准化答案；非法输入返回 null */
    public static String normalize(String raw, QuestionType type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (type == QuestionType.TRUE_FALSE) {
            return switch (upper) {
                case "T", "TRUE", "对", "√" -> "T";
                case "F", "FALSE", "错", "×", "X" -> "F";
                default -> null;
            };
        }
        // 选择类：按分隔符拆分；无分隔符的连写字串仅多选允许按字符拆
        String[] parts = upper.split("[|,，、;；\\s]+");
        Set<String> keys = new TreeSet<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            boolean allLetters = part.chars().allMatch(c -> c >= 'A' && c <= 'Z');
            if (allLetters && part.length() > 1) {
                if (type != QuestionType.MULTIPLE_CHOICE) {
                    return null; // 单选不允许连写多键
                }
                for (char c : part.toCharArray()) {
                    keys.add(String.valueOf(c));
                }
            } else if (part.length() == 1 && part.charAt(0) >= 'A' && part.charAt(0) <= 'Z') {
                keys.add(part);
            } else {
                return null;
            }
        }
        if (keys.isEmpty() || (type == QuestionType.SINGLE_CHOICE && keys.size() != 1)) {
            return null;
        }
        return String.join("|", keys);
    }

    /** 校验标准化答案是否都在允许的选项键范围内 */
    public static boolean withinKeys(String normalized, Set<String> optionKeys) {
        if (normalized == null || optionKeys == null) {
            return false;
        }
        Set<String> keys = Arrays.stream(normalized.split("\\|")).collect(Collectors.toSet());
        return optionKeys.containsAll(keys);
    }
}
