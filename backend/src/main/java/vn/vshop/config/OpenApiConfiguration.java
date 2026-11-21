package vn.vshop.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;
import vn.vshop.common.web.dto.ApiErrorResponse;

/**
 * Shapes the generated OpenAPI document into a contract that the TypeScript clients can rely on.
 *
 * <p>Springdoc alone leaves every response property optional and nullable-by-default. Here response
 * records are described as they behave: a component is required unless it is annotated
 * {@code @Nullable}, in which case it is also declared nullable. Request schemas keep the
 * requirements that Bean Validation already declares. The document is only produced by a test
 * ({@code OpenApiSpecTests}); the running API does not serve it.
 */
@Configuration
public class OpenApiConfiguration {
    static {
        // The authenticated principal comes from the token, never from the request, so it is not
        // an API parameter (without this, springdoc would flatten Jwt into query parameters).
        org.springdoc.core.utils.SpringDocUtils.getConfig()
                .addRequestWrapperToIgnore(org.springframework.security.oauth2.jwt.Jwt.class);
    }

    private static final String BEARER = "bearerAuth";
    private static final String ACCESS_EXTENSION = "x-vshop-access";

    @Bean
    OpenAPI vshopOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("VShop API")
                                .version("v1")
                                .description(
                                        "Marketplace API. Money is integer VND. Errors use ApiErrorResponse."
                                                + " Access rules are in the x-vshop-access extension of each"
                                                + " operation; the API enforces them again on every request."))
                .servers(List.of(new Server().url("/")))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        BEARER,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }

    @Bean
    OperationCustomizer vshopOperationIds() {
        return (operation, handlerMethod) -> {
            String controller =
                    handlerMethod.getBeanType().getSimpleName().replace("Controller", "");
            String method = handlerMethod.getMethod().getName();
            operation.setOperationId(
                    Character.toLowerCase(controller.charAt(0))
                            + controller.substring(1)
                            + Character.toUpperCase(method.charAt(0))
                            + method.substring(1));
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer vshopContract() {
        return openApi -> {
            describeResponseSchemas(openApi);
            addErrorSchema(openApi);
            if (openApi.getPaths() != null) {
                openApi.getPaths()
                        .forEach(
                                (path, item) ->
                                        item.readOperationsMap()
                                                .forEach(
                                                        (method, operation) ->
                                                                describeOperation(
                                                                        path,
                                                                        method.name(),
                                                                        operation)));
            }
        };
    }

    // ---- response schemas: required and nullable ----------------------------------------------

    private static void describeResponseSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Class<?>> records = responseRecords();
        openApi.getComponents()
                .getSchemas()
                .forEach(
                        (name, schema) -> {
                            if (schema.getProperties() == null) {
                                return;
                            }
                            if (name.startsWith("PageResponse")) {
                                // Generic page wrapper: nothing in it is nullable.
                                schema.setRequired(
                                        new ArrayList<>(
                                                new TreeSet<>(schema.getProperties().keySet())));
                                return;
                            }
                            Class<?> type = records.get(name);
                            if (type != null) {
                                markRecord(schema, type);
                            }
                        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void markRecord(Schema schema, Class<?> type) {
        Map<String, Schema> properties = schema.getProperties();
        TreeSet<String> required = new TreeSet<>();
        for (RecordComponent component : type.getRecordComponents()) {
            Schema property = properties.get(component.getName());
            if (property == null) {
                continue;
            }
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                properties.put(component.getName(), nullable(property));
            }
            required.add(component.getName());
        }
        // Nullable properties are still always present in the JSON (as null), so they are required.
        schema.setRequired(new ArrayList<>(required));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullable(Schema property) {
        if (property.get$ref() != null) {
            Schema nullType = new Schema<>();
            nullType.addType("null");
            return new Schema<>().oneOf(List.of(property, nullType));
        }
        property.addType("null");
        if (property.getEnum() != null && !property.getEnum().contains(null)) {
            property.addEnumItemObject(null);
        }
        return property;
    }

    /** Response records of this application, by the schema name springdoc gives them. */
    private static Map<String, Class<?>> responseRecords() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        Map<String, Class<?>> found = new HashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("vn.vshop")) {
            String className = candidate.getBeanClassName();
            if (className == null || !className.contains(".dto.response.")) {
                continue;
            }
            try {
                Class<?> type = Class.forName(className);
                Class<?> previous = found.put(type.getSimpleName(), type);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Two response types share the schema name "
                                    + type.getSimpleName()
                                    + ": rename one so the OpenAPI contract stays unambiguous.");
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return found;
    }

    // ---- common error schema ------------------------------------------------------------------

    private static void addErrorSchema(OpenAPI openApi) {
        ResolvedSchema resolved =
                ModelConverters.getInstance(true)
                        .resolveAsResolvedSchema(
                                new io.swagger.v3.core.converter.AnnotatedType(
                                                ApiErrorResponse.class)
                                        .resolveAsRef(false));
        Schema<?> error = resolved.schema;
        error.setRequired(List.of("code", "correlationId", "message"));
        openApi.getComponents().addSchemas("ApiErrorResponse", error);
    }

    // ---- operations: content types, errors, security ------------------------------------------

    private static void describeOperation(String path, String method, Operation operation) {
        normaliseContent(operation);
        ApiResponse failure =
                new ApiResponse()
                        .description(
                                "Error. The body is an ApiErrorResponse; the HTTP status says which kind.")
                        .content(
                                new Content()
                                        .addMediaType(
                                                "application/json",
                                                new MediaType()
                                                        .schema(
                                                                new Schema<>()
                                                                        .$ref(
                                                                                "#/components/schemas/ApiErrorResponse"))));
        operation.getResponses().addApiResponse("default", failure);

        Map<String, Object> access = new LinkedHashMap<>();
        if (((path.startsWith("/api/v1/catalog/") || path.equals("/api/v1/payments/vnpay/ipn"))
                        && "GET".equals(method))
                || ((path.equals("/api/v1/shipping/ghtk/webhook")
                                || path.equals("/api/v1/payments/payos/webhook")
                                || path.equals("/api/v1/auth/register"))
                        && "POST".equals(method))) {
            operation.setSecurity(List.of());
            access.put("public", true);
        } else {
            operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER)));
            access.put("public", false);
            access.put("anyOfRoles", rolesFor(path));
            if (path.startsWith("/api/v1/admin/")) {
                access.put("requiresSecondFactor", true);
            }
        }
        operation.addExtension(ACCESS_EXTENSION, access);
    }

    /** Roles that may call a path; mirrors SecurityConfiguration, which a test cross-checks. */
    static List<String> rolesFor(String path) {
        if (path.startsWith("/api/v1/admin/")) {
            return List.of("ADMIN");
        }
        if (path.equals("/api/v1/me")) {
            return List.of("ADMIN", "BUYER", "SELLER");
        }
        return List.of("BUYER", "SELLER");
    }

    /** Springdoc labels bodies as any media type; every body in this API is JSON. */
    private static void normaliseContent(Operation operation) {
        if (operation.getRequestBody() != null) {
            retype(operation.getRequestBody().getContent());
        }
        if (operation.getResponses() != null) {
            operation.getResponses().values().forEach(response -> retype(response.getContent()));
        }
    }

    private static void retype(Content content) {
        if (content != null && content.containsKey("*/*")) {
            content.addMediaType("application/json", content.remove("*/*"));
        }
    }
}
