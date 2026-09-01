package Mumbai.JS.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The employee who applied. Same @ManyToOne you already used for department.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private employeemodel employee;

    // The manager who approved/rejected it. Null while still PENDING.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private employeemodel approver;

    private LocalDate startDate;
    private LocalDate endDate;

    // EnumType.STRING stores "PENDING" in the DB instead of 0.
    // Ordinal (the default) breaks the moment you reorder the enum, so always use STRING.
    @Enumerated(EnumType.STRING)
    private LeaveType type;

    @Enumerated(EnumType.STRING)
    private LeaveStatus status;

    private String reason;
    private String decisionComment;

    private LocalDate appliedOn;
    private LocalDate decidedOn;

    public LeaveRequest() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public employeemodel getEmployee() {
        return employee;
    }

    public void setEmployee(employeemodel employee) {
        this.employee = employee;
    }

    public employeemodel getApprover() {
        return approver;
    }

    public void setApprover(employeemodel approver) {
        this.approver = approver;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LeaveType getType() {
        return type;
    }

    public void setType(LeaveType type) {
        this.type = type;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public void setDecisionComment(String decisionComment) {
        this.decisionComment = decisionComment;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public void setAppliedOn(LocalDate appliedOn) {
        this.appliedOn = appliedOn;
    }

    public LocalDate getDecidedOn() {
        return decidedOn;
    }

    public void setDecidedOn(LocalDate decidedOn) {
        this.decidedOn = decidedOn;
    }
}
