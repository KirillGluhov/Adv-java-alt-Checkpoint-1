package com.example.test.test.models.responses.returned;

import com.example.test.test.models.responses.supporting.PeriodWithAllIds;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.List;

@Builder(toBuilder = true)
@Data
@AllArgsConstructor
public class PeriodsWithPageAndSize
{
    @NonNull
    private List<PeriodWithAllIds> periodList;
    private Integer page;
    private Integer size;
    private Integer totalPages;


}
