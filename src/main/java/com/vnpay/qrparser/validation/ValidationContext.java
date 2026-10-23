package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationError.Code;
import com.vnpay.qrparser.model.TlvNode;

import java.util.ArrayList;
import java.util.List;

/** Cây đã parse cùng vị trí UTF-16, dùng chung cho các bộ quy tắc. */
final class ValidationContext {
    private final QrType type;
    private final List<LocatedTag> tags = new ArrayList<>();
    private final List<QrValidationError> errors;

    ValidationContext(QrType type, List<TlvNode> nodes, List<QrValidationError> errors) {
        this.type = type;
        this.errors = errors;
        locate(nodes, "", 0);
    }

    QrType type() { return type; }
    List<LocatedTag> tags() { return List.copyOf(tags); }
    LocatedTag find(String path) {
        return tags.stream().filter(tag -> tag.path().equals(path)).findFirst().orElse(null);
    }
    boolean has(String path) { return find(path) != null; }

    void error(Code code, LocatedTag tag, String message) {
        errors.add(tag.error(code, message));
    }

    void missing(String path, String message) {
        errors.add(new QrValidationError(Code.MISSING_REQUIRED_TAG, path, null, null, message));
    }

    void requireChild(LocatedTag parent, String id, String message) {
        if (parent.node().children().stream().noneMatch(child -> child.tag().equals(id))) {
            missing(parent.path() + "/" + id, message);
        }
    }

    private void locate(List<TlvNode> nodes, String parentPath, int valueStart) {
        int localOffset = 0;
        for (TlvNode node : nodes) {
            String path = parentPath.isEmpty() ? node.tag() : parentPath + "/" + node.tag();
            int absoluteOffset = valueStart + localOffset;
            tags.add(new LocatedTag(path, node, parentPath.isEmpty() ? null : localOffset, absoluteOffset));
            locate(node.children(), path, absoluteOffset + 4);
            localOffset += 4 + node.value().length();
        }
    }

    record LocatedTag(String path, TlvNode node, Integer parentValueOffset, int absoluteOffset) {
        QrValidationError error(Code code, String message) {
            return new QrValidationError(code, path, parentValueOffset, absoluteOffset, message);
        }
    }
}
