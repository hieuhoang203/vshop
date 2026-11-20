package vn.vshop.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Generates the OpenAPI document from the controllers and DTOs and compares it with the committed
 * {@code docs/api/openapi.json}. A mismatch fails the build, so the published contract can never
 * silently drift from the code. After an intentional API change run {@code npm run openapi:update}
 * (it sets UPDATE_OPENAPI=true), review the diff and commit it together with the regenerated
 * TypeScript contracts.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "springdoc.api-docs.enabled=true"
        })
@AutoConfigureMockMvc
class OpenApiSpecTests {
    private static final Path COMMITTED = Path.of("..", "docs", "api", "openapi.json");

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void committedSpecMatchesTheCode() throws Exception {
        String generated =
                normalise(
                        mvc.perform(get("/v3/api-docs"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString(StandardCharsets.UTF_8));
        if ("true".equals(System.getenv("UPDATE_OPENAPI"))) {
            Files.createDirectories(COMMITTED.getParent());
            Files.writeString(COMMITTED, generated, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.exists(COMMITTED)) {
            throw new AssertionError(
                    "docs/api/openapi.json is missing. Run `npm run openapi:update` and commit it.");
        }
        String committed =
                Files.readString(COMMITTED, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!committed.equals(generated)) {
            throw new AssertionError(
                    "The OpenAPI contract changed but docs/api/openapi.json was not updated."
                            + " Run `npm run openapi:update`, review the diff and commit it with the"
                            + " regenerated TypeScript contracts.");
        }
    }

    /** Stable text: sorted keys, two-space indent, LF line endings, trailing newline. */
    private static String normalise(String json) throws Exception {
        ObjectMapper mapper =
                JsonMapper.builder()
                        .enable(SerializationFeature.INDENT_OUTPUT)
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                        .build();
        JsonNode tree = mapper.readTree(json);
        return mapper.writeValueAsString(sortKeys(mapper, tree)).replace("\r\n", "\n") + "\n";
    }

    private static JsonNode sortKeys(ObjectMapper mapper, JsonNode node) {
        if (node.isObject()) {
            var sorted = mapper.createObjectNode();
            node.propertyNames().stream()
                    .sorted()
                    .forEach(name -> sorted.set(name, sortKeys(mapper, node.get(name))));
            return sorted;
        }
        if (node.isArray()) {
            var array = mapper.createArrayNode();
            node.forEach(item -> array.add(sortKeys(mapper, item)));
            return array;
        }
        return node;
    }
}
