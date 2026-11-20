package vn.vshop.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.vshop.modules.identity.service.AddressService;

@SpringBootTest(
        properties = {
            "spring.datasource.url=${VSHOP_TEST_DATABASE_URL:jdbc:postgresql://localhost:15433/vshop_test}",
            "spring.flyway.locations=classpath:db/migration",
            "vshop.worker.enabled=false",
            "vshop.pii.keys.test-1=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "vshop.pii.active-key=test-1"
        })
@AutoConfigureMockMvc
@Transactional
class AddressIntegrationTests {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STREET = "Số 12 ngõ 34 Nguyễn Trãi";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired AddressService addresses;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void firstAddressBecomesDefaultAndOnlyOneDefaultExists() throws Exception {
        JsonNode first = create("addr-1", body("Nhà", "0912345678", STREET, false), 201);
        assertThat(first.get("isDefault").asBoolean()).isTrue();
        JsonNode second =
                create("addr-1", body("Công ty", "0987654321", "Tầng 5 Tòa A", false), 201);
        assertThat(second.get("isDefault").asBoolean()).isFalse();
        JsonNode third =
                create("addr-1", body("Bố mẹ", "0356789012", "Xóm 3 thôn Đông", true), 201);
        assertThat(third.get("isDefault").asBoolean()).isTrue();

        JsonNode list = list("addr-1");
        assertThat(list).hasSize(3);
        assertThat(list.get(0).get("id").asText())
                .isEqualTo(third.get("id").asText()); // default first
        assertThat(count("is_default", "addr-1")).isEqualTo(1);

