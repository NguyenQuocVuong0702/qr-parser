package com.vnpay.qrparser.parser;

import com.vnpay.qrparser.model.TlvTag;
import com.vnpay.qrparser.model.QrValidationError.Code;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TlvParserTest {

    private final TlvParser parser = new TlvParser();

    @Test
    void parsesSampleIntoThreeTags() {
        List<TlvTag> tags = parser.parse("0002010102115303704");

        assertEquals(List.of(
                new TlvTag("00", 2, "01"),
                new TlvTag("01", 2, "11"),
                new TlvTag("53", 3, "704")
        ), tags);
    }

    @Test
    void parsesSingleTag() {
        assertEquals(List.of(new TlvTag("59", 5, "HELLO")), parser.parse("5905HELLO"));
    }

    @Test
    void parsesDifferentTagsAndTwoDigitLength() {
        assertEquals(List.of(
                new TlvTag("59", 10, "ABCDEFGHIJ"),
                new TlvTag("54", 4, "1500")
        ), parser.parse("5910ABCDEFGHIJ54041500"));
    }

    @Test
    void acceptsZeroLengthValue() {
        assertEquals(List.of(new TlvTag("59", 0, "")), parser.parse("5900"));
    }

    @Test
    void continuesToNextTagAfterZeroLengthValue() {
        assertEquals(List.of(
                new TlvTag("59", 0, ""),
                new TlvTag("00", 2, "01")
        ), parser.parse("5900000201"));
    }

    @Test
    void preservesLeadingInternalAndTrailingSpacesInValue() {
        assertEquals(List.of(new TlvTag("59", 5, " A B ")), parser.parse("5905 A B "));
    }

    @Test
    void acceptsValueContainingOnlySpaces() {
        assertEquals(List.of(new TlvTag("59", 3, "   ")), parser.parse("5903   "));
    }

    @Test
    void keepsTemplateValuesAsStrings() {
        assertEquals(List.of(
                new TlvTag("26", 6, "000201"),
                new TlvTag("62", 6, "000201")
        ), parser.parse("26060002016206000201"));
    }

    @Test
    void rejectsNullInput() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(null));

        assertEquals("Dữ liệu TLV không được để trống.", exception.getMessage());
    }

    @Test
    void rejectsEmptyInput() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(""));

        assertEquals("Dữ liệu TLV không được để trống.", exception.getMessage());
    }

    @Test
    void rejectsBlankInput() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse(" \t\n"));

        assertEquals("Dữ liệu TLV không được để trống.", exception.getMessage());
    }

    @Test
    void rejectsIncompleteFirstHeader() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("000"));

        assertEquals("Thiếu header tại vị trí 0: cần 4 ký tự, chỉ còn 3.", exception.getMessage());
    }

    @Test
    void rejectsIncompleteHeaderAfterValidTag() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("0002010"));

        assertEquals("Thiếu header tại vị trí 6: cần 4 ký tự, chỉ còn 1.", exception.getMessage());
    }

    @Test
    void rejectsNonNumericLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("00A201"));

        assertEquals("Length của tag 00 tại vị trí 0 phải gồm 2 chữ số, nhận được 'A2'.",
                exception.getMessage());
    }

    @Test
    void rejectsSignedLength() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("00+201"));

        assertEquals("Length của tag 00 tại vị trí 0 phải gồm 2 chữ số, nhận được '+2'.",
                exception.getMessage());
    }

    @Test
    void rejectsLengthContainingSpace() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("000 01"));

        assertEquals("Length của tag 00 tại vị trí 0 phải gồm 2 chữ số, nhận được '0 '.",
                exception.getMessage());
    }

    @Test
    void rejectsTruncatedValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("00021"));

        assertEquals("Value của tag 00 tại vị trí 0 bị cắt ngắn: cần 2 ký tự, chỉ còn 1.",
                exception.getMessage());
    }

    @Test
    void rejectsMissingValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("0002"));

        assertEquals("Value của tag 00 tại vị trí 0 bị cắt ngắn: cần 2 ký tự, chỉ còn 0.",
                exception.getMessage());
    }

    @Test
    void rejectsTruncatedValueAfterValidTag() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> parser.parse("00020153037"));

        assertEquals("Value của tag 53 tại vị trí 6 bị cắt ngắn: cần 3 ký tự, chỉ còn 1.",
                exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HELLO", "ĐỒNG QUÊ", "\uD83D\uDE00", " A\uD83D\uDE00 ", "e\u0301"})
    void parsesValuesByCodePointWithoutNormalizing(String value) {
        int length = value.codePointCount(0, value.length());
        String qr = "99" + String.format(Locale.ROOT, "%02d", length) + value + "000201";

        assertEquals(List.of(new TlvTag("99", length, value), new TlvTag("00", 2, "01")), parser.parse(qr));
    }

    @Test
    void parsesNinetyNineSupplementaryCodePointsAndNextTag() {
        String value = "\uD83D\uDE00".repeat(99);
        List<TlvTag> tags = parser.parse("9999" + value + "000201");

        assertEquals(99, tags.getFirst().length());
        assertEquals(198, tags.getFirst().value().length());
        assertEquals(new TlvTag("00", 2, "01"), tags.get(1));
    }

    @Test
    void reportsTruncatedValueUsingCodePointCount() {
        TlvParseException exception = assertThrows(TlvParseException.class, () -> parser.parse("9902\uD83D\uDE00"));

        assertEquals(Code.TRUNCATED_VALUE, exception.error().code());
        assertEquals(0, exception.error().absoluteOffset());
        assertEquals("Value của tag 99 tại vị trí 0 bị cắt ngắn: cần 2 ký tự, chỉ còn 1.", exception.getMessage());
    }

    @Test
    void keepsIncompleteHeaderOffsetInUtf16AfterEmoji() {
        TlvParseException exception = assertThrows(TlvParseException.class,
                () -> parser.parse("9901\uD83D\uDE000"));

        assertEquals(Code.INCOMPLETE_HEADER, exception.error().code());
        assertEquals(6, exception.error().absoluteOffset());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uD800", "\uDC00", "A\uD800X", "\uDC00\uD800"})
    void rejectsUnpairedSurrogatesWithStableCode(String value) {
        String qr = "99" + String.format(Locale.ROOT, "%02d", value.codePointCount(0, value.length())) + value;
        TlvParseException exception = assertThrows(TlvParseException.class, () -> parser.parse(qr));

        assertEquals(Code.INVALID_UNICODE, exception.error().code());
        assertEquals("99", exception.error().tagPath());
        assertEquals(0, exception.error().absoluteOffset());
    }

    @Test
    void rejectsUnpairedSurrogateInHeader() {
        TlvParseException exception = assertThrows(TlvParseException.class, () -> parser.parse("\uD800001A"));

        assertEquals(Code.INVALID_UNICODE, exception.error().code());
        assertEquals(0, exception.error().absoluteOffset());
    }

    @Test
void invalidUnicodeInValueShouldMentionValue() {
    String invalidUnicode = String.valueOf((char) 0xD800);
    String input = "5901" + invalidUnicode;

    TlvParseException exception = assertThrows(
            TlvParseException.class,
            () -> new TlvParser().parse(input)
    );

    assertEquals(Code.INVALID_UNICODE, exception.error().code());

    assertTrue(
            exception.error().message().contains("value của tag 59")
    );
}
}
