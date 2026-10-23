package com.vnpay.qrparser.validation;

import com.vnpay.qrparser.metadata.EmvTagMetadataRegistry;
import com.vnpay.qrparser.model.*;
import com.vnpay.qrparser.parser.NestedTlvParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.vnpay.qrparser.support.TestQrBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

class QrTypeValidationTest {
    private final QrValidationService service = new QrValidationService();
    private final EmvTagMetadataRegistry metadata = new EmvTagMetadataRegistry();

    @ParameterizedTest
    @EnumSource(QrType.class)
    void acceptsEachTypeWithItsOwnMetadataAndOriginalUnicode(QrType type) {
        var result = service.validate(crc(full(type)), type);
        assertEquals(QrValidationStatus.VALID, result.status(), result.errors().toString());
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        assertTrue(result.message().contains(type.sheetName()));
        var merchant = result.tags().stream().filter(t -> t.tag().equals("59")).findFirst().orElseThrow();
        assertEquals(" ĐỒNG QUÊ 😀 ", merchant.value());
        assertEquals(12, merchant.length());
        var additional = result.tags().stream().filter(t -> t.tag().equals("62")).findFirst().orElseThrow();
        assertTrue(additional.children().stream().noneMatch(t -> t.name().equals("Unknown Tag")));
        String reconstructed = result.tags().stream().map(t -> tlv(t.tag(), t.value())).reduce("", String::concat);
        assertEquals(crc(full(type)), reconstructed);
    }

    @ParameterizedTest
    @EnumSource(QrType.class)
    void handlesEmptyTruncatedAndNonNumericLengthForEveryType(QrType type) {
        assertEquals(QrValidationStatus.INVALID_INPUT, service.validate("", type).status());
        error(service.validate("000", type), QrValidationError.Code.INCOMPLETE_HEADER, "");
        error(service.validate("5905AB", type), QrValidationError.Code.TRUNCATED_VALUE, "59");
        error(service.validate("595AHELLO", type), QrValidationError.Code.NON_NUMERIC_LENGTH, "59");
    }

    @ParameterizedTest
    @EnumSource(QrType.class)
    void collectsSpecErrorsEvenWhenCrcIsWrong(QrType type) {
        String qr = crc(remove(full(type), "52"));
        qr = qr.substring(0, qr.length() - 1) + (qr.endsWith("0") ? "1" : "0");
        var result = service.validate(qr, type);
        assertEquals(QrValidationStatus.CRC_MISMATCH, result.status());
        assertEquals(QrValidationError.Code.CRC_MISMATCH, result.errors().getFirst().code());
        error(result, QrValidationError.Code.MISSING_REQUIRED_TAG, "52");
    }

    static Stream<Arguments> requiredRoots() {
        return Arrays.stream(QrType.values()).flatMap(type -> Stream.of("00", "26", "52", "53", "58", "59", "60")
                .map(id -> Arguments.of(type, id)));
    }

    @ParameterizedTest
    @MethodSource("requiredRoots")
    void requiresAllMandatoryRootsPerSheet(QrType type, String path) {
        var result = service.validate(crc(remove(base(type), path)), type);
        assertEquals(CrcValidationStatus.VALID, result.crcResult().status());
        var error = error(result, QrValidationError.Code.MISSING_REQUIRED_TAG, path);
        assertNull(error.absoluteOffset());
    }

    static Stream<Arguments> lengthCases() {
        return Arrays.stream(QrType.values()).flatMap(type -> {
            Stream<Arguments> common = Stream.of("59:25", "60:15", "61:10", "26/00:32", "26/01:13",
                    "62/03:20", "62/07:15", "62/08:19").map(s -> lengthCase(type, s));
            Stream<Arguments> specific = switch (type) {
                case TYPE_01 -> Stream.of("54:13", "62/01:20", "62/09:3").map(s -> lengthCase(type, s));
                case TYPE_02 -> Stream.of("62/06:25", "80/00:32", "80/01:20").map(s -> lengthCase(type, s));
                case TYPE_03, TYPE_04 -> Stream.of("54:13", "62/05:25", "62/09:3").map(s -> lengthCase(type, s));
            };
            return Stream.concat(common, specific);
        });
    }

