package com.vnpay.qrparser.api;

import com.vnpay.qrparser.model.QrType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static com.vnpay.qrparser.support.TestQrBuilder.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class QrTypeApiTest {
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;

    @ParameterizedTest
    @EnumSource(QrType.class)
    void acceptsAllExplicitTypesWithSameResponseContract(QrType type) throws Exception {
        String response = mvc.perform(post("/api/qr/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("qr", crc(full(type)), "type", type.code()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.crcResult.status").value("VALID"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertEquals(5, json.readTree(response).size());
    }

    @Test
    void exposesBillingTemplate80AndTermMetadataOverHttp() throws Exception {
        String payload = replace(full(QrType.TYPE_02), "01", "12")
                + tlv("80", tlv("00", "GUID") + tlv("01", "DEC26"));
        mvc.perform(post("/api/qr/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("qr", crc(payload), "type", "02"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[9].tag").value("80"))
                .andExpect(jsonPath("$.tags[9].children[0].name").value("Globally Unique Identifier"))
                .andExpect(jsonPath("$.tags[9].children[1].name").value("Term"))
                .andExpect(jsonPath("$.tags[9].children[1].value").value("DEC26"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"00", "05", "1", "TYPE_01", "Type 02", "", " 01", "auto", "０１", "sensitive-qr"})
    void rejectsUnknownTypeInsteadOfDetectingOrFallingBack(String type) throws Exception {
        String response = mvc.perform(post("/api/qr/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("qr", crc(base(QrType.TYPE_01)), "type", type))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_QR_TYPE"))
                .andExpect(jsonPath("$.tags").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("sensitive-qr"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "false", "[]", "{}"})
    void rejectsNonStringTypeWithoutCoercion(String rawType) throws Exception {
        mvc.perform(post("/api/qr/analyze").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qr\":\"000201\",\"type\":" + rawType + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("TYPE_MUST_BE_STRING"));
    }

    @Test
    void omittedNullAndExplicit01AreEquivalentEvenForAmbiguousProductPayload() throws Exception {
        String qr = crc(full(QrType.TYPE_03));
        var request = json.createObjectNode().put("qr", qr);
        String original = send(request.toString());
        request.putNull("type");
        assertEquals(json.readTree(original), json.readTree(send(request.toString())));
        request.put("type", "01");
        assertEquals(json.readTree(original), json.readTree(send(request.toString())));
        var reference = json.readTree(original).get("tags").get(8).get("children").get(3);
        assertEquals("05", reference.get("tag").asString());
        assertEquals("Unknown Tag", reference.get("name").asString());
        request.put("type", "03");
        var selected = json.readTree(send(request.toString())).get("tags").get(8).get("children").get(3);
        assertEquals("Reference Label", selected.get("name").asString());
        assertTrue(selected.get("displayValue").asString().contains("Mã sản phẩm: PRODUCT"));
    }

    private String send(String body) throws Exception {
        return mvc.perform(post("/api/qr/analyze").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void allFourReadmeRequestsActuallyReturnValidWithMatchingCrc() throws Exception {
        String readme = Files.readString(Path.of("README.md"));
        var blocks = Pattern.compile("```json\\R(.*?)\\R```", Pattern.DOTALL).matcher(readme);
        var testedTypes = new HashSet<String>();
        while (blocks.find()) {
            var body = json.readTree(blocks.group(1));
            if (body.has("type") && body.has("qr")) {
                String type = body.get("type").asString();
                assertTrue(testedTypes.add(type), "Ví dụ Type bị lặp: " + type);
                var response = json.readTree(send(blocks.group(1)));
                assertEquals("VALID", response.get("status").asString());
                assertEquals("VALID", response.get("crcResult").get("status").asString());
            }
        }
        assertEquals(java.util.Set.of("01", "02", "03", "04"), testedTypes);
    }
}
