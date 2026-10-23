package com.vnpay.qrparser.model;

/** Loại do caller chọn; không suy luận từ payload hoặc prefix của reference. */
public enum QrType {
    TYPE_01("01"), TYPE_02("02"), TYPE_03("03"), TYPE_04("04");

    private final String code;

    QrType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public String sheetName() {
        return "Type " + code;
    }

    public static QrType fromCode(String code) {
        for (QrType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Type phải là 01, 02, 03 hoặc 04.");
    }
}
