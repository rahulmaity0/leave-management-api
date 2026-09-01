package Mumbai.JS.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What a manager sends when approving or rejecting a leave request.
 *
 * approverId is here because there is no login yet. Once you add JWT auth this
 * field goes away and the approver is read from the authenticated principal instead.
 */
public record LeaveDecisionRequest(

        @NotNull(message = "approverId is required")
        Integer approverId,

        @Size(max = 500, message = "comment must be at most 500 characters")
        String comment) {
}
