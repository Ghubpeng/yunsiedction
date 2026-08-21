package com.yunsie.module.question.error;

import com.yunsie.common.error.ErrorCode;

/**
 * question 域错误码（业务码段 3xxxx，question 占用 30301-30319）。
 */
public enum QuestionErrorCode implements ErrorCode {

    QUESTION_TYPE_INVALID(30301, "题型非法或暂不支持"),
    QUESTION_NOT_FOUND(30302, "题目不存在"),
    QUESTION_NOT_PUBLISHED(30303, "题目未发布，不可练习"),
    QUESTION_STATUS_NOT_EDITABLE(30304, "当前状态不可编辑"),
    QUESTION_STATUS_NOT_DELETABLE(30305, "当前状态不可删除（已发布/待审核）"),
    QUESTION_STATUS_INVALID_ACTION(30306, "当前状态不允许该操作"),
    ANSWER_INVALID(30307, "答案非法：不在选项范围内或格式错误"),
    ANALYSIS_REQUIRED(30308, "解析必填：无解析不得发布（question-bank 铁律）"),
    OPTION_INVALID(30309, "选项非法：键重复/判断题不允许选项/选择题选项不足"),
    NODE_ASSOC_INVALID(30310, "知识点关联非法：节点不存在/未启用/非知识点类型/不属于该证书"),
    NODE_IDS_REQUIRED(30311, "题目必须关联至少一个知识点/子知识点"),
    MISTAKE_NOT_FOUND(30312, "错题记录不存在"),
    PRACTICE_MODE_INVALID(30313, "练习模式非法或缺少必要参数"),
    DUPLICATE_QUESTION(30314, "题干重复：同证书下已存在相同题干"),
    IMPORT_FILE_INVALID(30315, "导入文件非法：仅支持 .xlsx"),
    IMPORT_TOO_MANY_ROWS(30316, "导入行数超过上限（最多5000行）"),
    IMPORT_HEADER_INVALID(30317, "导入表头不符合模板"),
    QUESTION_DELETE_PENDING_REVIEW(30318, "审核中的题目请先撤回再删除"),
    QUESTION_DELETE_PUBLISHED(30319, "已发布题目删除将进入回收站（历史数据受保护）"),
    QUESTION_NOT_RECYCLED(30320, "仅回收站中的题目可恢复");

    private final int code;
    private final String message;

    QuestionErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
