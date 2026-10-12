package pe.buildshield.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.application.NotificationQueryService;
import pe.buildshield.notification.application.RecipientSettingsService;
import pe.buildshield.notification.domain.model.Notification;
import pe.buildshield.notification.domain.model.NotificationPage;
import pe.buildshield.notification.domain.model.NotificationType;
import pe.buildshield.notification.shared.security.JwtTokenIssuer;
import pe.buildshield.notification.shared.security.RevokedTokenStore;
import pe.buildshield.notification.shared.tenant.TenantInfo;
import pe.buildshield.notification.support.WebSliceTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa web con la seguridad real: cualquier rol usa su bandeja; solo el administrador configura los destinatarios
 * (US45); sin token, 401.
 */
@WebMvcTest
@WebSliceTest
class RoleAuthorizationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtTokenIssuer issuer;

    @MockitoBean
    NotificationQueryService queries;

    @MockitoBean
    NotificationCommandService commands;

    @MockitoBean
    RevokedTokenStore revokedTokens;

    @Test
    void every_role_uses_its_inbox() throws Exception {
        UUID organization = UUID.randomUUID();
        Notification notification = Notification.create(organization, UUID.randomUUID(),
                NotificationType.DISPATCH_IN_TRANSIT, "Despacho en camino", "Sale hoy", "/reception/1", null,
                Instant.parse("2026-11-03T13:30:00Z"));
        when(queries.inbox(anyBoolean(), anyInt(), anyInt())).thenReturn(new NotificationQueryService.Inbox(
                new NotificationPage(List.of(notification), 0, 20, 1), 1));
        when(queries.unreadCount()).thenReturn(1L);
        UUID id = UUID.randomUUID();

        for (String role : List.of("ADMINISTRATOR", "WAREHOUSE_MANAGER", "SITE_MANAGER")) {
            String bearer = "Bearer " + issuer.issue(new TenantInfo(organization, UUID.randomUUID(), role)).value();
            mvc.perform(get("/api/v1/notifications").header("Authorization", bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].type").value("DISPATCH_IN_TRANSIT"))
                    .andExpect(jsonPath("$.items[0].readAt").isEmpty())
                    .andExpect(jsonPath("$.unreadCount").value(1))
                    .andExpect(jsonPath("$.total").value(1));
            mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
            mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", bearer))
                    .andExpect(status().isNoContent());
            mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", bearer))
                    .andExpect(status().isNoContent());
        }
        verify(commands, org.mockito.Mockito.times(3)).markRead(id);
    }

    @MockitoBean
    RecipientSettingsService recipientSettings;

    @Test
    void only_the_administrator_configures_recipients() throws Exception {
        UUID worksite = UUID.randomUUID();
        var typeSettings = new RecipientSettingsService.TypeSettings(NotificationType.DISPATCH_IN_TRANSIT,
                "Despacho en camino", true, "Encargados de obra asignados a la obra", List.of(), null, null);
        when(recipientSettings.list()).thenReturn(List.of());
        when(recipientSettings.forWorksite(worksite)).thenReturn(new RecipientSettingsService.WorksiteSettings(
                worksite, "Torre Norte", List.of(typeSettings), List.of()));
        when(recipientSettings.replace(eq(worksite), eq(NotificationType.DISPATCH_IN_TRANSIT), any()))
                .thenReturn(typeSettings);
        String base = "/api/v1/notifications/recipient-settings";
        String admin = bearer("ADMINISTRATOR");

        mvc.perform(get(base).header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get(base + "/worksites/" + worksite).header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.types[0].configured").value(true));
        mvc.perform(put(base + "/worksites/" + worksite + "/types/DISPATCH_IN_TRANSIT").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Despacho en camino"));
        mvc.perform(put(base + "/worksites/" + worksite + "/types/DISPATCH_IN_TRANSIT").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(base + "/worksites/" + worksite + "/types/NOPE").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(base + "/worksites/" + worksite + "/types/DISPATCH_IN_TRANSIT")
                .header("Authorization", admin)).andExpect(status().isNoContent());
        verify(recipientSettings).reset(worksite, NotificationType.DISPATCH_IN_TRANSIT);

        for (String role : List.of("WAREHOUSE_MANAGER", "SITE_MANAGER")) {
            mvc.perform(get(base + "/worksites/" + worksite).header("Authorization", bearer(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(put(base + "/worksites/" + worksite + "/types/DISPATCH_IN_TRANSIT")
                            .header("Authorization", bearer(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[]}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
    }

    private String bearer(String role) {
        return "Bearer " + issuer.issue(new TenantInfo(UUID.randomUUID(), UUID.randomUUID(), role)).value();
    }

    @Test
    void without_token_the_inbox_answers_401() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }
}
