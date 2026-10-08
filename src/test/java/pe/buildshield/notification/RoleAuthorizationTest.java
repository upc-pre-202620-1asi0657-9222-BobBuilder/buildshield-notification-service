package pe.buildshield.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.buildshield.notification.application.NotificationCommandService;
import pe.buildshield.notification.application.NotificationQueryService;
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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Capa web con la seguridad real: cualquier rol usa su bandeja; sin token, 401. */
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

    @Test
    void without_token_the_inbox_answers_401() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }
}
