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

    static final String WORKSITE_ID = "3a4b5c6d-7e8f-4a9b-8c0d-1e2f3a4b5c6d";

    static final String TYPE_SETTINGS = """
            {
              "type": "DISPATCH_IN_TRANSIT",
              "label": "Despacho en camino",
              "configured": true,
              "defaultAudience": "Encargados de obra asignados a la obra",
              "recipients": [
                {
                  "userId": "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
                  "fullName": "Luis Ramos",
                  "email": "luis.ramos@obra.pe",
                  "role": "SITE_MANAGER",
                  "active": true
                },
                {
                  "userId": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
                  "fullName": "Ana Torres",
                  "email": "ana.torres@obra.pe",
                  "role": "ADMINISTRATOR",
                  "active": true
                }
              ],
              "updatedBy": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
              "updatedAt": "2026-11-02T15:00:00Z"
            }""";

    static final String WORKSITE_SETTINGS = """
            {
              "worksiteId": "3a4b5c6d-7e8f-4a9b-8c0d-1e2f3a4b5c6d",
              "worksiteName": "Torre Norte",
              "types": [
                {
                  "type": "DISPATCH_IN_TRANSIT",
                  "label": "Despacho en camino",
                  "configured": true,
                  "defaultAudience": "Encargados de obra asignados a la obra",
                  "recipients": [
                    {"userId": "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f", "fullName": "Luis Ramos",
                     "email": "luis.ramos@obra.pe", "role": "SITE_MANAGER", "active": true}
                  ],
                  "updatedBy": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
                  "updatedAt": "2026-11-02T15:00:00Z"
                },
                {
                  "type": "TRANSIT_INCIDENT",
                  "label": "Incidencia en ruta",
                  "configured": false,
                  "defaultAudience": "Encargados de obra asignados a la obra y administradores",
                  "recipients": [],
                  "updatedBy": null,
                  "updatedAt": null
                }
              ],
              "candidates": [
                {"userId": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d", "fullName": "Ana Torres",
                 "email": "ana.torres@obra.pe", "role": "ADMINISTRATOR", "active": true},
                {"userId": "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f", "fullName": "Luis Ramos",
                 "email": "luis.ramos@obra.pe", "role": "SITE_MANAGER", "active": true}
              ]
            }""";

    static final String RECIPIENT_SETTINGS = """
            [
              {
                "worksiteId": "3a4b5c6d-7e8f-4a9b-8c0d-1e2f3a4b5c6d",
                "worksiteName": "Torre Norte",
                "setting": {
                  "type": "DISPATCH_IN_TRANSIT",
                  "label": "Despacho en camino",
                  "configured": true,
                  "defaultAudience": "Encargados de obra asignados a la obra",
                  "recipients": [
                    {"userId": "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f", "fullName": "Luis Ramos",
                     "email": "luis.ramos@obra.pe", "role": "SITE_MANAGER", "active": true}
                  ],
                  "updatedBy": "8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d",
                  "updatedAt": "2026-11-02T15:00:00Z"
                }
              }
            ]""";

    private NotificationApiExamples() {
    }
}
