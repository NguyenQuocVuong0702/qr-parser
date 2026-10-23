package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.model.QrValidationError.Code;

/** Type 01!F5/L5: tĩnh không có cả hai trường, động phải có cả hai. */
final class OfflineQrRules implements TypeSpecificRules {
    public void validate(ValidationContext context) {
        for (var tag : context.tags()) {
            switch (tag.path()) {
                case "54" -> CommonQrRules.maximum(context, tag, 13);
                case "62/01" -> CommonQrRules.maximum(context, tag, 20);
                case "01" -> {
                    String method = tag.node().value();
                    if (!method.equals("11") && !method.equals("12")) {
                        context.error(Code.INVALID_FIELD_VALUE, tag, "Excel Type 01 chỉ định value của tag 01 là 11 hoặc 12.");
                    } else if (method.equals("11") && (context.has("54") || context.has("62/01"))) {
                        context.error(Code.INVALID_INITIATION_RULE, tag,
                                "Theo Excel Type 01, QR tĩnh (01=11) không có tag 54 và 62/01.");
                    } else if (method.equals("12") && (!context.has("54") || !context.has("62/01"))) {
                        context.error(Code.INVALID_INITIATION_RULE, tag,
                                "Theo Excel Type 01, QR động (01=12) cần cả tag 54 và 62/01.");
                    }
                }
                default -> { }
            }
        }
    }
}