        mvc.perform(
                        put("/api/v1/me/addresses/" + first.get("id").asText() + "/default")
                                .with(buyer("addr-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
        assertThat(count("is_default", "addr-1")).isEqualTo(1);
        assertThat(list("addr-1").get(0).get("id").asText()).isEqualTo(first.get("id").asText());
    }

    @Test
    void phoneNumbersAreNormalisedAndBadInputIsRejected() throws Exception {
        for (String accepted :
                new String[] {
                    "0912345678", "091 234 5678", "091.234.5678", "+84912345678", "84912345678"
                }) {
            JsonNode created = create("addr-2", body("X", accepted, STREET, false), 201);
            assertThat(created.get("phone").asText()).isEqualTo("0912345678");
            remove("addr-2", created.get("id").asText(), 204);
        }
        for (String bad :
                new String[] {"0212345678", "12345", "09123456789", "abcdefghij", "+8491234", ""}) {
            create("addr-2", body("X", bad, STREET, false), 400);
        }
        create("addr-2", body("X", "0912345678", "ngắn", false), 400); // street < 5
        create("addr-2", body("X", "0912345678", STREET, false).replace("Nguyễn Văn A", "A"), 400);
        create(
                "addr-2",
                body("X", "0912345678", STREET, false)
                        .replace("\"ward\":\"Phường Láng\"", "\"ward\":\"\""),
                400);
        // Control characters are refused rather than stored.
        create("addr-2", body("X", "0912345678", "Số 1\\nDòng hai xyz", false), 400);
        assertThat(count("1", "addr-2")).isZero();
    }

    @Test
    void phoneAndStreetAreEncryptedAtRestAndBoundToTheirRow() throws Exception {
        JsonNode a = create("addr-3", body("A", "0912345678", STREET, false), 201);
        JsonNode b = create("addr-3", body("B", "0987654321", "Căn hộ 1203 Tòa B", false), 201);
        for (String column : new String[] {"phone_ciphertext", "street_ciphertext"}) {
            for (String stored :
                    jdbc.sql("SELECT " + column + " FROM identity.address")
                            .query(String.class)
                            .list()) {
                assertThat(stored).startsWith("v1.test-1.");
                assertThat(stored)
                        .doesNotContain("0912345678")
                        .doesNotContain("Nguyễn")
                        .doesNotContain("Tòa");
            }
        }
        assertThat(
                        jdbc.sql("SELECT phone_ciphertext FROM identity.address WHERE id = :id")
                                .param("id", UUID.fromString(a.get("id").asText()))
                                .query(String.class)
                                .single())
                .doesNotContain("0912345678");

        // Copying a ciphertext into another row (or column) makes reads fail, never leak.
        jdbc.sql(
                        "UPDATE identity.address SET phone_ciphertext = (SELECT phone_ciphertext FROM identity.address WHERE id = :a) WHERE id = :b")
                .param("a", UUID.fromString(a.get("id").asText()))
                .param("b", UUID.fromString(b.get("id").asText()))
                .update();
        assertThatThrownBy(() -> addresses.list("addr-3"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void addressesBelongOnlyToTheirOwner() throws Exception {
        JsonNode mine = create("addr-4", body("Mine", "0912345678", STREET, false), 201);
        String id = mine.get("id").asText();
        create("addr-5", body("Other", "0987654321", "Tầng 2", false), 201);

        assertThat(list("addr-5")).hasSize(1);
        mvc.perform(
                        put("/api/v1/me/addresses/" + id)
                                .with(buyer("addr-5"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(update(body("Hacked", "0912345678", STREET, false), 0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        mvc.perform(put("/api/v1/me/addresses/" + id + "/default").with(buyer("addr-5")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/addresses/" + id).with(buyer("addr-5")))
                .andExpect(status().isNotFound());
        assertThat(count("1", "addr-4")).isEqualTo(1);

        mvc.perform(get("/api/v1/me/addresses")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/me/addresses")
                                .with(
                                        jwt().jwt(j -> j.subject("only-admin"))
                                                .authorities(
                                                        new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatesUseVersionsAndDefaultChangesDoNotBumpThem() throws Exception {
        JsonNode created = create("addr-6", body("Old", "0912345678", STREET, false), 201);
        String id = created.get("id").asText();
        assertThat(created.get("version").asLong()).isZero();

        mvc.perform(
                        put("/api/v1/me/addresses/" + id)
                                .with(buyer("addr-6"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        update(
                                                body("New", "0987654321", "Phố mới số 9", false),
                                                0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.phone").value("0987654321"))
                .andExpect(jsonPath("$.street").value("Phố mới số 9"))
                .andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(
                        put("/api/v1/me/addresses/" + id)
                                .with(buyer("addr-6"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        update(
                                                body("Stale", "0987654321", "Phố cũ số 9", false),
                                                0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        create("addr-6", body("Two", "0356789012", "Xóm 3", false), 201);
        mvc.perform(put("/api/v1/me/addresses/" + id + "/default").with(buyer("addr-6")))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void deletingTheDefaultPromotesAnotherAndTheLimitIsEnforced() throws Exception {
        JsonNode a = create("addr-7", body("A", "0912345678", STREET, false), 201);
        JsonNode b = create("addr-7", body("B", "0987654321", "Tầng 2 Tòa B", false), 201);
        remove("addr-7", a.get("id").asText(), 204); // a was the default
        JsonNode remaining = list("addr-7");
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).get("id").asText()).isEqualTo(b.get("id").asText());
        assertThat(remaining.get(0).get("isDefault").asBoolean()).isTrue();
        remove("addr-7", b.get("id").asText(), 204);
        assertThat(list("addr-7")).isEmpty();

        for (int i = 0; i < 10; i++) {
            create(
                    "addr-7",
                    body("N" + i, "09123456" + (10 + i), "Địa chỉ số " + i + " phố A", false),
                    201);
        }
        mvc.perform(
                        post("/api/v1/me/addresses")
                                .with(buyer("addr-7"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("Extra", "0912345678", STREET, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADDRESS_LIMIT_REACHED"));
    }

    @Test
    void quotesBindToAnAddressRegionOnlyAndGoStaleWhenItChanges() throws Exception {
        UUID sku = sellableSku();
        mvc.perform(
                        post("/api/v1/cart/lines")
                                .with(buyer("addr-8"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"skuId\":\"" + sku + "\",\"quantity\":1}"))
                .andExpect(status().isOk());
        JsonNode address = create("addr-8", body("Home", "0912345678", STREET, false), 201);
        String id = address.get("id").asText();

        String quoteJson =
                mvc.perform(
                                post("/api/v1/checkout/quotes")
                                        .with(buyer("addr-8"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"addressId\":\"" + id + "\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.current").value(true))
                        .andExpect(jsonPath("$.shipTo.province").value("Hà Nội"))
                        .andExpect(jsonPath("$.shipTo.ward").value("Phường Láng"))
                        .andExpect(
                                content()
                                        .string(
                                                org.hamcrest.Matchers.not(
                                                        org.hamcrest.Matchers.containsString(
                                                                "0912345678"))))
                        .andExpect(
                                content()
                                        .string(
                                                org.hamcrest.Matchers.not(
                                                        org.hamcrest.Matchers.containsString(
                                                                "Nguyễn Trãi"))))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String quote = JSON.readTree(quoteJson).get("id").asText();
        assertThat(
                        jdbc.sql("SELECT snapshot::text FROM checkout.quote WHERE id = :id")
                                .param("id", UUID.fromString(quote))
                                .query(String.class)
                                .single())
                .doesNotContain("0912345678");

        // Becoming the default does not change what the quote was calculated for.
        create("addr-8", body("Work", "0987654321", "Tầng 9 Tòa C", true), 201);
        mvc.perform(get("/api/v1/checkout/quotes/" + quote).with(buyer("addr-8")))
                .andExpect(jsonPath("$.current").value(true));

        // Editing the address makes the quote stale (BR04).
        mvc.perform(
                        put("/api/v1/me/addresses/" + id)
                                .with(buyer("addr-8"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        update(
                                                body("Home", "0912345678", "Số 99 phố mới", false),
                                                0)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/checkout/quotes/" + quote).with(buyer("addr-8")))
                .andExpect(jsonPath("$.current").value(false))
                .andExpect(jsonPath("$.expired").value(false));

        // So does deleting it; the region stays visible as what was quoted.
        String second =
                JSON.readTree(
                                mvc.perform(
                                                post("/api/v1/checkout/quotes")
                                                        .with(buyer("addr-8"))
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content("{\"addressId\":\"" + id + "\"}"))
                                        .andExpect(status().isCreated())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString())
                        .get("id")
                        .asText();
        remove("addr-8", id, 204);
        mvc.perform(get("/api/v1/checkout/quotes/" + second).with(buyer("addr-8")))
                .andExpect(jsonPath("$.current").value(false))
                .andExpect(jsonPath("$.shipTo.province").value("Hà Nội"));

        // Another buyer's address, or an unknown one, cannot be used.
        JsonNode theirs =
                create("addr-9", body("Theirs", "0356789012", "Xóm 3 thôn Đông", false), 201);
        mvc.perform(
                        post("/api/v1/checkout/quotes")
                                .with(buyer("addr-8"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"addressId\":\"" + theirs.get("id").asText() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
        // A quote without an address still works as before.
        mvc.perform(post("/api/v1/checkout/quotes").with(buyer("addr-8")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipTo").doesNotExist());
    }

    private UUID sellableSku() {
        UUID owner = UUID.randomUUID();
        UUID shop = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        UUID sku = UUID.randomUUID();
        jdbc.sql("INSERT INTO identity.account(external_subject) VALUES (:s)")
                .param("s", "addr-seller-" + owner)
                .update();
        jdbc.sql(
                        "INSERT INTO merchant.shop(id,owner_account_id,name,description,status) SELECT :id,id,'Addr Shop','Address test shop description',:st FROM identity.account WHERE external_subject=:s")
                .param("id", shop)
                .param("st", "APPROVED")
                .param("s", "addr-seller-" + owner)
                .update();
        jdbc.sql("INSERT INTO catalog.category(id,name,slug) VALUES (:id,'Addr',:slug)")
                .param("id", category)
                .param("slug", "addr-" + category)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product(id,shop_id,category_id,slug,name,description,image_key,status) VALUES (:id,:shop,:c,:slug,'Addr product','Address test product description','lamp','ACTIVE')")
                .param("id", sku)
                .param("shop", shop)
                .param("c", category)
                .param("slug", "addr-" + sku)
                .update();
        jdbc.sql(
                        "INSERT INTO catalog.product_sku(id,product_id,shop_id,seller_sku,price) VALUES (:id,:id,:shop,:code,10000)")
                .param("id", sku)
                .param("shop", shop)
                .param("code", "ADDR-" + sku)
                .update();
        UUID warehouse = UUID.randomUUID();
        jdbc.sql("INSERT INTO inventory.warehouse(id,shop_id,code,name) VALUES (:id,:s,'W','Kho')")
                .param("id", warehouse)
                .param("s", shop)
                .update();
        jdbc.sql(
                        "INSERT INTO inventory.stock_balance(sku_id,warehouse_id,shop_id,on_hand) VALUES (:k,:w,:s,5)")
                .param("k", sku)
                .param("w", warehouse)
                .param("s", shop)
                .update();
        return sku;
    }

    private long count(String expression, String subject) {
        return jdbc.sql(
                        "SELECT count(*) FILTER (WHERE "
                                + (expression.equals("1") ? "TRUE" : expression)
                                + ") FROM identity.address a JOIN identity.account c ON c.id = a.account_id WHERE c.external_subject = :s")
                .param("s", subject)
                .query(Long.class)
                .single();
    }

    private JsonNode create(String subject, String body, int expected) throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/me/addresses")
                                        .with(buyer(subject))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().is(expected))
                        .andReturn();
        String text = result.getResponse().getContentAsString();
        return text.isBlank() ? null : JSON.readTree(text);
    }

    private JsonNode list(String subject) throws Exception {
        return JSON.readTree(
                mvc.perform(get("/api/v1/me/addresses").with(buyer(subject)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private void remove(String subject, String id, int expected) throws Exception {
        mvc.perform(delete("/api/v1/me/addresses/" + id).with(buyer(subject)))
                .andExpect(status().is(expected));
    }

    private static String body(String label, String phone, String street, boolean makeDefault) {
        return "{\"label\":\""
                + label
                + "\",\"recipientName\":\"Nguyễn Văn A\",\"phone\":\""
                + phone
                + "\",\"street\":\""
                + street
                + "\",\"ward\":\"Phường Láng\",\"province\":\"Hà Nội\",\"makeDefault\":"
                + makeDefault
                + "}";
    }

    private static String update(String createBody, long version) {
        return createBody.substring(0, createBody.length() - 1)
                + ",\"expectedVersion\":"
                + version
                + "}";
    }

    private static JwtRequestPostProcessor buyer(String subject) {
        return jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
    }
}
