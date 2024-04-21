package com.example.test.test.controllers;

import com.example.test.test.exception.ErrorResponse;
import com.example.test.test.models.responses.accepted.FilterAndSorting;
import com.example.test.test.models.responses.accepted.PeriodWithIds;
import com.example.test.test.services.PeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.ZonedDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/period")
public class PeriodController
{
    private final PeriodService periodService;
    //Основной контроллер

    @PostMapping
    public Object createPeriod(
            @RequestBody PeriodWithIds period, @RequestHeader("X-Current-User") String administratorId
    )
    {
        return periodService.createPeriod(period, administratorId);
    }

    @GetMapping("/{id}")
    public Object readPeriod(
            @PathVariable String id
    )
    {
        return periodService.findPeriod(id);
    }

    @PostMapping("/all")
    public Object readPeriods(
            @RequestBody FilterAndSorting filterAndSorting
    )
    {
        return periodService.findPeriods(filterAndSorting);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse
                .builder()
                .message("Не удалось создать период на основании предоставленных данных. Возможно slotType не тот или нет заголовка X-Current-User")
                .timestamp(ZonedDateTime.now())
                .status(400)
                .error("Bad Request")
                .path("/period")
                .build()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadableException(DataIntegrityViolationException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse
                .builder()
                .message("Этот слот уже реализован другой сущностью периода")
                .timestamp(ZonedDateTime.now())
                .status(400)
                .error("Bad Request")
                .path("/period")
                .build()
        );
    }
}
