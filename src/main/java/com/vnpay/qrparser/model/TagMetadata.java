package com.vnpay.qrparser.model;

import java.util.Map;

/** Tên trường, mô tả và bảng giải nghĩa các mã giá trị theo specification. */
public record TagMetadata(String name, String description, Map<String, String> valueMeanings) {

    public TagMetadata {
        // Bảo vệ metadata khỏi việc sửa đổi bảng mã từ bên ngoài.
        valueMeanings = Map.copyOf(valueMeanings);
    }
}
