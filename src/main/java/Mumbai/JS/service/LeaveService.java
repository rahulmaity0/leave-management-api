package Mumbai.JS.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import Mumbai.JS.dto.LeaveApplicationRequest;
import Mumbai.JS.dto.LeaveDecisionRequest;
import Mumbai.JS.dto.LeaveResponse;
import Mumbai.JS.exception.EmployeeNotFoundException;
import Mumbai.JS.exception.InvalidLeaveRequestException;
import Mumbai.JS.exception.LeaveConflictException;
import Mumbai.JS.exception.LeaveNotFoundException;
import Mumbai.JS.model.LeaveRequest;
import Mumbai.JS.model.LeaveStatus;
import Mumbai.JS.model.LeaveType;
import Mumbai.JS.model.employeemodel;
import Mumbai.JS.repo.LeaveRequestRepository;
import Mumbai.JS.repo.employeerepo;

/**
 * All the leave business rules live here. Controllers only translate HTTP,
 * repositories only talk to the database.
 */
@Service
public class LeaveService {

    /** Statuses that still "hold" the dates, so a new request can't overlap them. */
    private static final Set<LeaveStatus> BLOCKING_STATUSES = Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveRequestRepository leaveRepository;
    private final employeerepo employeeRepository;

    public LeaveService(LeaveRequestRepository leaveRepository, employeerepo employeeRepository) {
        this.leaveRepository = leaveRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Rule 1: end date can't be before start date.
     * Rule 2: the request can't overlap an existing PENDING/APPROVED request.
     * Rule 3: paid leave can't exceed the employee's remaining balance.
     * Balance is NOT deducted here - only on approval.
     */
    @Transactional
    public LeaveResponse apply(LeaveApplicationRequest request) {
        employeemodel employee = findEmployee(request.employeeId());

        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidLeaveRequestException("endDate cannot be before startDate");
        }

        long days = daysBetween(request.startDate(), request.endDate());

        if (leaveRepository.hasOverlappingLeave(employee.getId(), BLOCKING_STATUSES,
                request.startDate(), request.endDate())) {
            throw new LeaveConflictException(
                    "Employee already has a pending or approved leave overlapping these dates");
        }

        // UNPAID leave doesn't draw from the balance, so it skips this check.
        if (request.type() != LeaveType.UNPAID && days > employee.getLeaveBalance()) {
            throw new InvalidLeaveRequestException("Requested " + days
                    + " day(s) but only " + employee.getLeaveBalance() + " day(s) of balance remain");
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setType(request.type());
        leave.setReason(request.reason());
        leave.setStatus(LeaveStatus.PENDING);
        leave.setAppliedOn(LocalDate.now());

        return LeaveResponse.from(leaveRepository.save(leave), days);
    }

    /**
     * Rule 4: only a PENDING request can be decided on.
     * Rule 5: nobody approves their own leave (separation of duties).
     * Rule 6: balance is re-checked at approval time, because other leaves may have
     *         been approved since this one was applied for.
     */
    @Transactional
    public LeaveResponse approve(Long leaveId, LeaveDecisionRequest decision) {
        LeaveRequest leave = findLeave(leaveId);
        requirePending(leave, "approved");

        employeemodel approver = findEmployee(decision.approverId());
        if (approver.getId() == leave.getEmployee().getId()) {
            throw new InvalidLeaveRequestException("An employee cannot approve their own leave request");
        }

        employeemodel employee = leave.getEmployee();
        long days = daysBetween(leave.getStartDate(), leave.getEndDate());

        if (leave.getType() != LeaveType.UNPAID) {
            if (days > employee.getLeaveBalance()) {
                throw new InvalidLeaveRequestException("Employee no longer has enough balance: needs "
                        + days + " day(s), has " + employee.getLeaveBalance());
            }
            employee.setLeaveBalance(employee.getLeaveBalance() - (int) days);
        }

        leave.setStatus(LeaveStatus.APPROVED);
        leave.setApprover(approver);
        leave.setDecisionComment(decision.comment());
        leave.setDecidedOn(LocalDate.now());

        // No save() call needed. Inside @Transactional both entities are managed by the
        // persistence context, so Hibernate detects the changes and flushes them on commit.
        // Either both the status change and the balance deduction land, or neither does.
        return LeaveResponse.from(leave, days);
    }

    @Transactional
    public LeaveResponse reject(Long leaveId, LeaveDecisionRequest decision) {
        LeaveRequest leave = findLeave(leaveId);
        requirePending(leave, "rejected");

        employeemodel approver = findEmployee(decision.approverId());
        if (approver.getId() == leave.getEmployee().getId()) {
            throw new InvalidLeaveRequestException("An employee cannot reject their own leave request");
        }

        leave.setStatus(LeaveStatus.REJECTED);
        leave.setApprover(approver);
        leave.setDecisionComment(decision.comment());
        leave.setDecidedOn(LocalDate.now());

        // Rejected leave never touched the balance, so there is nothing to credit back.
        return LeaveResponse.from(leave, daysBetween(leave.getStartDate(), leave.getEndDate()));
    }

    /**
     * Rule 7: you can only cancel your own request.
     * Rule 8: a PENDING request can always be cancelled. An APPROVED one can only be
     *         cancelled before it starts - and cancelling it credits the balance back.
     */
    @Transactional
    public LeaveResponse cancel(Long leaveId, int employeeId) {
        LeaveRequest leave = findLeave(leaveId);

        if (leave.getEmployee().getId() != employeeId) {
            throw new InvalidLeaveRequestException("A leave request can only be cancelled by the employee who applied");
        }

        long days = daysBetween(leave.getStartDate(), leave.getEndDate());

        switch (leave.getStatus()) {
            case PENDING -> {
                // Nothing was deducted yet.
            }
            case APPROVED -> {
                if (!leave.getStartDate().isAfter(LocalDate.now())) {
                    throw new LeaveConflictException("Approved leave cannot be cancelled once it has started");
                }
                if (leave.getType() != LeaveType.UNPAID) {
                    employeemodel employee = leave.getEmployee();
                    employee.setLeaveBalance(employee.getLeaveBalance() + (int) days);
                }
            }
            default -> throw new LeaveConflictException(
                    "Leave request is already " + leave.getStatus() + " and cannot be cancelled");
        }

        leave.setStatus(LeaveStatus.CANCELLED);
        leave.setDecidedOn(LocalDate.now());
        return LeaveResponse.from(leave, days);
    }

    @Transactional(readOnly = true)
    public LeaveResponse getLeave(Long leaveId) {
        LeaveRequest leave = findLeave(leaveId);
        return LeaveResponse.from(leave, daysBetween(leave.getStartDate(), leave.getEndDate()));
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> getLeavesForEmployee(int employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EmployeeNotFoundException("No employee found with id " + employeeId);
        }
        return leaveRepository.findByEmployeeIdOrderByStartDateDesc(employeeId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** The manager's inbox: everything still waiting on a decision, oldest first. */
    @Transactional(readOnly = true)
    public List<LeaveResponse> getLeavesByStatus(LeaveStatus status) {
        return leaveRepository.findByStatusOrderByAppliedOnAsc(status).stream()
                .map(this::toResponse)
                .toList();
    }

    // ---- helpers ----

    private LeaveResponse toResponse(LeaveRequest leave) {
        return LeaveResponse.from(leave, daysBetween(leave.getStartDate(), leave.getEndDate()));
    }

    /** Inclusive of both ends: 5th to 5th is 1 day, 5th to 7th is 3 days. */
    private long daysBetween(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    private void requirePending(LeaveRequest leave, String action) {
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new LeaveConflictException("Only a PENDING request can be " + action
                    + "; this one is already " + leave.getStatus());
        }
    }

    private LeaveRequest findLeave(Long leaveId) {
        return leaveRepository.findById(leaveId)
                .orElseThrow(() -> new LeaveNotFoundException("No leave request found with id " + leaveId));
    }

    private employeemodel findEmployee(int employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException("No employee found with id " + employeeId));
    }
}
