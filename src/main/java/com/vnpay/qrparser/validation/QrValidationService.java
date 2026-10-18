package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.crc.EmvCrcValidator;
import com.vnpay.qrparser.interpreter.EmvQrInterpreter;
import com.vnpay.qrparser.model.CrcValidationResult;
import com.vnpay.qrparser.model.CrcValidationStatus;
import com.vnpay.qrparser.model.QrValidationError;
import com.vnpay.qrparser.model.QrValidationError.Code;
import com.vnpay.qrparser.model.QrValidationResult;
import com.vnpay.qrparser.model.QrValidationStatus;
import com.vnpay.qrparser.model.TlvNode;
import com.vnpay.qrparser.parser.NestedTlvParser;
import com.vnpay.qrparser.parser.TlvParseException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Điều phối kiểm tra theo Excel Type 01, không thay đổi dữ liệu QR. */
@Service
public class QrValidationService {

    // Tag 63 cũng là M trong Excel, nhưng được kiểm tra bởi CRC validator.
    private static final List<String> REQUIRED_ROOT_TAGS = List.of("00", "26", "52", "53", "58", "59", "60");
    private static final Map<String, Integer> FIXED_LENGTHS = Map.of("00", 2, "01", 2, "52", 4, "53", 3, "58", 2);
    private static final Set<String> NUMERIC_PATHS = Set.of("00", "01", "52", "53");
    private static final Map<String, Integer> MAX_LENGTHS = Map.ofEntries(
            Map.entry("26", 99), Map.entry("54", 13), Map.entry("59", 25),
            Map.entry("60", 15), Map.entry("61", 10), Map.entry("62", 99),
            Map.entry("26/00", 32), Map.entry("26/01", 13), Map.entry("62/01", 20),
            Map.entry("62/03", 20), Map.entry("62/07", 15), Map.entry("62/08", 19), Map.entry("62/09", 3));

    private final NestedTlvParser parser = new NestedTlvParser();
    private final EmvCrcValidator crcValidator = new EmvCrcValidator();
    private final EmvQrInterpreter interpreter = new EmvQrInterpreter();

    public QrValidationResult validate(String qr) {
        List<TlvNode> nodes;
        try {
            nodes = parser.parse(qr);
        } catch (TlvParseException exception) {
            QrValidationError error = exception.error();
            QrValidationStatus status = error.code() == Code.EMPTY_INPUT
                    ? QrValidationStatus.INVALID_INPUT : QrValidationStatus.INVALID_STRUCTURE;
            return result(status, error.message(), List.of(error), null, List.of());
        }

        List<LocatedTag> locations = new ArrayList<>();
        locateTags(nodes, "", 0, locations);
        List<QrValidationError> errors = new ArrayList<>();
        for (LocatedTag tag : locations) {
            if (!tag.node().tag().matches("[0-9]{2}")) {
                errors.add(at(Code.INVALID_TAG_ID, tag, "ID tag phải gồm đúng hai chữ số ASCII từ 00 đến 99."));
            }
        }
        if (!errors.isEmpty()) {
            return result(QrValidationStatus.INVALID_STRUCTURE, "QR có ID tag không hợp lệ.", errors, null, nodes);
        }

        CrcValidationResult crc = crcValidator.validate(qr);
        if (crc.status() == CrcValidationStatus.STRUCTURE_ERROR) {
            LocatedTag crcTag = find(locations, "63");
            errors.add(crcTag == null
                    ? new QrValidationError(crc.errorCode(), "63", null, null, crc.message())
                    : at(crc.errorCode(), crcTag, crc.message()));
            return result(QrValidationStatus.INVALID_STRUCTURE, crc.message(), errors, crc, nodes);
        }

        checkSpecification(locations, errors);
        if (crc.status() == CrcValidationStatus.MISMATCH) {
            errors.add(0, at(Code.CRC_MISMATCH, find(locations, "63"), crc.message()));
            return result(QrValidationStatus.CRC_MISMATCH, crc.message(), errors, crc, nodes);
        }
        if (!errors.isEmpty()) {
            return result(QrValidationStatus.SPEC_VIOLATION,
                    "QR vi phạm quy tắc của Excel Type 01.", errors, crc, nodes);
        }
        return result(QrValidationStatus.VALID,
                "QR hợp lệ trong phạm vi kiểm tra Excel Type 01.", errors, crc, nodes);
    }

