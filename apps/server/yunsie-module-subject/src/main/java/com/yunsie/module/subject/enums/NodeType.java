package com.yunsie.module.subject.enums;

/**
 * 知识节点类型（subject_knowledge_node.node_type）。
 */
public enum NodeType {

    CHAPTER(1, "章节"),
    KNOWLEDGE_POINT(2, "知识点"),
    CHILD_KNOWLEDGE_POINT(3, "子知识点");

    private final int code;
    private final String desc;

    NodeType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public static NodeType of(Integer code) {
        if (code == null) {
            return null;
        }
        for (NodeType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
