package com.vnpay.qrparser.parser;

import com.vnpay.qrparser.model.TlvTag;
import com.vnpay.qrparser.model.QrValidationError.Code;
import com.vnpay.qrparser.text.UnicodeText;

import java.util.ArrayList;
import java.util.List;

public class TlvParser {

    public List<TlvTag> parse(String input) {
        // Chỉ kiểm tra đầu vào, không trim vì khoảng trắng có thể thuộc value.
        if (input == null || input.isBlank()) {
            throw new TlvParseException(Code.EMPTY_INPUT, "", null, "Dữ liệu TLV không được để trống.");
        }

        List<TlvTag> tags = new ArrayList<>();
        int position = 0;

        while (position < input.length()) {
            int remainingCharacters = input.length() - position;

            // Mỗi header cần 2 ký tự tag và 2 ký tự length.
            if (remainingCharacters < 4) {
                checkUnicode(input.substring(position), "", position);
                throw new TlvParseException(Code.INCOMPLETE_HEADER, "", position,
                        "Thiếu header tại vị trí " + position
                                + ": cần 4 ký tự, chỉ còn " + remainingCharacters + ".");
            }

            checkUnicode(input.substring(position, position + 4), "", position);
            String tag = input.substring(position, position + 2);
            String lengthText = input.substring(position + 2, position + 4);

            // Length chỉ chấp nhận hai chữ số ASCII từ 0 đến 9.
            for (int index = 0; index < lengthText.length(); index++) {
                char character = lengthText.charAt(index);
                if (character < '0' || character > '9') {
                    throw new TlvParseException(Code.NON_NUMERIC_LENGTH, tag, position,
                            "Length của tag " + tag + " tại vị trí " + position
                                    + " phải gồm 2 chữ số, nhận được '" + lengthText + "'.");
                }
            }

            int length = Integer.parseInt(lengthText);
            int valueStart = position + 4;
            int valueEnd = valueStart;
            int availableValueCharacters = 0;

            // Length đếm code point; valueEnd vẫn là chỉ số UTF-16 để dùng substring.
            while (availableValueCharacters < length && valueEnd < input.length()) {
                int codePoint = input.codePointAt(valueEnd);
                valueEnd += Character.charCount(codePoint);
                availableValueCharacters++;
            }
            String value = input.substring(valueStart, valueEnd);
            checkUnicode(value, tag, position);

            // Báo lỗi nếu đã hết dữ liệu mà vẫn chưa đọc đủ số code point khai báo.
            if (availableValueCharacters < length) {
                throw new TlvParseException(Code.TRUNCATED_VALUE, tag, position,
                        "Value của tag " + tag + " tại vị trí " + position
                                + " bị cắt ngắn: cần " + length
                                + " ký tự, chỉ còn " + availableValueCharacters + ".");
            }

            tags.add(new TlvTag(tag, length, value));

            // Chuyển đến cuối value theo UTF-16, kể cả khi value có emoji hoặc length 00.
            position = valueEnd;
        }

        return tags;
    }

    private void checkUnicode(String text, String tag, int headerOffset) {
        if (UnicodeText.containsUnpairedSurrogate(text)) {
            throw new TlvParseException(Code.INVALID_UNICODE, tag, headerOffset,
                    "Dữ liệu TLV chứa surrogate đơn không hợp lệ tại header ở vị trí " + headerOffset + ".");
        }
    }
}
