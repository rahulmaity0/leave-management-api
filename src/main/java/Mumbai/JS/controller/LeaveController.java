package Mumbai.JS.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import Mumbai.JS.dto.LeaveApplicationRequest;
import Mumbai.JS.dto.LeaveDecisionRequest;
import Mumbai.JS.dto.LeaveResponse;
import Mumbai.JS.model.LeaveStatus;
import Mumbai.JS.service.LeaveService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    /** POST /leaves - employee applies for leave. 201 Created. */
    @PostMapping
    public ResponseEntity<LeaveResponse> apply(@Valid @RequestBody LeaveApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(leaveService.apply(request));
    }

    /** GET /leaves/{id} */
    @GetMapping("/{id}")
    public LeaveResponse getLeave(@PathVariable Long id) {
        return leaveService.getLeave(id);
    }

    /**
     * GET /leaves?status=PENDING - the manager's pending-approvals inbox.
     * Defaults to PENDING because that is the queue people actually want.
     */
    @GetMapping
    public List<LeaveResponse> getByStatus(
            @RequestParam(defaultValue = "PENDING") LeaveStatus status) {
        return leaveService.getLeavesByStatus(status);
    }

    /** GET /leaves/employee/{employeeId} - one employee's leave history. */
    @GetMapping("/employee/{employeeId}")
    public List<LeaveResponse> getForEmployee(@PathVariable int employeeId) {
        return leaveService.getLeavesForEmployee(employeeId);
    }

    // PATCH, not PUT: these change one field (the status), they don't replace the resource.

    /** PATCH /leaves/{id}/approve */
    @PatchMapping("/{id}/approve")
    public LeaveResponse approve(@PathVariable Long id, @Valid @RequestBody LeaveDecisionRequest decision) {
        return leaveService.approve(id, decision);
    }

    /** PATCH /leaves/{id}/reject */
    @PatchMapping("/{id}/reject")
    public LeaveResponse reject(@PathVariable Long id, @Valid @RequestBody LeaveDecisionRequest decision) {
        return leaveService.reject(id, decision);
    }

    /** PATCH /leaves/{id}/cancel?employeeId=1 - the requester withdraws it. */
    @PatchMapping("/{id}/cancel")
    public LeaveResponse cancel(@PathVariable Long id, @RequestParam int employeeId) {
        return leaveService.cancel(id, employeeId);
    }
}
