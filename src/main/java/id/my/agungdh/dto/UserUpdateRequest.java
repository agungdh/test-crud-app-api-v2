package id.my.agungdh.dto;

import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(min = 3, max = 50) String username,
        @Size(min = 6, max = 100) String password,
        @Size(min = 1, max = 100) String name
) {}
