package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.model.QrValidationError.Code;
import com.vnpay.qrparser.model.ReferenceLabel;

/** Type 03/04: reference khác prefix; không kế thừa điều kiện amount/bill của Type 01. */
final class ReferenceQrRules implements TypeSpecificRules {
    public void validate(ValidationContext context) {
        for (var tag : context.tags()) {
            switch (tag.path()) {
                case "54" -> CommonQrRules.maximum(context, tag, 13);
                case "62/05" -> {
                    CommonQrRules.maximum(context, tag, 25);
                    if (ReferenceLabel.parse(tag.node().value(), context.type()).isEmpty()) {
                        String prefix = context.type() == QrType.TYPE_03 ? "02" : "01";
                        context.error(Code.INVALID_FIELD_VALUE, tag, "Excel " + context.type().sheetName()
                                + "!K28 yêu cầu " + prefix + " + thời hạn yymmddhhmm + mã tham chiếu không rỗng.");
                    }
                }
                case "01" -> {
                    String expected = context.type() == QrType.TYPE_03 ? "11" : "12";
                    if (!tag.node().value().equals(expected)) {
                        context.error(Code.INVALID_FIELD_VALUE, tag, "Excel " + context.type().sheetName()
                                + "!F5 chỉ mô tả 01=" + expected + ".");
                    }
                }
                default -> { }
            }
        }
    }
}
