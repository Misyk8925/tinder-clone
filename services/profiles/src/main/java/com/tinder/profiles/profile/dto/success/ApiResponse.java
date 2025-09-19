package com.tinder.profiles.profile.dto.success;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ApiResponse<T> {

    private String code;
    private String status;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(String message){
        return new ApiResponse<T>("200", "SUCCESS", message, null);
    }

    public static <T> ApiResponse<T> success(String message, T data){
        return new ApiResponse<T>("200", "SUCCESS", message, data);
    }

    public static <T> ApiResponse<T> created(String message){
        return new ApiResponse<T>("201", "created", message, null);
    }

    public static <T> ApiResponse<T> created(String message, T data){
        return new ApiResponse<T>("201", "created", message, data);
    }

    public static <T> ApiResponse<T> badRequest(String message){
        return new ApiResponse<T>("400", "BAD REQUEST", message, null);
    }

    public static <T> ApiResponse<T> error(String message){
        return new ApiResponse<T>("500", "ERROR", message, null);
    }

}
