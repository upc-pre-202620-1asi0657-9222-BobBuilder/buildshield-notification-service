package pe.buildshield.notification.interfaces.rest;

/** Ejemplos de respuesta y de parámetros para la documentación OpenAPI de notification-service. */
final class NotificationApiExamples {

    static final String NOTIFICATION_ID = "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f";

    static final String INBOX = """
            {
              "items": [
                {
                  "id": "2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f",
                  "type": "DISPATCH_IN_TRANSIT",
                  "title": "Despacho en camino a Torre Norte",
                  "body": "El despacho MAN-20261103-0001 salió de Almacén Central hacia Torre Norte. Prepárate para recibirlo.",
                  "link": "/reception/6f7a8b9c-0d1e-4f2a-8b3c-4d5e6f7a8b9c",
                  "createdAt": "2026-11-03T13:30:02Z",
                  "readAt": null
                }
              ],
              "unreadCount": 1,
              "page": 0,
              "size": 20,
              "total": 1
            }""";

    static final String UNREAD_COUNT = """
            {"unreadCount": 3}""";

    private NotificationApiExamples() {
    }
}
