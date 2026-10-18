package com.vnpay.qrparser.model;

import java.util.List;

/** Dữ liệu TLV gốc cùng tên trường, mô tả và giá trị dùng để hiển thị. */
public record InterpretedTlvNode(
        String tag,
        int length,
        String value,
        String name,
        String description,
        String displayValue,
        List<InterpretedTlvNode> children) {

    public InterpretedTlvNode {
        children = List.copyOf(children);
    }
}
