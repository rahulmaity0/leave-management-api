package Mumbai.JS.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import Mumbai.JS.dto.LeaveApplicationRequest;
import Mumbai.JS.dto.LeaveDecisionRequest;
import Mumbai.JS.dto.LeaveResponse;
import Mumbai.JS.exception.EmployeeNotFoundException;
import Mumbai.JS.exception.InvalidLeaveRequestException;
import Mumbai.JS.exception.LeaveConflictException;
import Mumbai.JS.exception.LeaveNotFoundException;
import Mumbai.JS.model.LeaveStatus;
import Mumbai.JS.model.LeaveType;
import Mumbai.JS.service.LeaveService;

/**
 * Tests the HTTP layer only: routing, request validation, status codes and the JSON
 * shape. The service is mocked, because whether the rules are right is already
 * covered in LeaveServiceTest - here we only care that a given outcome becomes the
 * right HTTP response.
 *
 * @WebMvcTest loads the controller plus the @RestControllerAdvice, and nothing else -
 * no database, no service beans - so it starts fast.
 */
@WebMvcTest(LeaveController.class)
class LeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaveService leaveService;

    /** Dates are computed rather than hardcoded so @FutureOrPresent doesn't fail next year. */
    private static final LocalDate START = LocalDate.now().plusDays(10);
    private static final LocalDate END = LocalDate.now().plusDays(12);

    private String applyJson(String employeeId, String start, String end, String type, String reason) {
        return """
                {
                  "employeeId": %s,
                  "startDate": "%s",
                  "endDate": "%s",
                  "type": "%s",
                  "reason": "%s"
                }
                """.formatted(employeeId, start, end, type, reason);
    }

    private String validApplyJson() {
        return applyJson("1", START.toString(), END.toString(), "CASUAL", "family function");
    }

    private LeaveResponse sampleResponse(LeaveStatus status) {
        return new LeaveResponse(100L, 1, "Rahul", START, END, 3, LeaveType.CASUAL, status,
                "family function", status == LeaveStatus.PENDING ? null : "Priya",
                null, LocalDate.now(), null, 17);
    }

    // ---- POST /leaves ----

    @Test
    @DisplayName("POST /leaves returns 201 with the created request")
    void applyReturnsCreated() throws Exception {
        when(leaveService.apply(any(LeaveApplicationRequest.class)))
                .thenReturn(sampleResponse(LeaveStatus.PENDING));

        mockMvc.perform(post("/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApplyJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.employeeName").value("Rahul"))
                .andExpect(jsonPath("$.days").value(3));
    }

    @Test
    @DisplayName("POST /leaves returns 400 and names the offending field when reason is blank")
    void applyRejectsBlankReason() throws Exception {
        mockMvc.perform(post("/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(applyJson("1", START.toString(), END.toString(), "CASUAL", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.reason").exists());

        // Validation must fail before the service is ever called.
        verify(leaveService, never()).apply(any());
    }

    @Test
    @DisplayName("POST /leaves returns 400 when the start date is in the past")
    void applyRejectsPastStartDate() throws Exception {
        String past = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(post("/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(applyJson("1", past, END.toString(), "CASUAL", "family function")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startDate").exists());

        verify(leaveService, never()).apply(any());
    }

    @Test
    @DisplayName("POST /leaves returns 400 when employeeId is missing")
    void applyRejectsMissingEmployeeId() throws Exception {
        mockMvc.perform(post("/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(applyJson("null", START.toString(), END.toString(), "CASUAL", "family function")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.employeeId").exists());
    }

    @Test
    @DisplayName("POST /leaves returns 400 for a leave type that does not exist")
    void applyRejectsUnknownLeaveType() throws Exception {
        mockMvc.perform(post("/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(applyJson("1", START.toString(), END.toString(), "HOLIDAY", "family function")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /leaves maps a missing employee to 404")
    void applyMapsUnknownEmployeeTo404() throws Exception {
        when(leaveService.apply(any())).thenThrow(new EmployeeNotFoundException("No employee found with id 1"));

        mockMvc.perform(post("/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApplyJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No employee found with id 1"));
    }

    /**
     * The distinction that matters: a broken business rule is 400, a clash with the
     * current state is 409. These two tests pin that down.
     */
    @Test
    @DisplayName("POST /leaves maps an overlapping request to 409 Conflict")
    void applyMapsOverlapTo409() throws Exception {
        when(leaveService.apply(any())).thenThrow(new LeaveConflictException("overlapping dates"));

        mockMvc.perform(post("/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApplyJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /leaves maps an insufficient balance to 400 Bad Request")
    void applyMapsInsufficientBalanceTo400() throws Exception {
        when(leaveService.apply(any())).thenThrow(new InvalidLeaveRequestException("not enough balance"));

        mockMvc.perform(post("/leaves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApplyJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---- PATCH /leaves/{id}/approve and /reject ----

    @Test
    @DisplayName("PATCH /leaves/{id}/approve returns 200 with the approved request")
    void approveReturnsOk() throws Exception {
        when(leaveService.approve(anyLong(), any(LeaveDecisionRequest.class)))
                .thenReturn(sampleResponse(LeaveStatus.APPROVED));

        mockMvc.perform(patch("/leaves/100/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "approverId": 2, "comment": "approved" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approverName").value("Priya"))
                .andExpect(jsonPath("$.remainingBalance").value(17));
    }

    @Test
    @DisplayName("PATCH /leaves/{id}/approve returns 400 when approverId is missing")
    void approveRequiresApproverId() throws Exception {
        mockMvc.perform(patch("/leaves/100/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "comment": "approved" }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.approverId").exists());

        verify(leaveService, never()).approve(anyLong(), any());
    }

    @Test
    @DisplayName("PATCH /leaves/{id}/approve returns 409 when it is no longer PENDING")
    void approveOnDecidedRequestReturns409() throws Exception {
        when(leaveService.approve(anyLong(), any()))
                .thenThrow(new LeaveConflictException("Only a PENDING request can be approved"));

        mockMvc.perform(patch("/leaves/100/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "approverId": 2 }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a PENDING request can be approved"));
    }

    @Test
    @DisplayName("PATCH /leaves/{id}/approve returns 404 for an unknown leave id")
    void approveUnknownLeaveReturns404() throws Exception {
        when(leaveService.approve(anyLong(), any()))
                .thenThrow(new LeaveNotFoundException("No leave request found with id 999"));

        mockMvc.perform(patch("/leaves/999/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "approverId": 2 }
                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /leaves/{id}/reject returns 200 with the rejected request")
    void rejectReturnsOk() throws Exception {
        when(leaveService.reject(anyLong(), any()))
                .thenReturn(sampleResponse(LeaveStatus.REJECTED));

        mockMvc.perform(patch("/leaves/100/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "approverId": 2, "comment": "team is short" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ---- PATCH /leaves/{id}/cancel ----

    @Test
    @DisplayName("PATCH /leaves/{id}/cancel returns 200 with the cancelled request")
    void cancelReturnsOk() throws Exception {
        when(leaveService.cancel(anyLong(), anyInt()))
                .thenReturn(sampleResponse(LeaveStatus.CANCELLED));

        mockMvc.perform(patch("/leaves/100/cancel").param("employeeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PATCH /leaves/{id}/cancel returns 400 when employeeId is not supplied")
    void cancelRequiresEmployeeId() throws Exception {
        mockMvc.perform(patch("/leaves/100/cancel"))
                .andExpect(status().isBadRequest());
    }

    // ---- GET ----

    @Test
    @DisplayName("GET /leaves defaults to the PENDING queue")
    void listDefaultsToPending() throws Exception {
        when(leaveService.getLeavesByStatus(LeaveStatus.PENDING))
                .thenReturn(java.util.List.of(sampleResponse(LeaveStatus.PENDING)));

        mockMvc.perform(get("/leaves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(leaveService).getLeavesByStatus(LeaveStatus.PENDING);
    }

    @Test
    @DisplayName("GET /leaves?status=APPROVED filters by the given status")
    void listFiltersByStatus() throws Exception {
        when(leaveService.getLeavesByStatus(LeaveStatus.APPROVED))
                .thenReturn(java.util.List.of(sampleResponse(LeaveStatus.APPROVED)));

        mockMvc.perform(get("/leaves").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));

        verify(leaveService).getLeavesByStatus(LeaveStatus.APPROVED);
    }

    @Test
    @DisplayName("GET /leaves/employee/{id} returns that employee's history")
    void listForEmployee() throws Exception {
        when(leaveService.getLeavesForEmployee(1))
                .thenReturn(java.util.List.of(sampleResponse(LeaveStatus.APPROVED)));

        mockMvc.perform(get("/leaves/employee/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeId").value(1));
    }

    @Test
    @DisplayName("GET /leaves/{id} returns 404 for an unknown id")
    void getUnknownLeaveReturns404() throws Exception {
        when(leaveService.getLeave(999L)).thenThrow(new LeaveNotFoundException("nope"));

        mockMvc.perform(get("/leaves/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