    private void checkSpecification(List<LocatedTag> locations, List<QrValidationError> errors) {
        for (String path : REQUIRED_ROOT_TAGS) {
            if (find(locations, path) == null) {
                missing(path, "Excel Type 01 yêu cầu tag " + path + " (Presence=M).", errors);
            }
        }
        for (LocatedTag tag : locations) {
            String path = tag.path();
            String value = tag.node().value();
            int length = tag.node().length();
            Integer fixedLength = FIXED_LENGTHS.get(path);
            Integer maxLength = MAX_LENGTHS.get(path);
            if (fixedLength != null && length != fixedLength) {
                errors.add(at(Code.INVALID_FIELD_LENGTH, tag,
                        "Excel Type 01 yêu cầu length của " + path + " bằng " + fixedLength + ", nhận được " + length + "."));
            }
            if (maxLength != null && length > maxLength) {
                errors.add(at(Code.INVALID_FIELD_LENGTH, tag,
                        "Excel Type 01 giới hạn length của " + path + " tối đa " + maxLength + ", nhận được " + length + "."));
            }
            if (NUMERIC_PATHS.contains(path) && !value.matches("[0-9]+")) {
                errors.add(at(Code.INVALID_FIELD_VALUE, tag,
                        "Excel Type 01 quy định " + path + " có định dạng N, chỉ chứa chữ số ASCII."));
            }
            if (path.equals("58") && !value.matches("[A-Z]{2}")) {
                errors.add(at(Code.INVALID_FIELD_VALUE, tag,
                        "Excel Type 01 yêu cầu tag 58 có dạng ISO 3166-1 alpha-2: hai chữ cái Latin viết hoa."));
            }
            if (path.equals("00") && !value.equals("01")) {
                errors.add(at(Code.INVALID_FIELD_VALUE, tag, "Excel Type 01 yêu cầu value của tag 00 bằng 01."));
            }
            if (path.equals("01")) {
                checkInitiationMethod(tag, locations, errors);
            }
            if (path.equals("26") && !hasChild(tag.node(), "00")) {
                missing("26/00", "Excel Type 01 yêu cầu GUID 26/00 trong template 26 (ô J15=M).", errors);
            }
            // Quy tắc này thuộc Excel Type 01!J31; EMVCo MPM v1.1 coi 62/09 là optional.
            if (path.equals("62") && !hasChild(tag.node(), "09")) {
                missing("62/09", "Excel Type 01 (ô J31=M) yêu cầu 62/09 khi có tag 62;"
                        + " EMVCo Merchant-Presented Mode v1.1 coi trường này là optional.", errors);
            }
            if (path.equals("62/09") && !value.matches("[AME]+")) {
                errors.add(at(Code.INVALID_FIELD_VALUE, tag,
                        "Excel Type 01 quy định 62/09 chỉ chứa các ký tự A, M, E (ô K31)."));
            }
        }
    }

    private void checkInitiationMethod(LocatedTag tag, List<LocatedTag> locations, List<QrValidationError> errors) {
        String method = tag.node().value();
        boolean hasAmount = find(locations, "54") != null;
        boolean hasBill = find(locations, "62/01") != null;
        // Điều kiện QR tĩnh/động lấy từ Excel Type 01!F5/L5, không phải quy tắc EMVCo chung.
        if (!method.equals("11") && !method.equals("12")) {
            errors.add(at(Code.INVALID_FIELD_VALUE, tag, "Excel Type 01 chỉ định value của tag 01 là 11 hoặc 12."));
        } else if (method.equals("11") && (hasAmount || hasBill)) {
            errors.add(at(Code.INVALID_INITIATION_RULE, tag,
                    "Theo Excel Type 01, QR tĩnh (01=11) không có tag 54 và 62/01."));
        } else if (method.equals("12") && (!hasAmount || !hasBill)) {
            errors.add(at(Code.INVALID_INITIATION_RULE, tag,
                    "Theo Excel Type 01, QR động (01=12) cần cả tag 54 và 62/01."));
        }
    }

    private void locateTags(List<TlvNode> nodes, String parentPath, int valueStart, List<LocatedTag> locations) {
        int localOffset = 0;
        for (TlvNode node : nodes) {
            String path = parentPath.isEmpty() ? node.tag() : parentPath + "/" + node.tag();
            int absoluteOffset = valueStart + localOffset;
            locations.add(new LocatedTag(path, node, parentPath.isEmpty() ? null : localOffset, absoluteOffset));
            // Vị trí value con = vị trí header cha + 4; không đếm byte UTF-8.
            locateTags(node.children(), path, absoluteOffset + 4, locations);
            // Dùng độ dài chuỗi gốc theo UTF-16 để định vị header kế tiếp.
            localOffset += 4 + node.value().length();
        }
    }

    private LocatedTag find(List<LocatedTag> locations, String path) {
        for (LocatedTag tag : locations) {
            if (tag.path().equals(path)) {
                return tag;
            }
        }
        return null;
    }

    private boolean hasChild(TlvNode parent, String id) {
        for (TlvNode child : parent.children()) {
            if (child.tag().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private QrValidationError at(Code code, LocatedTag tag, String message) {
        return new QrValidationError(code, tag.path(), tag.parentValueOffset(), tag.absoluteOffset(), message);
    }

    private void missing(String path, String message, List<QrValidationError> errors) {
        // Tag không tồn tại nên không gán offset của tag cha hay vị trí dự đoán.
        errors.add(new QrValidationError(Code.MISSING_REQUIRED_TAG, path, null, null, message));
    }

    private QrValidationResult result(QrValidationStatus status, String message, List<QrValidationError> errors,
                                       CrcValidationResult crc, List<TlvNode> nodes) {
        return new QrValidationResult(status, message, errors, crc, interpreter.interpret(nodes));
    }

    private record LocatedTag(String path, TlvNode node, Integer parentValueOffset, int absoluteOffset) {
    }
}
