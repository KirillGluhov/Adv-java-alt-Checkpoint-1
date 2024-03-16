package com.example.test.test.services;

import com.example.test.test.entities.Schedule;
import com.example.test.test.responses.Id;

import java.util.List;

public interface ScheduleService
{
    Id createSchedule(Schedule schedule);

    Object findSchedule(String id);

    List<Schedule> findAllSchedules();
}
