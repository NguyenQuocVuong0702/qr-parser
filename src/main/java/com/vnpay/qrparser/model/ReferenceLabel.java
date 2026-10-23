package com.vnpay.qrparser.model;

import java.time.DateTimeException;
import java.time.MonthDay;
import java.util.Optional;

/** Type 03/04!K28: prefix + yymmddhhmm + mã. Không gán thế kỷ, múi giờ hay trạng thái hết hạn. */
public record ReferenceLabel(String prefix, String deadlineText, String identifier) {

    public static Optional<ReferenceLabel> parse(String value, QrType type) {
        String prefix = switch (type) {
            case TYPE_03 -> "02";
            case TYPE_04 -> "01";
            default -> null;
        };
        if (prefix == null || value == null || value.codePointCount(0, value.length()) > 25
                || value.length() <= 12 || !value.startsWith(prefix)
                || !value.substring(2, 12).matches("[0-9]{10}")) {
            return Optional.empty();
        }
        String deadline = value.substring(2, 12);
        try {
            int year = Integer.parseInt(deadline.substring(0, 2));
            MonthDay date = MonthDay.of(Integer.parseInt(deadline.substring(2, 4)),
                    Integer.parseInt(deadline.substring(4, 6)));
            // Với yy khác 00, tính nhuận không phụ thuộc thế kỷ vì thế kỷ là bội của 100.
            // yy=00 còn phụ thuộc thế kỷ: giữ việc chấp nhận 29/02 như trước, chưa gán thế kỷ.
            if (date.equals(MonthDay.of(2, 29)) && year != 0 && year % 4 != 0) {
                return Optional.empty();
            }
            if (Integer.parseInt(deadline.substring(6, 8)) > 23
                    || Integer.parseInt(deadline.substring(8, 10)) > 59) {
                return Optional.empty();
            }
        } catch (DateTimeException exception) {
            return Optional.empty();
        }
        return Optional.of(new ReferenceLabel(prefix, deadline, value.substring(12)));
    }

    public String display(QrType type) {
        return (type == QrType.TYPE_03 ? "Mã sản phẩm: " : "Mã đơn hàng: ") + identifier
                + "; thời hạn thanh toán (yymmddhhmm): " + deadlineText;
    }
}
