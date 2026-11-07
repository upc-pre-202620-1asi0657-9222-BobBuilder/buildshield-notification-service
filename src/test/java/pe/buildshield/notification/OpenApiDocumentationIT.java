package pe.buildshield.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pe.buildshield.notification.support.NotificationIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La especificación OpenAPI es pública y documenta los endpoints de notification con su esquema de seguridad. */
@NotificationIntegrationTest
@AutoConfigureMockMvc
class OpenApiDocumentationIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void api_docs_are_public_and_describe_the_notification_endpoints() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/api/v1/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(spec.path("paths").fieldNames()).toIterable().contains("/api/v1/notifications",
                "/api/v1/notifications/unread-count", "/api/v1/notifications/{id}/read",
                "/api/v1/notifications/read-all", "/api/v1/notifications/recipient-settings",
                "/api/v1/notifications/recipient-settings/worksites/{worksiteId}",
                "/api/v1/notifications/recipient-settings/worksites/{worksiteId}/types/{type}");
        assertThat(spec.at("/components/securitySchemes/bearer/scheme").asText()).isEqualTo("bearer");
    }
}
