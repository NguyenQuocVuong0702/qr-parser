package com.vnpay.qrparser.interpreter;

import com.vnpay.qrparser.metadata.EmvTagMetadataRegistry;
import com.vnpay.qrparser.model.InterpretedTlvNode;
import com.vnpay.qrparser.model.TagMetadata;
import com.vnpay.qrparser.model.TlvNode;
import com.vnpay.qrparser.parser.NestedTlvParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmvQrInterpreterTest {

    private final NestedTlvParser parser = new NestedTlvParser();
    private final EmvQrInterpreter interpreter = new EmvQrInterpreter();

    @Test
    void interpretsStaticQrWithoutChangingValue() {
        InterpretedTlvNode node = interpreter.interpret(parser.parse("010211")).get(0);

        assertEquals("Point of Initiation Method", node.name());
        assertEquals("11", node.value());
        assertEquals("QR tĩnh", node.displayValue());
    }

    @Test
    void interpretsDynamicQrWithoutChangingValue() {
        InterpretedTlvNode node = interpreter.interpret(parser.parse("010212")).get(0);

        assertEquals("12", node.value());
        assertEquals("QR động", node.displayValue());
    }

    @Test
    void interpretsPayloadFormatVersion() {
        InterpretedTlvNode node = interpreter.interpret(parser.parse("000201")).get(0);

        assertEquals("Payload Format Indicator", node.name());
        assertEquals("01", node.value());
        assertEquals("Phiên bản dữ liệu 01", node.displayValue());
    }

    @Test
    void interpretsVndCurrencyWithoutChangingValue() {
        InterpretedTlvNode node = interpreter.interpret(parser.parse("5303704")).get(0);

        assertEquals("Transaction Currency Code", node.name());
        assertEquals("704", node.value());
        assertEquals("VND", node.displayValue());
    }

    @Test
    void preservesSpacesInMerchantName() {
        InterpretedTlvNode node = interpreter.interpret(parser.parse("5905 A B ")).get(0);

        assertEquals("Merchant Name", node.name());
        assertEquals(" A B ", node.value());
        assertEquals(" A B ", node.displayValue());
    }

    @Test
    void distinguishesId01AtThreeDifferentPaths() {
        List<InterpretedTlvNode> nodes = interpreter.interpret(
                parser.parse("01021126060102116206010211"));

        InterpretedTlvNode root = nodes.get(0);
        InterpretedTlvNode merchantId = nodes.get(1).children().get(0);
        InterpretedTlvNode billNumber = nodes.get(2).children().get(0);

        assertEquals("Point of Initiation Method", root.name());
        assertEquals("QR tĩnh", root.displayValue());
        assertEquals("Merchant ID", merchantId.name());
        assertEquals("11", merchantId.displayValue());
        assertEquals("Bill Number", billNumber.name());
        assertEquals("11", billNumber.displayValue());
        assertEquals("11", root.value());
        assertEquals("11", merchantId.value());
        assertEquals("11", billNumber.value());
    }

    @Test
    void distinguishesRootId00FromGuid() {
        List<InterpretedTlvNode> nodes = interpreter.interpret(parser.parse("0002012606000201"));

        assertEquals("Payload Format Indicator", nodes.get(0).name());
        assertEquals("Phiên bản dữ liệu 01", nodes.get(0).displayValue());
        InterpretedTlvNode guid = nodes.get(1).children().get(0);
        assertEquals("Globally Unique Identifier", guid.name());
        assertEquals("01", guid.value());
        assertEquals("01", guid.displayValue());
    }

    @Test
    void distinguishesReservedRootId03FromStoreLabel() {
        List<InterpretedTlvNode> nodes = interpreter.interpret(parser.parse("0302AB62060302AB"));

        assertEquals("Merchant Account Information", nodes.get(0).name());
        assertEquals("Dành riêng cho Visa.", nodes.get(0).description());
        assertEquals("Store Label", nodes.get(1).children().get(0).name());
    }

    @Test
    void handlesUnknownRootTagSafely() {
        InterpretedTlvNode node = interpreter.interpret(parser.parse("9905 A B ")).get(0);

        assertEquals("99", node.tag());
        assertEquals(5, node.length());
        assertEquals("Unknown Tag", node.name());
        assertEquals(" A B ", node.value());
        assertEquals(" A B ", node.displayValue());
        assertEquals(List.of(), node.children());
    }

    @Test
    void doesNotUseRootCurrencyMetadataForUnknownChild() {
        InterpretedTlvNode child = interpreter.interpret(parser.parse("26075303704"))
                .get(0).children().get(0);

        assertEquals("Unknown Tag", child.name());
        assertEquals("704", child.value());
        assertEquals("704", child.displayValue());
    }

    @Test
    void doesNotUseRootVersionMetadataForUnknownChild() {
        InterpretedTlvNode child = interpreter.interpret(parser.parse("6206000201"))
                .get(0).children().get(0);

        assertEquals("Unknown Tag", child.name());
        assertEquals("01", child.value());
        assertEquals("01", child.displayValue());
    }

    @Test
    void usesEntirePathWhenInterpretingAnExistingDeeperTree() {
        // Tạo cây trực tiếp để kiểm tra đường dẫn; không mở rộng nested parser.
        TlvNode leaf = new TlvNode("03", 2, "AB", List.of());
        TlvNode middle = new TlvNode("62", 6, "0302AB", List.of(leaf));
        TlvNode root = new TlvNode("26", 10, "62060302AB", List.of(middle));

        InterpretedTlvNode result = interpreter.interpret(List.of(root)).get(0);

        assertEquals("Unknown Tag", result.children().get(0).name());
        assertEquals("Unknown Tag", result.children().get(0).children().get(0).name());
        assertEquals("AB", result.children().get(0).children().get(0).value());
    }

    @Test
    void preservesUnknownInitiationMethodValue() {
        InterpretedTlvNode node = interpreter.interpret(parser.parse("010299")).get(0);

        assertEquals("Point of Initiation Method", node.name());
        assertEquals("99", node.value());
        assertEquals("99", node.displayValue());
    }

    @Test
    void preservesCurrencyCodeWithoutAValueMapping() {
        InterpretedTlvNode node = interpreter.interpret(parser.parse("5303999")).get(0);

        assertEquals("Transaction Currency Code", node.name());
        assertEquals("999", node.value());
        assertEquals("999", node.displayValue());
    }

    @Test
    void preservesRootOrderIncludingRepeatedTags() {
        List<InterpretedTlvNode> nodes = interpreter.interpret(parser.parse("0102115303704010212"));

        assertEquals(3, nodes.size());
        assertEquals("QR tĩnh", nodes.get(0).displayValue());
        assertEquals("VND", nodes.get(1).displayValue());
        assertEquals("QR động", nodes.get(2).displayValue());
    }

    @Test
    void preservesChildOrderIncludingRepeatedTagsAndSpaces() {
        List<InterpretedTlvNode> children = interpreter.interpret(parser.parse("62160701X0302 A0701Y"))
                .get(0).children();

        assertEquals(3, children.size());
        assertEquals("Terminal Label", children.get(0).name());
        assertEquals("X", children.get(0).value());
        assertEquals("Store Label", children.get(1).name());
        assertEquals(" A", children.get(1).value());
        assertEquals(" A", children.get(1).displayValue());
        assertEquals("Terminal Label", children.get(2).name());
        assertEquals("Y", children.get(2).value());
    }

    @Test
    void recognizesOtherAdditionalFieldsFromType01() {
        List<InterpretedTlvNode> children = interpreter.interpret(parser.parse("62100801X0901M"))
                .get(0).children();

        assertEquals("Purpose of Transaction", children.get(0).name());
        assertEquals("Additional Consumer Data Request", children.get(1).name());
        assertEquals("M", children.get(1).value());
        assertTrue(children.get(1).description().contains("M = số điện thoại"));
    }

    @Test
    void keepsOriginalTreeUnchanged() {
        List<TlvNode> source = parser.parse("01021126060002015303704");
        List<TlvNode> expectedSource = List.of(
                new TlvNode("01", 2, "11", List.of()),
                new TlvNode("26", 6, "000201", List.of(new TlvNode("00", 2, "01", List.of()))),
                new TlvNode("53", 3, "704", List.of()));

        List<InterpretedTlvNode> result = interpreter.interpret(source);

        assertEquals(expectedSource, source);
        assertRawTreePreserved(source, result);
    }

    @Test
    void handlesEmptyTree() {
        assertEquals(List.of(), interpreter.interpret(List.of()));
    }

    @Test
    void copiesAndProtectsMetadataValueMeanings() {
        Map<String, String> originalMeanings = new HashMap<>();
        originalMeanings.put("11", "QR tĩnh");
        TagMetadata metadata = new TagMetadata("Example", "Example description", originalMeanings);

        originalMeanings.put("11", "Changed");

        assertEquals("QR tĩnh", metadata.valueMeanings().get("11"));
        assertThrows(UnsupportedOperationException.class,
                () -> metadata.valueMeanings().put("12", "QR động"));
    }

    @Test
    void protectsValueMeaningsReturnedByRegistry() {
        TagMetadata metadata = new EmvTagMetadataRegistry().lookup("01");

        assertThrows(UnsupportedOperationException.class, () -> metadata.valueMeanings().clear());
        assertEquals(Map.of("11", "QR tĩnh", "12", "QR động"), metadata.valueMeanings());
    }

    @Test
    void copiesChildrenOfInterpretedNode() {
        InterpretedTlvNode child = interpreter.interpret(parser.parse("000201")).get(0);
        List<InterpretedTlvNode> originalChildren = new ArrayList<>(List.of(child));
        InterpretedTlvNode parent = new InterpretedTlvNode("26", 6, "000201",
                "Merchant Account Information", "Thông tin tài khoản Merchant.", "000201", originalChildren);

        originalChildren.clear();

        assertEquals(List.of(child), parent.children());
    }

    @Test
    void protectsChildrenReturnedByInterpreter() {
        InterpretedTlvNode parent = interpreter.interpret(parser.parse("2606000201")).get(0);

        assertThrows(UnsupportedOperationException.class, () -> parent.children().clear());
    }

    @ParameterizedTest
    @CsvSource({
            "26, Merchant Account Information",
            "52, Merchant Category Code",
            "54, Transaction Amount",
            "58, Country Code",
            "59, Merchant Name",
            "60, Merchant City",
            "61, Postal Code",
            "62, Additional Data Field",
            "63, CRC"
    })
    void recognizesOtherRootDefinitionsFromType01(String tag, String expectedName) {
        // Interpreter nhận cây đã có; test này kiểm tra metadata độc lập với parser.
        TlvNode source = new TlvNode(tag, 1, "X", List.of());
        InterpretedTlvNode result = interpreter.interpret(List.of(source)).get(0);

        assertEquals(expectedName, result.name());
        assertEquals("X", result.value());
        assertEquals("X", result.displayValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"02", "25", "27", "51"})
    void recognizesReservedRangesFromType01(String tag) {
        InterpretedTlvNode result = interpreter.interpret(parser.parse(tag + "01X")).get(0);

        assertEquals("Merchant Account Information", result.name());
        assertEquals("X", result.value());
        assertEquals(List.of(), result.children());
    }

    @Test
    void leavesTagUndefinedWhenItIsAbsentFromType01() {
        InterpretedTlvNode result = interpreter.interpret(parser.parse("55011")).get(0);

        assertEquals("Unknown Tag", result.name());
        assertEquals("1", result.value());
    }

    @Test
    void interpretsCompleteQrFromTheProblem() {
        String input = "00020101021126300010A00000077501120682000002105204549953037045802VN5911DONG QUE 796007LAMDONG62270311DONG QUE 790708DONGQUE163045E14";
        List<TlvNode> source = parser.parse(input);

        List<InterpretedTlvNode> nodes = interpreter.interpret(source);

        assertEquals(10, nodes.size());
        assertRawTreePreserved(source, nodes);
        assertEquals(List.of("Payload Format Indicator", "Point of Initiation Method",
                        "Merchant Account Information", "Merchant Category Code", "Transaction Currency Code",
                        "Country Code", "Merchant Name", "Merchant City", "Additional Data Field", "CRC"),
                nodes.stream().map(InterpretedTlvNode::name).toList());
        assertEquals("QR tĩnh", nodes.get(1).displayValue());
        assertEquals("VND", nodes.get(4).displayValue());
        assertEquals("DONG QUE 79", nodes.get(6).value());
        assertEquals("DONG QUE 79", nodes.get(6).displayValue());

        List<InterpretedTlvNode> merchantChildren = nodes.get(2).children();
        assertEquals(2, merchantChildren.size());
        assertEquals("00", merchantChildren.get(0).tag());
        assertEquals("Globally Unique Identifier", merchantChildren.get(0).name());
        assertEquals("A000000775", merchantChildren.get(0).displayValue());
        assertEquals("01", merchantChildren.get(1).tag());
        assertEquals("Merchant ID", merchantChildren.get(1).name());
        assertEquals("068200000210", merchantChildren.get(1).displayValue());

        List<InterpretedTlvNode> additionalChildren = nodes.get(8).children();
        assertEquals(2, additionalChildren.size());
        assertEquals("03", additionalChildren.get(0).tag());
        assertEquals("Store Label", additionalChildren.get(0).name());
        assertEquals("DONG QUE 79", additionalChildren.get(0).displayValue());
        assertEquals("07", additionalChildren.get(1).tag());
        assertEquals("Terminal Label", additionalChildren.get(1).name());
        assertEquals("DONGQUE1", additionalChildren.get(1).displayValue());
        assertEquals("5E14", nodes.get(9).value());
        assertEquals("5E14", nodes.get(9).displayValue());
    }

    @Test
    void describesCrcWithoutClaimingItHasNotBeenValidated() {
        InterpretedTlvNode crc = interpreter.interpret(parser.parse("6304ABCD")).getFirst();

        assertTrue(crc.description().contains("CRC validator"));
        assertFalse(crc.description().contains("chưa xác thực"));
        assertEquals("ABCD", crc.value());
    }

    private void assertRawTreePreserved(List<TlvNode> source, List<InterpretedTlvNode> interpreted) {
        assertEquals(source.size(), interpreted.size());
        for (int index = 0; index < source.size(); index++) {
            TlvNode originalNode = source.get(index);
            InterpretedTlvNode interpretedNode = interpreted.get(index);
            assertEquals(originalNode.tag(), interpretedNode.tag());
            assertEquals(originalNode.length(), interpretedNode.length());
            assertEquals(originalNode.value(), interpretedNode.value());
            assertRawTreePreserved(originalNode.children(), interpretedNode.children());
        }
    }
}
