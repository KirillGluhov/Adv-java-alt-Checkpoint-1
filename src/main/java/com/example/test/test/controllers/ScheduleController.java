package com.example.test.test.controllers;

import com.example.test.test.entities.Schedule;
import com.example.test.test.responses.Id;
import com.example.test.test.services.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/schedule")
public class ScheduleController
{
    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<Id> createSchedule(
            @RequestBody(required = false) Schedule schedule
    )
    {
        return ResponseEntity.ok(scheduleService.createSchedule(schedule));
    }

    @GetMapping("/{id}")
    public Object readSchedule(
            @PathVariable String id
    )
    {
        return scheduleService.findSchedule(id);
    }

    @GetMapping("/all")
    public ResponseEntity<List<Schedule>> readSchedules()
    {
        return ResponseEntity.ok(scheduleService.findAllSchedules());
    }
}