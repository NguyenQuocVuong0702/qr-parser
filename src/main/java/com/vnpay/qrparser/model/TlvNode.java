package com.vnpay.qrparser.model;

import java.util.List;

/** Một nút trong cây TLV, giữ nguyên value gốc và thứ tự các nút con. */
public record TlvNode(String tag, int length, String value, List<TlvNode> children) {

    public TlvNode {
        // Sao chép danh sách và không cho phép thay đổi children sau khi tạo nút.
        children = List.copyOf(children);
    }
}
