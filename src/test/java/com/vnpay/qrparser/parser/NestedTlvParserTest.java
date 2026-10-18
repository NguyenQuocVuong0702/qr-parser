package com.vnpay.qrparser.parser;

import com.vnpay.qrparser.model.TlvNode;
import com.vnpay.qrparser.model.QrValidationError.Code;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NestedTlvParserTest {

    private final NestedTlvParser parser = new NestedTlvParser();

    @Test
    void parsesTag26AndPreservesChildOrder() {
        assertEquals(List.of(
                new TlvNode("26", 12, "0002AB0102CD", List.of(
                        new TlvNode("00", 2, "AB", List.of()),
                        new TlvNode("01", 2, "CD", List.of())
                ))
        ), parser.parse("26120002AB0102CD"));
    }

    @Test
    void parsesTag62AndPreservesChildOrder() {
        assertEquals(List.of(
                new TlvNode("62", 13, "0503XYZ0102AB", List.of(
                        new TlvNode("05", 3, "XYZ", List.of()),
                        new TlvNode("01", 2, "AB", List.of())
                ))
        ), parser.parse("62130503XYZ0102AB"));
    }

    @Test
    void parsesBothTemplatesAndPreservesRootOrder() {
        assertEquals(List.of(
                new TlvNode("26", 12, "0002AB0102CD", List.of(
                        new TlvNode("00", 2, "AB", List.of()),
                        new TlvNode("01", 2, "CD", List.of())
                )),
                new TlvNode("62", 7, "0503XYZ", List.of(
                        new TlvNode("05", 3, "XYZ", List.of())
                )),
                new TlvNode("53", 3, "704", List.of())
        ), parser.parse("26120002AB0102CD62070503XYZ5303704"));
    }

    @Test
    void parsesShortExampleWithoutTemplates() {
        assertEquals(List.of(
                new TlvNode("00", 2, "01", List.of()),
                new TlvNode("01", 2, "11", List.of()),
                new TlvNode("53", 3, "704", List.of())
        ), parser.parse("0002010102115303704"));
    }

    @Test
    void parsesActualQrAndPreservesRootAndChildOrder() {
        String input = "00020101021126300010A00000077501120682000002105204549953037045802VN5911DONG QUE 796007LAMDONG62270311DONG QUE 790708DONGQUE163045E14";

        List<TlvNode> nodes = parser.parse(input);

        assertEquals(10, nodes.size());
        // So sánh cả cây để kiểm tra tag, length, value và thứ tự các nút.
        assertEquals(List.of(
                new TlvNode("00", 2, "01", List.of()),
                new TlvNode("01", 2, "11", List.of()),
                new TlvNode("26", 30, "0010A0000007750112068200000210", List.of(
                        new TlvNode("00", 10, "A000000775", List.of()),
                        new TlvNode("01", 12, "068200000210", List.of())
                )),
                new TlvNode("52", 4, "5499", List.of()),
                new TlvNode("53", 3, "704", List.of()),
                new TlvNode("58", 2, "VN", List.of()),
                new TlvNode("59", 11, "DONG QUE 79", List.of()),
                new TlvNode("60", 7, "LAMDONG", List.of()),
                new TlvNode("62", 27, "0311DONG QUE 790708DONGQUE1", List.of(
                        new TlvNode("03", 11, "DONG QUE 79", List.of()),
                        new TlvNode("07", 8, "DONGQUE1", List.of())
                )),
                // Tag 63 được đọc như TLV thông thường, chưa xác thực CRC.
                new TlvNode("63", 4, "5E14", List.of())
        ), nodes);
    }

    @Test
    void keepsOtherRootTagsAsLeaves() {
        assertEquals(List.of(
                new TlvNode("27", 3, "XYZ", List.of()),
                new TlvNode("59", 5, " A B ", List.of())
        ), parser.parse("2703XYZ5905 A B "));
    }

    @Test
    void keepsChildTags26And62AsLeavesWithoutParsingTheirValues() {
        assertEquals(List.of(
                new TlvNode("26", 10, "2601X6201Y", List.of(
                        new TlvNode("26", 1, "X", List.of()),
                        new TlvNode("62", 1, "Y", List.of())
                ))
        ), parser.parse("26102601X6201Y"));
    }

    @Test
    void preservesSpacesInParentAndChildValues() {
        assertEquals(List.of(
                new TlvNode("62", 9, "0505 A B ", List.of(
                        new TlvNode("05", 5, " A B ", List.of())
                )),
                new TlvNode("53", 3, "704", List.of())
        ), parser.parse("62090505 A B 5303704"));
    }

    @Test
    void acceptsChildValueContainingOnlySpaces() {
        assertEquals(List.of(
                new TlvNode("62", 7, "0503   ", List.of(
                        new TlvNode("05", 3, "   ", List.of())
                ))
        ), parser.parse("62070503   "));
    }

    @Test
    void acceptsZeroLengthChildValueAndContinuesToNextChild() {
        assertEquals(List.of(
                new TlvNode("26", 10, "00000102AB", List.of(
                        new TlvNode("00", 0, "", List.of()),
                        new TlvNode("01", 2, "AB", List.of())
                ))
        ), parser.parse("261000000102AB"));
    }

    @Test
    void copiesChildrenSoChangesToOriginalListDoNotAffectNode() {
        TlvNode firstChild = new TlvNode("00", 2, "AB", List.of());
        TlvNode secondChild = new TlvNode("01", 2, "CD", List.of());
        List<TlvNode> originalChildren = new ArrayList<>(List.of(firstChild, secondChild));
        TlvNode parent = new TlvNode("26", 12, "0002AB0102CD", originalChildren);

        originalChildren.clear();

        assertEquals(List.of(firstChild, secondChild), parent.children());
    }

    @Test
    void doesNotAllowChangesThroughChildrenAccessor() {
        TlvNode parent = parser.parse("26060002AB").get(0);
        TlvNode anotherChild = new TlvNode("01", 2, "CD", List.of());

        assertThrows(UnsupportedOperationException.class, () -> parent.children().add(anotherChild));
        assertThrows(UnsupportedOperationException.class, () -> parent.children().clear());
    }

    @Test
    void reportsMalformedRootInputUsingExistingParser() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("000"));

        assertEquals("Thiếu header tại vị trí 0: cần 4 ký tự, chỉ còn 3.", exception.getMessage());
    }

    // Mỗi test bên dưới chạy hai lần, một lần cho tag cha 26 và một lần cho 62.
    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsParentForIncompleteChildHeader(String parentTag) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(parentTag + "03000"));

        String originalMessage = "Thiếu header tại vị trí 0: cần 4 ký tự, chỉ còn 3.";
        assertEquals("TLV con của tag cha " + parentTag + " không hợp lệ: " + originalMessage,
                exception.getMessage());
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertEquals(originalMessage, exception.getCause().getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsParentForNonNumericChildLength(String parentTag) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(parentTag + "0600A2AB"));

        assertEquals("TLV con của tag cha " + parentTag
                        + " không hợp lệ: Length của tag 00 tại vị trí 0"
                        + " phải gồm 2 chữ số, nhận được 'A2'.",
                exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsParentForTruncatedValueAfterValidChild(String parentTag) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(parentTag + "110002AB0102C"));

        assertEquals("TLV con của tag cha " + parentTag
                        + " không hợp lệ: Value của tag 01 tại vị trí 6"
                        + " bị cắt ngắn: cần 2 ký tự, chỉ còn 1.",
                exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsParentForEmptyTemplateValue(String parentTag) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(parentTag + "00"));

        assertEquals("TLV con của tag cha " + parentTag
                        + " không hợp lệ: Dữ liệu TLV không được để trống.",
                exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void reportsParentForBlankTemplateValue(String parentTag) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(parentTag + "03   "));

        assertEquals("TLV con của tag cha " + parentTag
                        + " không hợp lệ: Dữ liệu TLV không được để trống.",
                exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void parsesSupplementaryCodePointsInTemplateValues(String parentTag) {
        List<TlvNode> nodes = parser.parse("9901\uD83D\uDE00" + parentTag + "110001\uD83D\uDE000102AB" + "6304ABCD");
        TlvNode parent = nodes.get(1);

        assertEquals(11, parent.length());
        assertEquals("0001\uD83D\uDE000102AB", parent.value());
        assertEquals(List.of("00", "01"), parent.children().stream().map(TlvNode::tag).toList());
        assertEquals("\uD83D\uDE00", parent.children().getFirst().value());
        assertEquals(1, parent.children().getFirst().length());
        assertEquals("AB", parent.children().get(1).value());
        assertEquals("63", nodes.get(2).tag());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void keepsNestedErrorOffsetsInUtf16AfterSupplementaryCharacters(String parentTag) {
        TlvParseException exception = assertThrows(TlvParseException.class,
                () -> parser.parse("9901\uD83D\uDE00" + parentTag + "100001\uD83D\uDE00015AX"));

        assertEquals(Code.NON_NUMERIC_LENGTH, exception.error().code());
        assertEquals(parentTag + "/01", exception.error().tagPath());
        assertEquals(6, exception.error().parentValueOffset());
        assertEquals(16, exception.error().absoluteOffset());
    }
}