    private static Arguments lengthCase(QrType type, String definition) {
        String[] parts = definition.split(":");
        return Arguments.of(type, parts[0], Integer.parseInt(parts[1]));
    }

    @ParameterizedTest
    @MethodSource("lengthCases")
    void enforcesEveryDeclaredMaximumAndAcceptsBoundary(QrType type, String path, int max) {
        for (int length : new int[]{max, max + 1}) {
            String value = "A".repeat(length);
            if (path.equals("62/09")) { value = "M".repeat(length); }
            if (path.equals("62/05")) {
                value = (type == QrType.TYPE_03 ? "02" : "01") + "2612311230" + "X".repeat(length - 12);
            }
            String payload = remove(base(type), "01");
            if (path.contains("/")) {
                String[] parts = path.split("/");
                String children = switch (parts[0]) {
                    case "26" -> tlv("00", "GUID") + tlv("01", "MERCHANT");
                    case "62" -> additional(type);
                    case "80" -> tlv("00", "GUID") + tlv("01", "122026");
                    default -> throw new AssertionError();
                };
                payload = replace(payload, parts[0], replace(children, parts[1], value));
            } else { payload = replace(payload, path, value); }
            var result = service.validate(crc(payload), type);
            if (length == max) { assertEquals(QrValidationStatus.VALID, result.status(), type + " " + path + result.errors()); }
            else { error(result, QrValidationError.Code.INVALID_FIELD_LENGTH, path); }
        }
    }

    @ParameterizedTest
    @EnumSource(QrType.class)
    void requiresGuidInEvery26AndKeepsOffsets(QrType type) {
        var result = service.validate(crc(base(type) + tlv("26", tlv("01", "ID"))), type);
        error(result, QrValidationError.Code.MISSING_REQUIRED_TAG, "26/00");
    }

    @ParameterizedTest
    @EnumSource(QrType.class)
    void enforcesFixedLengthsNumericAndCountryFormatsPerSheet(QrType type) {
        for (String id : List.of("00", "01", "52", "53", "58")) {
            error(service.validate(crc(replace(base(type), id, "X")), type), QrValidationError.Code.INVALID_FIELD_LENGTH, id);
        }
        for (String id : List.of("00", "01", "52", "53")) {
            error(service.validate(crc(replace(base(type), id, "XX")), type), QrValidationError.Code.INVALID_FIELD_VALUE, id);
        }
        error(service.validate(crc(replace(base(type), "58", "vn")), type), QrValidationError.Code.INVALID_FIELD_VALUE, "58");
        error(service.validate(crc(replace(base(type), "00", "02")), type), QrValidationError.Code.INVALID_FIELD_VALUE, "00");
        error(service.validate(crc(replace(base(type), "01", "13")), type), QrValidationError.Code.INVALID_FIELD_VALUE, "01");
    }

    @ParameterizedTest
    @EnumSource(QrType.class)
    void coversEveryReservedRootAndAllCommonMetadata(QrType type) {
        for (int id = 2; id <= 51; id++) {
            assertEquals("Merchant Account Information", metadata.lookup(type, String.format(java.util.Locale.ROOT, "%02d", id)).name());
        }
        for (String path : List.of("00", "01", "52", "53", "58", "59", "60", "61", "62", "63", "26/00", "26/01", "62/03", "62/07", "62/08")) {
            assertNotEquals("Unknown Tag", metadata.lookup(type, path).name(), type + " " + path);
        }
    }

    @Test
    void rejectsMalformedOrderReferenceAndLeavesValidPastDateUnexpired() {
        var type = QrType.TYPE_04;
        String payload = base(type) + tlv("62", tlv("05", "022612311230ORDER") + tlv("09", "M"));
        error(service.validate(crc(payload), type), QrValidationError.Code.INVALID_FIELD_VALUE, "62/05");
        String oldReference = "010001010000ANY-ID";
        assertTrue(service.validate(crc(base(type) + tlv("62", tlv("05", oldReference) + tlv("09", "M"))), type).isValid());
    }

