package Mumbai.JS.dto;

import java.time.LocalDate;

import Mumbai.JS.model.LeaveRequest;
import Mumbai.JS.model.LeaveStatus;
import Mumbai.JS.model.LeaveType;

/**
 * What the API sends back for a leave request.
 *
 * Returning this instead of the LeaveRequest entity keeps the database shape out of
 * the API contract, and avoids serialising the lazy employee/approver associations.
 */
public record LeaveResponse(
        Long id,
        int employeeId,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate,
        long days,
        LeaveType type,
        LeaveStatus status,
        String reason,
        String approverName,
        String decisionComment,
        LocalDate appliedOn,
        LocalDate decidedOn,
        int remainingBalance) {

    public static LeaveResponse from(LeaveRequest leave, long days) {
        return new LeaveResponse(
                leave.getId(),
                leave.getEmployee().getId(),
                leave.getEmployee().getName(),
                leave.getStartDate(),
                leave.getEndDate(),
                days,
                leave.getType(),
                leave.getStatus(),
                leave.getReason(),
                leave.getApprover() == null ? null : leave.getApprover().getName(),
                leave.getDecisionComment(),
                leave.getAppliedOn(),
                leave.getDecidedOn(),
                leave.getEmployee().getLeaveBalance());
    }
}
