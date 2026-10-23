package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.model.QrValidationError.Code;

/** Type 02!F5, J31:J32. Format N của template 80 mâu thuẫn với các con ans. */
final class BillingQrRules implements TypeSpecificRules {
    public void validate(ValidationContext context) {
        for (var tag : context.tags()) {
            switch (tag.path()) {
                case "62/06" -> CommonQrRules.maximum(context, tag, 25);
                case "80" -> {
                    CommonQrRules.maximum(context, tag, 99);
                    context.requireChild(tag, "00", "Excel Type 02!J31=M yêu cầu GUID 80/00 khi có template 80.");
                    context.requireChild(tag, "01", "Excel Type 02!J32=M yêu cầu kỳ cước 80/01 khi có template 80.");
                }
                case "80/00" -> CommonQrRules.maximum(context, tag, 32);
                case "80/01" -> CommonQrRules.maximum(context, tag, 20);
                case "01" -> {
                    String method = tag.node().value();
                    if (!method.equals("11") && !method.equals("12")) {
                        context.error(Code.INVALID_FIELD_VALUE, tag, "Excel Type 02 chỉ mô tả 01=11 hoặc 12.");
                    } else if (method.equals("11") && context.has("80")) {
                        context.error(Code.INVALID_INITIATION_RULE, tag, "Excel Type 02: QR tĩnh (11) không có kỳ cước (80).");
                    } else if (method.equals("12") && !context.has("80/01")) {
                        context.error(Code.INVALID_INITIATION_RULE, tag, "Excel Type 02: QR động (12) cần kỳ cước 80/01.");
                    }
                }
                default -> { }
            }
        }
    }
}
