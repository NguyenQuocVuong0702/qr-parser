package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.model.QrValidationError.Code;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Chỉ những quy tắc giống nhau đã đối chiếu trên cả bốn sheet. */
final class CommonQrRules {
    private static final List<String> REQUIRED = List.of("00", "26", "52", "53", "58", "59", "60");
    private static final Map<String, Integer> FIXED = Map.of("00", 2, "01", 2, "52", 4, "53", 3, "58", 2);
    private static final Set<String> NUMERIC = Set.of("00", "01", "52", "53");
    private static final Map<String, Integer> MAX = Map.ofEntries(
            Map.entry("26", 99), Map.entry("59", 25), Map.entry("60", 15), Map.entry("61", 10),
            Map.entry("62", 99), Map.entry("26/00", 32), Map.entry("26/01", 13),
            Map.entry("62/03", 20), Map.entry("62/07", 15), Map.entry("62/08", 19));

    void validate(ValidationContext context) {
        for (String path : REQUIRED) {
            if (!context.has(path)) {
                context.missing(path, "Excel " + context.type().sheetName() + " yêu cầu tag " + path + " (Presence=M).");
            }
        }
        for (var tag : context.tags()) {
            String path = tag.path();
            String value = tag.node().value();
            Integer fixed = FIXED.get(path);
            if (fixed != null && tag.node().length() != fixed) {
                context.error(Code.INVALID_FIELD_LENGTH, tag, "Excel " + context.type().sheetName()
                        + " yêu cầu length của " + path + " bằng " + fixed + ".");
            }
            Integer max = MAX.get(path);
            if (max != null) { maximum(context, tag, max); }
            if (NUMERIC.contains(path) && !value.matches("[0-9]+")) {
                context.error(Code.INVALID_FIELD_VALUE, tag, "Excel " + context.type().sheetName()
                        + " quy định " + path + " có định dạng N, chỉ chứa chữ số ASCII.");
            }
            if (path.equals("58") && !value.matches("[A-Z]{2}")) {
                context.error(Code.INVALID_FIELD_VALUE, tag, "Mã quốc gia phải có hai chữ cái Latin viết hoa (ISO 3166-1 alpha-2).");
            }
            if (path.equals("00") && !value.equals("01")) {
                context.error(Code.INVALID_FIELD_VALUE, tag, "Payload Format Indicator phải bằng 01.");
            }
            if (path.equals("26")) {
                context.requireChild(tag, "00", "Excel " + context.type().sheetName() + " yêu cầu GUID 26/00 (ô J15=M).");
            }
            if (path.equals("62") && context.type() != QrType.TYPE_02) {
                context.requireChild(tag, "09", "Excel " + context.type().sheetName()
                        + " (ô J31=M) yêu cầu 62/09 khi có tag 62; EMVCo Merchant-Presented Mode v1.1 coi trường này là optional.");
            }
            if (path.equals("62/09") && context.type() != QrType.TYPE_02) {
                maximum(context, tag, 3);
                if (!value.matches("[AME]+")) {
                    context.error(Code.INVALID_FIELD_VALUE, tag, "Excel " + context.type().sheetName()
                            + " quy định 62/09 chỉ chứa các ký tự A, M, E (ô K31).");
                }
            }
        }
    }

    static void maximum(ValidationContext context, ValidationContext.LocatedTag tag, int max) {
        if (tag.node().length() > max) {
            context.error(Code.INVALID_FIELD_LENGTH, tag, "Excel " + context.type().sheetName()
                    + " giới hạn length của " + tag.path() + " tối đa " + max + ".");
        }
    }
}