    @ParameterizedTest
    @EnumSource(value = QrType.class, names = {"TYPE_01", "TYPE_03", "TYPE_04"})
    void requires09InEvery62ButNotAnAbsent62(QrType type) {
        assertTrue(service.validate(crc(base(type)), type).isValid());
        var result = service.validate(crc(full(type) + tlv("62", tlv("03", "OTHER"))), type);
        error(result, QrValidationError.Code.MISSING_REQUIRED_TAG, "62/09");
    }

    @Test
    void billingStaticAndDynamicDependOnTermNotAmountOrBill() {
        var type = QrType.TYPE_02;
        assertTrue(service.validate(crc(full(type)), type).isValid());
        String term = tlv("80", tlv("00", "GUID-ANS") + tlv("01", "DEC-2026"));
        String dynamic = replace(full(type), "01", "12") + term;
        assertTrue(service.validate(crc(dynamic), type).isValid());
        error(service.validate(crc(full(type) + term), type), QrValidationError.Code.INVALID_INITIATION_RULE, "01");
        error(service.validate(crc(replace(full(type), "01", "12")), type), QrValidationError.Code.INVALID_INITIATION_RULE, "01");
        // Không có metadata/quy tắc cho các tag vắng trong sheet Type 02.
        assertTrue(service.validate(crc(full(type) + tlv("54", "X".repeat(14))), type).isValid());
        assertEquals("Unknown Tag", metadata.lookup(type, "54").name());
        assertEquals("Unknown Tag", metadata.lookup(type, "62/09").name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "01"})
    void requiresEach80ChildInEveryTemplate(String missingId) {
        String term = tlv("00", "GUID") + tlv("01", "DEC26");
        String payload = remove(base(QrType.TYPE_02), "01") + tlv("80", term) + tlv("80", remove(term, missingId));
        var result = service.validate(crc(payload), QrType.TYPE_02);
        error(result, QrValidationError.Code.MISSING_REQUIRED_TAG, "80/" + missingId);
    }

    @Test
    void parses80OnlyAtRootInBillingContextAndKeepsChildrenAsLeaves() {
        String raw = tlv("80", tlv("00", "GUID") + tlv("01", "TERM") + tlv("80", "LEAF"));
        var parser = new NestedTlvParser();
        assertEquals(3, parser.parse(raw, QrType.TYPE_02).getFirst().children().size());
        assertTrue(parser.parse(raw, QrType.TYPE_02).getFirst().children().get(2).children().isEmpty());
        for (var type : List.of(QrType.TYPE_01, QrType.TYPE_03, QrType.TYPE_04)) {
            assertTrue(parser.parse(raw, type).getFirst().children().isEmpty());
            assertEquals("Unknown Tag", metadata.lookup(type, "80/01").name());
        }
        assertTrue(parser.parse(raw).getFirst().children().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"005AX,NON_NUMERIC_LENGTH,80/00,0", "0005AB,TRUNCATED_VALUE,80/00,0", "0001A0,INCOMPLETE_HEADER,80,5"})
    void reports80ChildErrorsAfterEmojiWithUtf16Offsets(String children, QrValidationError.Code code, String path, int relative) {
        String prefix = tlv("99", "😀");
        var result = service.validate(prefix + tlv("80", children), QrType.TYPE_02);
        var error = error(result, code, path);
        assertEquals(relative, error.parentValueOffset());
        assertEquals(prefix.length() + 4 + relative, error.absoluteOffset());
        assertNull(result.crcResult());
    }

    @Test
    void rejectsEmpty80AsStructureError() {
        error(service.validate(tlv("80", ""), QrType.TYPE_02), QrValidationError.Code.EMPTY_TEMPLATE, "80");
    }

