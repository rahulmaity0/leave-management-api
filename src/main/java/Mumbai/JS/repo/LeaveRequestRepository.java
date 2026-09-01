package Mumbai.JS.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import Mumbai.JS.model.LeaveRequest;
import Mumbai.JS.model.LeaveStatus;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    // Derived queries: Spring Data reads the method name and writes the SQL for you.
    // "EmployeeId" resolves to the employee association's id column.
    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(int employeeId);

    List<LeaveRequest> findByStatusOrderByAppliedOnAsc(LeaveStatus status);

    /**
     * Does this employee already have a leave request that overlaps [startDate, endDate]?
     *
     * Two date ranges A and B overlap when A.start <= B.end AND A.end >= B.start.
     * That single condition covers every overlap case - partial at either end, and
     * one range fully containing the other - so no special-casing is needed.
     *
     * Only PENDING and APPROVED count; REJECTED and CANCELLED requests free the dates up.
     */
    @Query("""
            SELECT COUNT(l) > 0 FROM LeaveRequest l
            WHERE l.employee.id = :employeeId
              AND l.status IN :statuses
              AND l.startDate <= :endDate
              AND l.endDate >= :startDate
            """)
    boolean hasOverlappingLeave(@Param("employeeId") int employeeId,
            @Param("statuses") Collection<LeaveStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
