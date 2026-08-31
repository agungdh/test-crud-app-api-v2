package id.my.agungdh.common.application.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> data,
        String nextCursor,
        boolean hasNext
) {}
