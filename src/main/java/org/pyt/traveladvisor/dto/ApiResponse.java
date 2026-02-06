package org.pyt.traveladvisor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String status;
    private AuditType type; // optional
    private T data;
    private String message;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", null, data, null);
    }

    public static <T> ApiResponse<T> success(T data, AuditType type) {
        return new ApiResponse<>("SUCCESS", type, data, null);
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>("FAILED", null, null, message);
    }
}
