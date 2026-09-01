package Mumbai.JS.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import Mumbai.JS.model.LeaveRequest;
import Mumbai.JS.model.LeaveStatus;
import Mumbai.JS.model.LeaveType;
import Mumbai.JS.model.employeemodel;

/**
 * Tests the queries against a real database (in-memory H2), not a mock.
 *
 * The overlap query is the one piece of logic that could look right in Java and still
 * be wrong in SQL, so it gets a full boundary-value table rather than a happy path.
 * @DataJpaTest spins up only the JPA layer and rolls back after every test, so the
 * tests can't leak state into each other.
 */
@DataJpaTest(showSql = false)
class LeaveRequestRepositoryTest {

    /** The leave already on the books that every case below is compared against. */
    private static final LocalDate BOOKED_START = LocalDate.of(2030, 10, 10);
    private static final LocalDate BOOKED_END = LocalDate.of(2030, 10, 15);

    private static final Set<LeaveStatus> BLOCKING = Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    @Autowired
    private LeaveRequestRepository leaveRepository;

    @Autowired
    private employeerepo employeeRepository;

    private employeemodel rahul;
    private employeemodel priya;

    @BeforeEach
    void setUp() {
        rahul = saveEmployee("Rahul");
        priya = saveEmployee("Priya");
    }

    private employeemodel saveEmployee(String name) {
        employeemodel e = new employeemodel();
        e.setName(name);
        e.setAddress("Mumbai");
        e.setGender('M');
        e.setLeaveBalance(20);
        return employeeRepository.save(e);
    }

    private LeaveRequest saveLeave(employeemodel employee, LocalDate start, LocalDate end, LeaveStatus status) {
        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setStatus(status);
        leave.setType(LeaveType.CASUAL);
        leave.setReason("test");
        leave.setAppliedOn(LocalDate.now());
        return leaveRepository.save(leave);
    }

    /**
     * The booked leave runs 10 Oct - 15 Oct. Each row asks "does this new range clash?".
     * The interesting rows are the four around the edges: 9th and 16th must NOT clash,
     * 10th and 15th must. Those are exactly where an inclusive/exclusive mix-up shows up.
     */
    @ParameterizedTest(name = "{2}: {0} to {1} -> overlaps={3}")
    @CsvSource({
            "2030-10-01, 2030-10-05, entirely before,              false",
            "2030-10-01, 2030-10-09, ends the day before it starts, false",
            "2030-10-01, 2030-10-10, touches the first booked day,  true",
            "2030-10-01, 2030-10-20, swallows the booked range,     true",
            "2030-10-12, 2030-10-13, sits entirely inside,          true",
            "2030-10-10, 2030-10-15, exactly the same range,        true",
            "2030-10-15, 2030-10-20, touches the last booked day,   true",
            "2030-10-16, 2030-10-20, starts the day after it ends,  false",
            "2030-10-20, 2030-10-25, entirely after,                false"
    })
    @DisplayName("detects overlapping date ranges at the boundaries")
    void detectsOverlapAtBoundaries(LocalDate newStart, LocalDate newEnd, String scenario, boolean expected) {
        saveLeave(rahul, BOOKED_START, BOOKED_END, LeaveStatus.APPROVED);

        boolean overlaps = leaveRepository.hasOverlappingLeave(rahul.getId(), BLOCKING, newStart, newEnd);

        assertThat(overlaps)
                .as("range %s to %s (%s)", newStart, newEnd, scenario)
                .isEqualTo(expected);
    }

    /**
     * A rejected or cancelled leave frees the dates back up, so it must not block a
     * new request. Only PENDING and APPROVED hold the dates.
     */
    @ParameterizedTest(name = "a {0} leave does not block the same dates")
    @CsvSource({ "REJECTED", "CANCELLED" })
    @DisplayName("ignores leave that was rejected or cancelled")
    void ignoresFreedUpLeave(LeaveStatus status) {
        saveLeave(rahul, BOOKED_START, BOOKED_END, status);

        boolean overlaps = leaveRepository.hasOverlappingLeave(rahul.getId(), BLOCKING, BOOKED_START, BOOKED_END);

        assertThat(overlaps).isFalse();
    }

    @Test
    @DisplayName("a pending leave does block the same dates")
    void pendingLeaveBlocks() {
        saveLeave(rahul, BOOKED_START, BOOKED_END, LeaveStatus.PENDING);

        assertThat(leaveRepository.hasOverlappingLeave(rahul.getId(), BLOCKING, BOOKED_START, BOOKED_END))
                .isTrue();
    }

    /** Priya booking the same week must not clash with Rahul's leave. */
    @Test
    @DisplayName("only looks at the given employee's own leave")
    void isScopedToOneEmployee() {
        saveLeave(rahul, BOOKED_START, BOOKED_END, LeaveStatus.APPROVED);

        assertThat(leaveRepository.hasOverlappingLeave(priya.getId(), BLOCKING, BOOKED_START, BOOKED_END))
                .isFalse();
    }

    @Test
    @DisplayName("returns an employee's leave newest first")
    void findsEmployeeLeaveSortedByStartDateDescending() {
        saveLeave(rahul, LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 3), LeaveStatus.APPROVED);
        saveLeave(rahul, LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 3), LeaveStatus.PENDING);
        saveLeave(rahul, LocalDate.of(2030, 3, 1), LocalDate.of(2030, 3, 3), LeaveStatus.REJECTED);
        saveLeave(priya, LocalDate.of(2030, 4, 1), LocalDate.of(2030, 4, 3), LeaveStatus.PENDING);

        List<LeaveRequest> leaves = leaveRepository.findByEmployeeIdOrderByStartDateDesc(rahul.getId());

        assertThat(leaves)
                .hasSize(3)
                .extracting(LeaveRequest::getStartDate)
                .containsExactly(
                        LocalDate.of(2030, 6, 1),
                        LocalDate.of(2030, 3, 1),
                        LocalDate.of(2030, 1, 1));
    }

    @Test
    @DisplayName("returns the pending queue oldest first, so the longest wait is handled first")
    void findsPendingQueueOldestFirst() {
        LeaveRequest older = saveLeave(rahul, LocalDate.of(2030, 6, 1), LocalDate.of(2030, 6, 3), LeaveStatus.PENDING);
        older.setAppliedOn(LocalDate.of(2030, 1, 1));

        LeaveRequest newer = saveLeave(priya, LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 3), LeaveStatus.PENDING);
        newer.setAppliedOn(LocalDate.of(2030, 2, 1));

        saveLeave(rahul, LocalDate.of(2030, 8, 1), LocalDate.of(2030, 8, 3), LeaveStatus.APPROVED);

        leaveRepository.flush();

        List<LeaveRequest> pending = leaveRepository.findByStatusOrderByAppliedOnAsc(LeaveStatus.PENDING);

        assertThat(pending)
                .extracting(LeaveRequest::getAppliedOn)
                .containsExactly(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 2, 1));
    }

    @Test
    @DisplayName("persists the status as readable text, not an ordinal number")
    void storesEnumAsString() {
        LeaveRequest saved = saveLeave(rahul, BOOKED_START, BOOKED_END, LeaveStatus.APPROVED);
        leaveRepository.flush();

        LeaveRequest reloaded = leaveRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(reloaded.getType()).isEqualTo(LeaveType.CASUAL);
    }
}
