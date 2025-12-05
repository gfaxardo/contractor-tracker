package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {
    private List<T> data;
    private int page;
    private int size;
    private long total;
    private boolean hasMore;
    private int totalPages;
}

