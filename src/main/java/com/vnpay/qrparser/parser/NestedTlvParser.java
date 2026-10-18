package com.vnpay.qrparser.parser;

import com.vnpay.qrparser.model.TlvNode;
import com.vnpay.qrparser.model.TlvTag;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationError.Code;

import java.util.ArrayList;
import java.util.List;

public class NestedTlvParser {

    private final TlvParser tlvParser = new TlvParser();

    public List<TlvNode> parse(String input) {
        // Dùng parser cấp 1 để đọc toàn bộ tag ngoài cùng.
        List<TlvTag> tags = tlvParser.parse(input);
        List<TlvNode> nodes = new ArrayList<>();
        int parentHeaderOffset = 0;

        for (TlvTag tag : tags) {
            List<TlvNode> children = List.of();

            // Chỉ nhận biết template 26 và 62 ở cấp ngoài cùng.
            if ("26".equals(tag.tag()) || "62".equals(tag.tag())) {
                children = parseChildren(tag, parentHeaderOffset);
            }

            // Giữ nguyên tag, length và value; children là thông tin bổ sung.
            nodes.add(new TlvNode(tag.tag(), tag.length(), tag.value(), children));
            // Offset public đếm UTF-16; declared length của value đếm code point.
            parentHeaderOffset += 4 + tag.value().length();
        }

        return nodes;
    }

    private List<TlvNode> parseChildren(TlvTag parent, int parentHeaderOffset) {
        List<TlvTag> childTags;

        try {
            // Đọc trực tiếp value gốc, không trim và không viết lại thuật toán TLV.
            childTags = tlvParser.parse(parent.value());
        } catch (TlvParseException exception) {
            // Bổ sung tag cha và giữ lỗi gốc trong cause.
            // Vị trí trong lỗi gốc được tính từ đầu value của tag cha.
            QrValidationError childError = exception.error();
            Integer relativeOffset = childError.absoluteOffset();
            Integer absoluteOffset = relativeOffset == null ? null : parentHeaderOffset + 4 + relativeOffset;
            String path = childError.tagPath().isEmpty()
                    ? parent.tag() : parent.tag() + "/" + childError.tagPath();
            Code code = childError.code() == Code.EMPTY_INPUT ? Code.EMPTY_TEMPLATE : childError.code();
            String message = "TLV con của tag cha " + parent.tag()
                    + " không hợp lệ: " + exception.getMessage();
            // Offset gốc của lỗi con tính từ đầu value; chỉ quy đổi khi đã biết.
            throw new TlvParseException(
                    new QrValidationError(code, path, relativeOffset, absoluteOffset, message), exception);
        }

        List<TlvNode> children = new ArrayList<>();
        for (TlvTag child : childTags) {
            // Các tag con là nút lá, kể cả khi ID của chúng là 26 hoặc 62.
            children.add(new TlvNode(child.tag(), child.length(), child.value(), List.of()));
        }

        return children;
    }
}
