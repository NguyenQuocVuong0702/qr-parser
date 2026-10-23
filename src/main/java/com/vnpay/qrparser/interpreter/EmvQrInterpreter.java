package com.vnpay.qrparser.interpreter;

import com.vnpay.qrparser.metadata.EmvTagMetadataRegistry;
import com.vnpay.qrparser.model.InterpretedTlvNode;
import com.vnpay.qrparser.model.TagMetadata;
import com.vnpay.qrparser.model.TlvNode;
import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.model.ReferenceLabel;

import java.util.ArrayList;
import java.util.List;

public class EmvQrInterpreter {

    private final EmvTagMetadataRegistry registry = new EmvTagMetadataRegistry();

    public List<InterpretedTlvNode> interpret(List<TlvNode> nodes) {
        return interpret(nodes, QrType.TYPE_01);
    }

    public List<InterpretedTlvNode> interpret(List<TlvNode> nodes, QrType type) {
        List<InterpretedTlvNode> interpretedNodes = new ArrayList<>();
        for (TlvNode node : nodes) {
            interpretedNodes.add(interpretNode(node, node.tag(), type));
        }
        return interpretedNodes;
    }

    private InterpretedTlvNode interpretNode(TlvNode node, String path, QrType type) {
        TagMetadata metadata = registry.lookup(type, path);

        // Chỉ giải nghĩa các mã đã biết. Value gốc luôn được giữ nguyên.
        String displayValue = metadata.valueMeanings().getOrDefault(node.value(), node.value());
        if (path.equals("62/05")) {
            displayValue = ReferenceLabel.parse(node.value(), type).map(label -> label.display(type)).orElse(displayValue);
        }

        List<InterpretedTlvNode> children = new ArrayList<>();
        for (TlvNode child : node.children()) {
            // Mang theo toàn bộ đường dẫn, ví dụ 26/01 hoặc 62/01.
            String childPath = path + "/" + child.tag();
            children.add(interpretNode(child, childPath, type));
        }

        // Tạo cây kết quả mới; không thay đổi cây do parser trả về.
        return new InterpretedTlvNode(node.tag(), node.length(), node.value(),
                metadata.name(), metadata.description(), displayValue, children);
    }
}
