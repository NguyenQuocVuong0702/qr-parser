package com.vnpay.qrparser.parser;

import com.vnpay.qrparser.model.QrType;
import com.vnpay.qrparser.model.QrValidationError.Code;
import com.vnpay.qrparser.model.TlvTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;

import static com.vnpay.qrparser.support.TestQrBuilder.tlv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TlvLengthBoundaryTest {
    private final TlvParser parser = new TlvParser();

    @Test
    void oversizedLengthConsumesNextHeaderThenReportsIncompleteHeaderAtNewBoundary() {
        String malformed = consumeNextHeader("59", "53");

        var exception = assertThrows(TlvParseException.class, () -> parser.parse(malformed));

        assertEquals(Code.INCOMPLETE_HEADER, exception.error().code());
        assertEquals("", exception.error().tagPath());
        assertEquals(10, exception.error().absoluteOffset());
        assertNull(exception.error().parentValueOffset());
    }

    @Test
    void declaredLengthCanConsumeWholeFollowingTagWithoutLeavingMalformedTlv() {
        String absorbedTag = tlv("61", "XYZ");
        String encodedValue = "AB" + absorbedTag;
        String input = tlv("99", encodedValue) + tlv("00", "01");

        var tags = parser.parse(input);

        assertEquals(List.of(new TlvTag("99", 9, encodedValue), new TlvTag("00", 2, "01")), tags);
        // The declared boundary is authoritative; the parser cannot recover an intended tag 61.
        assertEquals("AB6103XYZ", tags.getFirst().value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62", "80"})
    void oversizedChildLengthReportsParentAndBothOffsetsAfterConsumingNextHeader(String parent) {
        String prefix = tlv("00", "01");
        String malformedChildren = consumeNextHeader("03", "07");
        String input = prefix + tlv(parent, malformedChildren);

        var exception = assertThrows(TlvParseException.class,
                () -> new NestedTlvParser().parse(input, QrType.TYPE_02));

        assertEquals(Code.INCOMPLETE_HEADER, exception.error().code());
        assertEquals(parent, exception.error().tagPath());
        assertEquals(10, exception.error().parentValueOffset());
        assertEquals(20, exception.error().absoluteOffset());
    }

    private String consumeNextHeader(String firstId, String nextId) {
        String original = tlv(firstId, "AB") + tlv(nextId, "XYZ");
        int lengthIncludingNextHeader = 2 + 4;
        return original.substring(0, 2) + String.format(Locale.ROOT, "%02d", lengthIncludingNextHeader)
                + original.substring(4);
    }
}
