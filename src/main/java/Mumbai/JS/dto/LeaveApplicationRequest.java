package Mumbai.JS.dto;

import java.time.LocalDate;

import Mumbai.JS.model.LeaveType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What the client sends to apply for leave.
 *
 * This is a Java 17 record: the compiler generates the constructor, the accessors
 * (employeeId(), startDate(), ...), equals/hashCode and toString. Same idea as your
 * employeedto, just without writing the getters by hand. Not Lombok - plain Java.
 */
public record LeaveApplicationRequest(

        @NotNull(message = "employeeId is required")
        Integer employeeId,

        @NotNull(message = "startDate is required")
        @FutureOrPresent(message = "startDate cannot be in the past")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @NotNull(message = "type is required (CASUAL, SICK, EARNED, UNPAID)")
        LeaveType type,

        @NotBlank(message = "reason is required")
        @Size(max = 500, message = "reason must be at most 500 characters")
        String reason) {
}
