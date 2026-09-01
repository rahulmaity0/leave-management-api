package Mumbai.JS.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Unit tests for the leave business rules.
 *
 * No Spring context and no database here - the repositories are Mockito mocks, so
 * these run in milliseconds and fail for exactly one reason: a rule is wrong.
 * The database side of things is covered separately in LeaveRequestRepositoryTest.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    private static final int EMPLOYEE_ID = 1;
    private static final int MANAGER_ID = 2;

    @Mock
    private LeaveRequestRepository leaveRepository;

    @Mock
    private employeerepo employeeRepository;

    @InjectMocks
    private LeaveService leaveService;

    // ---- test data builders ----

    private employeemodel employee(int id, String name, int balance) {
        employeemodel e = new employeemodel();
        e.setId(id);
        e.setName(name);
        e.setLeaveBalance(balance);
        return e;
    }

    private LeaveRequest existingLeave(LeaveStatus status, employeemodel owner,
            LocalDate start, LocalDate end, LeaveType type) {
        LeaveRequest leave = new LeaveRequest();
        leave.setId(100L);
        leave.setEmployee(owner);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setType(type);
        leave.setStatus(status);
        leave.setReason("some reason");
        return leave;
    }

    /** save() in these tests just hands the entity straight back, like a real save would. */
    private void stubSaveReturningArgument() {
        when(leaveRepository.save(any(LeaveRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("Applying for leave")
    class ApplyingForLeave {

        private LeaveApplicationRequest request(LocalDate start, LocalDate end, LeaveType type) {
            return new LeaveApplicationRequest(EMPLOYEE_ID, start, end, type, "family function");
        }

        @Test
        @DisplayName("creates a PENDING request and does not touch the balance yet")
        void createsPendingRequest() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(emp));
            when(leaveRepository.hasOverlappingLeave(anyInt(), anyCollection(), any(), any()))
                    .thenReturn(false);
            stubSaveReturningArgument();

            LocalDate start = LocalDate.now().plusDays(5);
            LeaveResponse response = leaveService.apply(request(start, start.plusDays(2), LeaveType.CASUAL));

            assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
            assertThat(response.days()).isEqualTo(3);
            assertThat(response.appliedOn()).isEqualTo(LocalDate.now());
            // Balance is only spent on approval, so it must still be untouched.
            assertThat(emp.getLeaveBalance()).isEqualTo(20);
            assertThat(response.remainingBalance()).isEqualTo(20);
        }

        @Test
        @DisplayName("rejects an unknown employee with 404")
        void rejectsUnknownEmployee() {
            when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

            LocalDate start = LocalDate.now().plusDays(5);
            assertThatThrownBy(() -> leaveService.apply(request(start, start, LeaveType.CASUAL)))
                    .isInstanceOf(EmployeeNotFoundException.class);

            verify(leaveRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects an end date before the start date")
        void rejectsBackwardsDateRange() {
            when(employeeRepository.findById(EMPLOYEE_ID))
                    .thenReturn(Optional.of(employee(EMPLOYEE_ID, "Rahul", 20)));

            LocalDate start = LocalDate.now().plusDays(5);
            assertThatThrownBy(() -> leaveService.apply(request(start, start.minusDays(1), LeaveType.CASUAL)))
                    .isInstanceOf(InvalidLeaveRequestException.class)
                    .hasMessageContaining("endDate cannot be before startDate");

            verify(leaveRepository, never()).save(any());
        }

        @Test
        @DisplayName("rejects dates that clash with an existing pending or approved leave")
        void rejectsOverlappingLeave() {
            when(employeeRepository.findById(EMPLOYEE_ID))
                    .thenReturn(Optional.of(employee(EMPLOYEE_ID, "Rahul", 20)));
            when(leaveRepository.hasOverlappingLeave(anyInt(), anyCollection(), any(), any()))
                    .thenReturn(true);

            LocalDate start = LocalDate.now().plusDays(5);
            assertThatThrownBy(() -> leaveService.apply(request(start, start.plusDays(2), LeaveType.CASUAL)))
                    .isInstanceOf(LeaveConflictException.class)
                    .hasMessageContaining("overlapping");

            verify(leaveRepository, never()).save(any());
        }

        /**
         * Boundary test: with a balance of exactly 5, a 5-day request must pass and a
         * 6-day request must fail. Off-by-one errors in the day count show up right here.
         * Day count is inclusive of both ends, so plusDays(4) is 5 days.
         */
        @ParameterizedTest(name = "balance 5, requesting {0} day(s) -> allowed={1}")
        @CsvSource({
                "1, true",
                "4, true",
                "5, true",
                "6, false",
                "10, false"
        })
        @DisplayName("allows a request up to the exact remaining balance, and no further")
        void enforcesBalanceBoundary(int requestedDays, boolean allowed) {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 5);
            when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(emp));
            when(leaveRepository.hasOverlappingLeave(anyInt(), anyCollection(), any(), any()))
                    .thenReturn(false);
            if (allowed) {
                stubSaveReturningArgument();
            }

            LocalDate start = LocalDate.now().plusDays(5);
            LocalDate end = start.plusDays(requestedDays - 1);

            if (allowed) {
                assertThat(leaveService.apply(request(start, end, LeaveType.CASUAL)).days())
                        .isEqualTo(requestedDays);
            } else {
                assertThatThrownBy(() -> leaveService.apply(request(start, end, LeaveType.CASUAL)))
                        .isInstanceOf(InvalidLeaveRequestException.class)
                        .hasMessageContaining("balance");
            }
        }

        @Test
        @DisplayName("allows UNPAID leave even with a zero balance")
        void unpaidLeaveIgnoresBalance() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 0);
            when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(emp));
            when(leaveRepository.hasOverlappingLeave(anyInt(), anyCollection(), any(), any()))
                    .thenReturn(false);
            stubSaveReturningArgument();

            LocalDate start = LocalDate.now().plusDays(5);
            LeaveResponse response = leaveService.apply(request(start, start.plusDays(9), LeaveType.UNPAID));

            assertThat(response.status()).isEqualTo(LeaveStatus.PENDING);
            assertThat(response.days()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Approving leave")
    class ApprovingLeave {

        private final LeaveDecisionRequest decision = new LeaveDecisionRequest(MANAGER_ID, "approved, enjoy");

        @Test
        @DisplayName("marks it APPROVED, records the approver and deducts the balance")
        void approvesAndDeductsBalance() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            employeemodel manager = employee(MANAGER_ID, "Priya", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.PENDING, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
            when(employeeRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager));

            LeaveResponse response = leaveService.approve(100L, decision);

            assertThat(response.status()).isEqualTo(LeaveStatus.APPROVED);
            assertThat(response.approverName()).isEqualTo("Priya");
            assertThat(response.decisionComment()).isEqualTo("approved, enjoy");
            assertThat(response.decidedOn()).isEqualTo(LocalDate.now());
            assertThat(emp.getLeaveBalance()).isEqualTo(17);
        }

        @Test
        @DisplayName("does not deduct balance for UNPAID leave")
        void unpaidApprovalDoesNotDeduct() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.PENDING, emp, start, start.plusDays(4), LeaveType.UNPAID);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
            when(employeeRepository.findById(MANAGER_ID))
                    .thenReturn(Optional.of(employee(MANAGER_ID, "Priya", 20)));

            leaveService.approve(100L, decision);

            assertThat(emp.getLeaveBalance()).isEqualTo(20);
        }

        /**
         * Separation of duties: the person deciding must not be the person who applied.
         */
        @Test
        @DisplayName("refuses to let an employee approve their own request")
        void refusesSelfApproval() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.PENDING, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
            when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(emp));

            assertThatThrownBy(() -> leaveService.approve(100L, new LeaveDecisionRequest(EMPLOYEE_ID, "ok")))
                    .isInstanceOf(InvalidLeaveRequestException.class)
                    .hasMessageContaining("cannot approve their own");

            assertThat(leave.getStatus()).isEqualTo(LeaveStatus.PENDING);
            assertThat(emp.getLeaveBalance()).isEqualTo(20);
        }

        /**
         * The state machine: only PENDING can be decided on. Trying to approve anything
         * else is a 409, never a silent success.
         */
        @ParameterizedTest(name = "cannot approve a request that is already {0}")
        @EnumSource(value = LeaveStatus.class, names = { "APPROVED", "REJECTED", "CANCELLED" })
        @DisplayName("refuses to approve a request that is no longer PENDING")
        void refusesNonPendingRequest(LeaveStatus currentStatus) {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(currentStatus, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

            assertThatThrownBy(() -> leaveService.approve(100L, decision))
                    .isInstanceOf(LeaveConflictException.class)
                    .hasMessageContaining("Only a PENDING request");

            assertThat(emp.getLeaveBalance()).isEqualTo(20);
        }

        /**
         * Balance is checked again at approval time. Between applying and approving, other
         * leaves may have been approved and eaten the balance.
         */
        @Test
        @DisplayName("re-checks the balance at approval time, not just at apply time")
        void rechecksBalanceOnApproval() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 1);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.PENDING, emp, start, start.plusDays(4), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
            when(employeeRepository.findById(MANAGER_ID))
                    .thenReturn(Optional.of(employee(MANAGER_ID, "Priya", 20)));

            assertThatThrownBy(() -> leaveService.approve(100L, decision))
                    .isInstanceOf(InvalidLeaveRequestException.class)
                    .hasMessageContaining("no longer has enough balance");

            assertThat(leave.getStatus()).isEqualTo(LeaveStatus.PENDING);
            assertThat(emp.getLeaveBalance()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns 404 for a leave id that does not exist")
        void unknownLeaveId() {
            when(leaveRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leaveService.approve(999L, decision))
                    .isInstanceOf(LeaveNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Rejecting leave")
    class RejectingLeave {

        @Test
        @DisplayName("marks it REJECTED and leaves the balance alone")
        void rejectsWithoutTouchingBalance() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.PENDING, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
            when(employeeRepository.findById(MANAGER_ID))
                    .thenReturn(Optional.of(employee(MANAGER_ID, "Priya", 20)));

            LeaveResponse response = leaveService.reject(100L, new LeaveDecisionRequest(MANAGER_ID, "team is short"));

            assertThat(response.status()).isEqualTo(LeaveStatus.REJECTED);
            assertThat(response.decisionComment()).isEqualTo("team is short");
            assertThat(emp.getLeaveBalance()).isEqualTo(20);
        }

        @Test
        @DisplayName("refuses to let an employee reject their own request")
        void refusesSelfRejection() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.PENDING, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));
            when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(emp));

            assertThatThrownBy(() -> leaveService.reject(100L, new LeaveDecisionRequest(EMPLOYEE_ID, "no")))
                    .isInstanceOf(InvalidLeaveRequestException.class);
        }
    }

    @Nested
    @DisplayName("Cancelling leave")
    class CancellingLeave {

        @Test
        @DisplayName("cancels a PENDING request without changing the balance")
        void cancelsPending() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.PENDING, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

            LeaveResponse response = leaveService.cancel(100L, EMPLOYEE_ID);

            assertThat(response.status()).isEqualTo(LeaveStatus.CANCELLED);
            assertThat(emp.getLeaveBalance()).isEqualTo(20);
        }

        /** Cancelling an approved future leave has to give the days back. */
        @Test
        @DisplayName("credits the balance back when cancelling an approved future leave")
        void cancellingApprovedFutureLeaveRefundsBalance() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 17);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.APPROVED, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

            leaveService.cancel(100L, EMPLOYEE_ID);

            assertThat(leave.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
            assertThat(emp.getLeaveBalance()).isEqualTo(20);
        }

        @Test
        @DisplayName("refuses to cancel approved leave that has already started")
        void refusesCancellingStartedLeave() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 17);
            LeaveRequest leave = existingLeave(LeaveStatus.APPROVED, emp,
                    LocalDate.now().minusDays(1), LocalDate.now().plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

            assertThatThrownBy(() -> leaveService.cancel(100L, EMPLOYEE_ID))
                    .isInstanceOf(LeaveConflictException.class)
                    .hasMessageContaining("once it has started");

            assertThat(emp.getLeaveBalance()).isEqualTo(17);
        }

        @Test
        @DisplayName("boundary: leave starting today counts as already started")
        void leaveStartingTodayCannotBeCancelled() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 17);
            LeaveRequest leave = existingLeave(LeaveStatus.APPROVED, emp,
                    LocalDate.now(), LocalDate.now().plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

            assertThatThrownBy(() -> leaveService.cancel(100L, EMPLOYEE_ID))
                    .isInstanceOf(LeaveConflictException.class);
        }

        @Test
        @DisplayName("refuses to let someone cancel a colleague's leave")
        void refusesCancellingSomeoneElsesLeave() {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(LeaveStatus.PENDING, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

            assertThatThrownBy(() -> leaveService.cancel(100L, MANAGER_ID))
                    .isInstanceOf(InvalidLeaveRequestException.class)
                    .hasMessageContaining("only be cancelled by the employee who applied");

            assertThat(leave.getStatus()).isEqualTo(LeaveStatus.PENDING);
        }

        @ParameterizedTest(name = "cannot cancel a request that is already {0}")
        @EnumSource(value = LeaveStatus.class, names = { "REJECTED", "CANCELLED" })
        @DisplayName("refuses to cancel an already-decided request")
        void refusesCancellingDecidedRequest(LeaveStatus currentStatus) {
            employeemodel emp = employee(EMPLOYEE_ID, "Rahul", 20);
            LocalDate start = LocalDate.now().plusDays(5);
            LeaveRequest leave = existingLeave(currentStatus, emp, start, start.plusDays(2), LeaveType.CASUAL);

            when(leaveRepository.findById(100L)).thenReturn(Optional.of(leave));

            assertThatThrownBy(() -> leaveService.cancel(100L, EMPLOYEE_ID))
                    .isInstanceOf(LeaveConflictException.class)
                    .hasMessageContaining("cannot be cancelled");
        }
    }

    @Nested
    @DisplayName("Reading leave")
    class ReadingLeave {

        @Test
        @DisplayName("404s when listing leave for an employee who does not exist")
        void listingUnknownEmployeeFails() {
            when(employeeRepository.existsById(eq(EMPLOYEE_ID))).thenReturn(false);

            assertThatThrownBy(() -> leaveService.getLeavesForEmployee(EMPLOYEE_ID))
                    .isInstanceOf(EmployeeNotFoundException.class);
        }
    }
}
