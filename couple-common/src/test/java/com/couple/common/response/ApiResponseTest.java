package com.couple.common.response;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 统一响应体状态码规则测试。 */
class ApiResponseTest {

    @Test
    void explicitSuccessStatusMatchesHttpStatus() {
        ApiResponse<String> response = ApiResponse.success(HttpStatus.CREATED, "created");

        assertEquals(201, response.getCode());
        assertEquals("created", response.getData());
    }

    @Test
    void explicitSuccessRejectsNon2xxStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> ApiResponse.success(HttpStatus.BAD_REQUEST, null));
    }
}
