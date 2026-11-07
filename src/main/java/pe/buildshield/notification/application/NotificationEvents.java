package pe.buildshield.notification.application;

/** Tipos de evento de los demás contextos que usa notification. Los demás se ignoran. */
public final class NotificationEvents {

    public static final String USER_CREATED = "UserCreated";
    public static final String USER_UPDATED = "UserUpdated";
    public static final String LOCATION_REGISTERED = "LocationRegistered";
    public static final String LOCATION_UPDATED = "LocationUpdated";
    public static final String STAFF_ASSIGNED = "StaffAssigned";
    public static final String STAFF_UNASSIGNED = "StaffUnassigned";
    public static final String DISPATCH_DEPARTED = "DispatchDeparted";
    public static final String DISPATCH_CANCELLED = "DispatchCancelled";
    public static final String TRANSIT_INCIDENT_REPORTED = "TransitIncidentReported";
    public static final String RECEPTION_CONFIRMED = "ReceptionConfirmed";
    public static final String DISCREPANCY_RESPONDED = "DiscrepancyResponded";
    public static final String DISCREPANCY_ESCALATED = "DiscrepancyEscalated";
    public static final String DISCREPANCY_RESOLVED = "DiscrepancyResolved";
    public static final String DISCREPANCY_ACCEPTED = "DiscrepancyAccepted";
    public static final String DISCREPANCY_OPENED = "DiscrepancyOpened";
    public static final String ORDER_APPROVED = "OrderApproved";
    public static final String STOCK_RESERVATION_REJECTED = "StockReservationRejected";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String SUBSCRIPTION_EXPIRED = "SubscriptionExpired";
    public static final String PASSWORD_RESET_REQUESTED = "PasswordResetRequested";

    private NotificationEvents() {
    }
}
