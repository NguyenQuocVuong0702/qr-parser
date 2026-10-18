package com.vnpay.qrparser.text;

/** Kiểm tra cặp surrogate của UTF-16, không chuẩn hóa hoặc thay đổi dữ liệu. */
public final class UnicodeText {

    private UnicodeText() {
    }

    public static boolean containsUnpairedSurrogate(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    return true;
                }
                // Cặp high/low surrogate biểu diễn một code point; bỏ qua low surrogate.
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return true;
            }
        }
        return false;
    }
}
