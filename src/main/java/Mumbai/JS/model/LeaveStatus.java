package Mumbai.JS.model;

/**
 * The states a leave request can be in.
 *
 * Allowed transitions (this is the whole "state machine"):
 *   PENDING  -> APPROVED   (by a manager, not the requester)
 *   PENDING  -> REJECTED   (by a manager, not the requester)
 *   PENDING  -> CANCELLED  (by the requester)
 *   APPROVED -> CANCELLED  (by the requester, only if the leave hasn't started yet)
 *
 * Anything else is rejected with 409 Conflict.
 */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
