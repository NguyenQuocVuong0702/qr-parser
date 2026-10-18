package com.vnpay.qrparser.api;

import com.vnpay.qrparser.crc.Crc16Calculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QrAnalyzeIntegrationTest {

    private static final String ORIGINAL_QR = "00020101021126300010A000000775011206820000021052045499"
            + "53037045802VN5911DONG QUE 796007LAMDONG62270311DONG QUE 790708DONGQUE163045E14";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void analyzesOriginalQrWithValidCrcAndSpecificationViolation() throws Exception {
        String response = analyze(ORIGINAL_QR)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value("SPEC_VIOLATION"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("MISSING_REQUIRED_TAG"))
                .andExpect(jsonPath("$.errors[0].tagPath").value("62/09"))
                .andExpect(jsonPath("$.errors[0].parentValueOffset").value(nullValue()))
                .andExpect(jsonPath("$.errors[0].absoluteOffset").value(nullValue()))
                .andExpect(jsonPath("$.crcResult.status").value("VALID"))
                .andExpect(jsonPath("$.crcResult.providedCrc").value("5E14"))
                .andExpect(jsonPath("$.crcResult.computedCrc").value("5E14"))
                .andExpect(jsonPath("$.tags.length()").value(10))
                .andExpect(jsonPath("$.tags[2].tag").value("26"))
                .andExpect(jsonPath("$.tags[2].children[0].tag").value("00"))
                .andExpect(jsonPath("$.tags[2].children[0].value").value("A000000775"))
                .andExpect(jsonPath("$.tags[2].children[0].name").value("Globally Unique Identifier"))
                .andExpect(jsonPath("$.tags[2].children[1].tag").value("01"))
                .andExpect(jsonPath("$.tags[2].children[1].value").value("068200000210"))
                .andExpect(jsonPath("$.tags[4].value").value("704"))
                .andExpect(jsonPath("$.tags[4].displayValue").value("VND"))
                .andExpect(jsonPath("$.tags[8].children.length()").value(2))
                .andExpect(jsonPath("$.tags[8].children[0].tag").value("03"))
                .andExpect(jsonPath("$.tags[8].children[0].value").value("DONG QUE 79"))
                .andExpect(jsonPath("$.tags[8].children[1].tag").value("07"))
                .andExpect(jsonPath("$.tags[8].children[1].value").value("DONGQUE1"))
                .andExpect(jsonPath("$.tags[9].value").value("5E14"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = jsonMapper.readTree(response);
        assertThat(json.get("errors").get(0).get("message").asString())
                .contains("Excel Type 01", "EMVCo Merchant-Presented Mode v1.1", "optional");
        StringBuilder reconstructed = new StringBuilder();
        for (JsonNode tag : json.get("tags")) {
            reconstructed.append(tag.get("tag").asString())
                    .append(String.format(Locale.ROOT, "%02d", tag.get("length").asInt()))
                    .append(tag.get("value").asString());
        }
        assertThat(reconstructed.toString()).isEqualTo(ORIGINAL_QR);
    }

    @Test
    void acceptsQrThatSatisfiesType01WithoutOptionalTemplate62() throws Exception {
        analyze(withCrc(basePayload("SHOP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.crcResult.status").value("VALID"))
                .andExpect(jsonPath("$.tags.length()").value(9));
    }

    @Test
    void acceptsTemplate62WithRequiredConsumerDataRequest() throws Exception {
        String additional = tlv("03", "STORE") + tlv("07", "TERM") + tlv("09", "M");
        analyze(withCrc(basePayload("SHOP") + tlv("62", additional)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[8].children[2].tag").value("09"))
                .andExpect(jsonPath("$.tags[8].children[2].value").value("M"));
    }

    @Test
    void preservesUnicodeAndLeadingTrailingSpacesInsideValues() throws Exception {
        String merchant = " ĐỒNG QUÊ ";
        analyze(withCrc(basePayload(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[6].value").value(merchant))
                .andExpect(jsonPath("$.tags[6].displayValue").value(merchant))
                .andExpect(jsonPath("$.crcResult.status").value("VALID"));
    }

    @Test
    void distinguishesCrcMismatchFromStructureErrorAndKeepsSpecificationErrors() throws Exception {
        String changedQr = ORIGINAL_QR.replaceFirst("DONG QUE 79", "DONG QUE 78");
        analyze(changedQr)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value("CRC_MISMATCH"))
                .andExpect(jsonPath("$.errors[0].code").value("CRC_MISMATCH"))
                .andExpect(jsonPath("$.errors[1].code").value("MISSING_REQUIRED_TAG"))
                .andExpect(jsonPath("$.crcResult.status").value("MISMATCH"))
                .andExpect(jsonPath("$.crcResult.providedCrc").value("5E14"))
                .andExpect(jsonPath("$.crcResult.computedCrc").value("93F6"))
                .andExpect(jsonPath("$.tags.length()").value(10))
                .andExpect(jsonPath("$.tags[6].value").value("DONG QUE 78"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n\t"})
    void treatsEmptyQrAsBusinessInvalidInput(String qr) throws Exception {
        analyze(qr)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].code").value("EMPTY_INPUT"))
                .andExpect(jsonPath("$.crcResult").value(nullValue()))
                .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    void treatsExplicitNullQrAsBusinessInvalidInput() throws Exception {
        send("{\"qr\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].code").value("EMPTY_INPUT"));
    }

    @ParameterizedTest
    @CsvSource({"000,INCOMPLETE_HEADER", "005Asecret,NON_NUMERIC_LENGTH", "0002A,TRUNCATED_VALUE",
            "AA0201,INVALID_TAG_ID"})
    void distinguishesTlvStructureErrorsFromRequestErrors(String qr, String code) throws Exception {
        analyze(qr)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value("INVALID_STRUCTURE"))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.errors[0].absoluteOffset").value(0))
                .andExpect(jsonPath("$.crcResult").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"26", "62"})
    void keepsPreciseParentAndAbsoluteOffsetsForNestedErrors(String parentId) throws Exception {
        String prefix = "000201010211";
        String childValue = "000201015Asecret";
        String qr = prefix + tlv(parentId, childValue);

        String response = analyze(qr)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].code").value("NON_NUMERIC_LENGTH"))
                .andExpect(jsonPath("$.errors[0].tagPath").value(parentId + "/01"))
                .andExpect(jsonPath("$.errors[0].parentValueOffset").value(6))
                .andExpect(jsonPath("$.errors[0].absoluteOffset").value(22))
                .andExpect(jsonPath("$.tags").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("secret", "5A", "TlvParseException");
    }

    @ParameterizedTest
    @CsvSource({"'',CRC_MISSING", "6303ABC,CRC_INVALID_LENGTH", "6304ZZZZ,CRC_INVALID_FORMAT",
            "6304ABCD5901X,CRC_INVALID_POSITION", "6304ABCD6304ABCD,CRC_DUPLICATED"})
    void reportsCrcStructureErrorsWithParsedTree(String suffix, String code) throws Exception {
        analyze(basePayload("SHOP") + suffix)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value("INVALID_STRUCTURE"))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(jsonPath("$.crcResult.status").value("STRUCTURE_ERROR"))
                .andExpect(jsonPath("$.crcResult.errorCode").value(code))
                .andExpect(jsonPath("$.tags").isNotEmpty());
    }

    @ParameterizedTest
    @CsvSource({"null,REQUEST_BODY_NULL", "{},QR_REQUIRED", "{\"qr\":123},QR_MUST_BE_STRING",
            "[],REQUEST_BODY_MUST_BE_OBJECT"})
    void checksActualJacksonBehaviorInFullApplication(String body, String code) throws Exception {
        send(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].code").value(code));
    }

    @Test
    void returnsSafeJsonForMalformedRequestWithSensitiveText() throws Exception {
        String response = send("{\"qr\":\"sensitive-qr\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_JSON"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("sensitive-qr", "Exception", "stackTrace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "vn", "V1", "ÁB"})
    void returnsSpecificationViolationForInvalidCountryFormat(String country) throws Exception {
        String payload = basePayload("SHOP").replace("5802VN", tlv("58", country));

        analyze(withCrc(payload))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value("SPEC_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FIELD_VALUE"))
                .andExpect(jsonPath("$.errors[0].tagPath").value("58"))
                .andExpect(jsonPath("$.crcResult.status").value("VALID"));
    }

    @Test
    void preservesSupplementaryUnicodeThroughJsonParsingAndCrc() throws Exception {
        String merchant = " ĐỒNG \uD83D\uDE00 ";

        analyze(withCrc(basePayload(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[6].value").value(merchant))
                .andExpect(jsonPath("$.tags[6].length").value(8))
                .andExpect(jsonPath("$.crcResult.status").value("VALID"));
    }

    @Test
    void returnsUtf16OffsetsForSpecificationErrorAfterEmoji() throws Exception {
        String prefix = basePayload("SHOP") + tlv("99", "\uD83D\uDE00");
        String children = tlv("03", "\uD83D\uDE00") + tlv("09", "X");

        analyze(withCrc(prefix + tlv("62", children)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0].tagPath").value("62/09"))
                .andExpect(jsonPath("$.errors[0].parentValueOffset").value(6))
                .andExpect(jsonPath("$.errors[0].absoluteOffset").value(prefix.length() + 4 + 6))
                .andExpect(jsonPath("$.tags[9].children[0].value").value("\uD83D\uDE00"))
                .andExpect(jsonPath("$.tags[9].children[0].length").value(1))
                .andExpect(jsonPath("$.crcResult.status").value("VALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\uD800", "\\uDC00"})
    void rejectsActualJacksonStringsWithUnpairedSurrogates(String escapedSurrogate) throws Exception {
        // Dùng JSON escape ASCII để byte UTF-8 của request không thay thế surrogate trước Jackson.
        String response = send("{\"qr\":\"9901" + escapedSurrogate + "\"}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.status").value("INVALID_STRUCTURE"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_UNICODE"))
                .andExpect(jsonPath("$.errors[0].tagPath").value("99"))
                .andExpect(jsonPath("$.errors[0].absoluteOffset").value(0))
                .andExpect(jsonPath("$.crcResult").value(nullValue()))
                .andExpect(jsonPath("$.tags").isEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("D800", "DC00", "Exception", "9901");
    }

    @Test
    void buildsAsciiLengthHeadersRegardlessOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"));
            assertThat(tlv("99", "\uD83D\uDE00")).isEqualTo("9901\uD83D\uDE00");
        } finally {
            Locale.setDefault(original);
        }
    }

    private ResultActions analyze(String qr) throws Exception {
        return send(jsonMapper.writeValueAsString(Map.of("qr", qr)));
    }

    private ResultActions send(String json) throws Exception {
        return mvc.perform(post("/api/qr/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(json.getBytes(StandardCharsets.UTF_8)))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private String basePayload(String merchant) {
        return tlv("00", "01") + tlv("01", "11") + tlv("26", tlv("00", "ABCDEF"))
                + tlv("52", "5499") + tlv("53", "704") + tlv("58", "VN")
                + tlv("59", merchant) + tlv("60", "CITY");
    }

    private String tlv(String tag, String value) {
        return tag + String.format(Locale.ROOT, "%02d", value.codePointCount(0, value.length())) + value;
    }

    private String withCrc(String payload) {
        String prefix = payload + "6304";
        return prefix + new Crc16Calculator().calculateHex(prefix);
    }
}