    @ParameterizedTest
    @EnumSource(value = QrType.class, names = {"TYPE_03", "TYPE_04"})
    void referenceTypesKeepOwnInitiationRuleAndParseTheirReference(QrType type) {
        String value = type == QrType.TYPE_03 ? "022612311230PRODUCT" : "012612311230ORDER";
        var parsed = ReferenceLabel.parse(value, type).orElseThrow();
        assertEquals("2612311230", parsed.deadlineText());
        assertEquals(type == QrType.TYPE_03 ? "PRODUCT" : "ORDER", parsed.identifier());
        var result = service.validate(crc(full(type) + tlv("54", "55000.00")), type);
        assertTrue(result.isValid(), result.errors().toString());
        var reference = result.tags().stream().filter(t -> t.tag().equals("62")).findFirst().orElseThrow()
                .children().stream().filter(t -> t.tag().equals("05")).findFirst().orElseThrow();
        assertEquals(value, reference.value());
        assertTrue(reference.displayValue().contains(parsed.identifier()));
        assertTrue(reference.displayValue().contains(parsed.deadlineText()));
        String otherMethod = type == QrType.TYPE_03 ? "12" : "11";
        error(service.validate(crc(replace(full(type), "01", otherMethod)), type), QrValidationError.Code.INVALID_FIELD_VALUE, "01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"012612311230ID", "02", "022612311230", "02abcdefghijID", "022613011230ID",
            "022604311230ID", "022612312430ID", "022612311260ID", "02261231123１ID"})
    void rejectsMalformedProductReferenceWithoutGuessingAnotherType(String value) {
        var result = service.validate(crc(base(QrType.TYPE_03) + tlv("62", tlv("05", value) + tlv("09", "M"))), QrType.TYPE_03);
        error(result, QrValidationError.Code.INVALID_FIELD_VALUE, "62/05");
    }

    @Test
    void sameTagHasDifferentMeaningBetweenSheetsAndPaths() {
        assertEquals(4, Arrays.stream(QrType.values()).map(t -> metadata.lookup(t, "62/08").description()).distinct().count());
        assertNotEquals(metadata.lookup(QrType.TYPE_03, "62/05"), metadata.lookup(QrType.TYPE_04, "62/05"));
        assertEquals("Unknown Tag", metadata.lookup(QrType.TYPE_01, "62/05").name());
        assertEquals("Consumer Label", metadata.lookup(QrType.TYPE_02, "62/06").name());
        assertNotEquals(metadata.lookup(QrType.TYPE_02, "01"), metadata.lookup(QrType.TYPE_02, "80/01"));
        for (QrType type : QrType.values()) {
            assertEquals("Unknown Tag", metadata.lookup(type, "26/62/05").name());
            assertThrows(UnsupportedOperationException.class, () -> metadata.lookup(type, "01").valueMeanings().clear());
        }
    }

    @Test
    void neverDetectsTypeFromAmbiguousPayloadOrReferencePrefix() {
        String minimal = crc(remove(base(QrType.TYPE_01), "01"));
        for (var type : QrType.values()) { assertTrue(service.validate(minimal, type).isValid()); }
        assertEquals(service.validate(minimal, QrType.TYPE_01), service.validate(minimal));
        String product = crc(full(QrType.TYPE_03));
        assertEquals(service.validate(product, QrType.TYPE_01), service.validate(product));
        var reference = service.validate(product).tags().stream().filter(t -> t.tag().equals("62")).findFirst().orElseThrow()
                .children().stream().filter(t -> t.tag().equals("05")).findFirst().orElseThrow();
        assertEquals("Unknown Tag", reference.name());
        assertEquals(reference.value(), reference.displayValue());
        String opaque80 = crc(base(QrType.TYPE_01) + tlv("80", "opaque"));
        assertTrue(service.validate(opaque80).isValid());
        assertEquals(QrValidationStatus.INVALID_STRUCTURE, service.validate(opaque80, QrType.TYPE_02).status());
    }

    private QrValidationError error(QrValidationResult result, QrValidationError.Code code, String path) {
        assertFalse(result.isValid());
        return result.errors().stream().filter(e -> e.code() == code && e.tagPath().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError(code + " " + path + " " + result.errors()));
    }
}
