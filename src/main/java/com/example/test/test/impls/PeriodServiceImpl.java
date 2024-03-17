package com.example.test.test.impls;
import com.example.test.test.enums.Direction;
import com.example.test.test.enums.Field;
import com.example.test.test.repositories.*;
import com.example.test.test.responses.accepted.FilterAndSorting;
import com.example.test.test.responses.accepted.PeriodWithIds;
import com.example.test.test.responses.returned.Id;
import com.example.test.test.responses.returned.PeriodWithoutIdAndMinimumInfo;
import com.example.test.test.responses.returned.PeriodsWithPageAndSize;
import com.example.test.test.responses.supporting.PeriodWithAllIds;
import org.springframework.data.domain.Page;
import com.example.test.test.entities.Employee;
import com.example.test.test.entities.Period;
import com.example.test.test.entities.Schedule;
import com.example.test.test.entities.Slot;
import com.example.test.test.exception.ErrorResponse;
import com.example.test.test.services.PeriodService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.stream.Collectors;

@Transactional
@RequiredArgsConstructor
@Service
public class PeriodServiceImpl implements PeriodService {

    private final PeriodRepository periodRepository;
    private final SlotRepository slotRepository;
    private final ScheduleRepository scheduleRepository;
    private final EmployeeRepository employeeRepository;

    private final PeriodPageRepository periodPageRepository;
    @Override
    public Object createPeriod(PeriodWithIds period, String administratorId)
    {
        Slot slot = slotRepository.findById(period.getSlotId()).orElse(null);
        Schedule schedule = scheduleRepository.findById(period.getScheduleId()).orElse(null);
        Employee employee = employeeRepository.findById(administratorId).orElse(null);
        Employee executor = null;

        String message = "";

        if (slot == null)
        {
            message += "Слота с таким идентификатором не существует.";
        }

        if (schedule == null)
        {
            message += "Расписания с таким идентификатором не существует.";
        }

        if (employee == null)
        {
            message += "Владельца слота с таким идентификатором не существует.";
        }

        if (period.getExecutorId() != null)
        {
            executor = employeeRepository.findById(period.getExecutorId()).orElse(null);

            if (executor == null)
            {
                message += "Исполнителя слота с таким идентификатором не существует.";
            }

            if (Objects.equals(executor.getId(), administratorId))
            {
                executor = null;
            }
        }

        if (!message.isEmpty())
        {
            ErrorResponse errorResponse = ErrorResponse
                    .builder()
                    .timestamp(ZonedDateTime.now())
                    .status(400)
                    .error("Bad Request")
                    .message(message)
                    .path("/period").build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        else
        {
            Period periodNew = Period
                    .builder()
                    .slot(slot)
                    .schedule(schedule)
                    .slotType(period
                            .getSlotType())
                    .administrator(employee)
                    .executor(executor)
                    .build();
            periodRepository.save(periodNew);
            return Id.builder().id(periodNew.getId()).build();
        }
    }

    @Override
    public Object findPeriod(String id) {
        Period period = periodRepository.findById(id).orElse(null);

        if (period == null)
        {
            ErrorResponse errorResponse = ErrorResponse
                    .builder()
                    .timestamp(ZonedDateTime.now())
                    .status(404)
                    .error("Not Found")
                    .message("Период с таким идентификатором не найден")
                    .path("/period/" + id).build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        else
        {
            String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
            return PeriodWithoutIdAndMinimumInfo
                    .builder()
                    .slotId(period
                            .getSlot()
                            .getId())
                    .administratorId(period
                            .getAdministrator()
                            .getId())
                    .scheduleId(period
                            .getSchedule()
                            .getId())
                    .slotType(period
                            .getSlotType())
                    .executorId(executorId)
                    .build();
        }
    }

    @Override
    public Object findPeriods(FilterAndSorting filterAndSorting) {

        Integer page = filterAndSorting.getPage() == null ? 0 : filterAndSorting.getPage();
        Integer size = filterAndSorting.getSize() == null ? 10 : filterAndSorting.getSize();
        Direction direction = filterAndSorting.getSort() == null ? Direction.DESC : filterAndSorting.getSort().getDirection() == null ? Direction.DESC : filterAndSorting.getSort().getDirection();
        Field field = filterAndSorting.getSort() == null ? Field.beginTime : filterAndSorting.getSort().getField() == null ? Field.beginTime : filterAndSorting.getSort().getField();

        /*
        * Знаю, тут много повторений, но при попытке вынести в отдельную функцию эндпоинт перестал работать правильно (Почему - не разобрался)
        *
        * endTime и beginTime вводятся в формате YYYY-MM-DDTHH:mm:ssZ
        * Фильтрация работает лишь по одному критерию (в представленном порядке)
        *
        * Почти везде возвращаются "плоские" классы, кроме шаблона расписания, расписания и этой сущности
        *
        * Самая жесть в package responses. Там пример не очень удачного именования + разделения
        *
        * Во всех контроллерах есть эндпоинт, предоставляющий всех сущностей определённого вида
        *
        * page = 0, а size = 10 по умолчанию, чтобы если пользователь забудет ввести их, то не будет слишком большой нагрузки на приложение из-за получения из БД нескольких тысяч записей
        * */
        if (filterAndSorting.getFilter() == null)
        {
            Page<Period> periods = periodPageRepository.findAll(PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
            if (periods.getTotalPages() < page)
            {
                ErrorResponse errorResponse = ErrorResponse
                        .builder()
                        .timestamp(ZonedDateTime.now())
                        .status(404)
                        .error("Not Found")
                        .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                        .path("/period/all").build();
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            return PeriodsWithPageAndSize
                    .builder()
                    .size(size)
                    .page(page)
                    .periodList(periods
                            .stream()
                            .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                return PeriodWithAllIds.builder()
                                        .id(period
                                                .getId())
                                        .slotId(period
                                                .getSlot()
                                                .getId())
                                        .administratorId(period
                                                .getAdministrator()
                                                .getId())
                                        .scheduleId(period
                                                .getSchedule()
                                                .getId())
                                        .slotType(period
                                                .getSlotType())
                                        .executorId(executorId)
                                        .build();})
                            .collect(Collectors
                                    .toList()))
                    .build();
        }
        else
        {
            if (filterAndSorting.getFilter().getId() != null)
            {
                Page<Period> periods = periodPageRepository.findAllById(filterAndSorting.getFilter().getId(), PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
                if (periods.getTotalPages() < page)
                {
                    ErrorResponse errorResponse = ErrorResponse
                            .builder()
                            .timestamp(ZonedDateTime.now())
                            .status(404)
                            .error("Not Found")
                            .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                            .path("/period/all").build();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                return PeriodsWithPageAndSize
                        .builder()
                        .size(size)
                        .page(page)
                        .periodList(periods
                                .stream()
                                .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                    return PeriodWithAllIds.builder()
                                            .id(period
                                                    .getId())
                                            .slotId(period
                                                    .getSlot()
                                                    .getId())
                                            .administratorId(period
                                                    .getAdministrator()
                                                    .getId())
                                            .scheduleId(period
                                                    .getSchedule()
                                                    .getId())
                                            .slotType(period
                                                    .getSlotType())
                                            .executorId(executorId)
                                            .build();})
                                .collect(Collectors
                                        .toList()))
                        .build();

            }
            else if (filterAndSorting.getFilter().getSlotType() != null)
            {
                Page<Period> periods = periodPageRepository.findAllBySlotType(filterAndSorting.getFilter().getSlotType(), PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
                if (periods.getTotalPages() < page)
                {
                    ErrorResponse errorResponse = ErrorResponse
                            .builder()
                            .timestamp(ZonedDateTime.now())
                            .status(404)
                            .error("Not Found")
                            .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                            .path("/period/all").build();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                return PeriodsWithPageAndSize
                        .builder()
                        .size(size)
                        .page(page)
                        .periodList(periods
                                .stream()
                                .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                    return PeriodWithAllIds.builder()
                                            .id(period
                                                    .getId())
                                            .slotId(period
                                                    .getSlot()
                                                    .getId())
                                            .administratorId(period
                                                    .getAdministrator()
                                                    .getId())
                                            .scheduleId(period
                                                    .getSchedule()
                                                    .getId())
                                            .slotType(period
                                                    .getSlotType())
                                            .executorId(executorId)
                                            .build();})
                                .collect(Collectors
                                        .toList()))
                        .build();
            }
            else if (filterAndSorting.getFilter().getSlotId() != null)
            {
                Page<Period> periods = periodPageRepository.findAllBySlotId(filterAndSorting.getFilter().getSlotId(), PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
                if (periods.getTotalPages() < page)
                {
                    ErrorResponse errorResponse = ErrorResponse
                            .builder()
                            .timestamp(ZonedDateTime.now())
                            .status(404)
                            .error("Not Found")
                            .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                            .path("/period/all").build();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                return PeriodsWithPageAndSize
                        .builder()
                        .size(size)
                        .page(page)
                        .periodList(periods
                                .stream()
                                .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                    return PeriodWithAllIds.builder()
                                            .id(period
                                                    .getId())
                                            .slotId(period
                                                    .getSlot()
                                                    .getId())
                                            .administratorId(period
                                                    .getAdministrator()
                                                    .getId())
                                            .scheduleId(period
                                                    .getSchedule()
                                                    .getId())
                                            .slotType(period
                                                    .getSlotType())
                                            .executorId(executorId)
                                            .build();})
                                .collect(Collectors
                                        .toList()))
                        .build();
            }
            else if (filterAndSorting.getFilter().getScheduleId() != null)
            {
                Page<Period> periods = periodPageRepository.findAllByScheduleId(filterAndSorting.getFilter().getScheduleId(), PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
                if (periods.getTotalPages() < page)
                {
                    ErrorResponse errorResponse = ErrorResponse
                            .builder()
                            .timestamp(ZonedDateTime.now())
                            .status(404)
                            .error("Not Found")
                            .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                            .path("/period/all").build();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                return PeriodsWithPageAndSize
                        .builder()
                        .size(size)
                        .page(page)
                        .periodList(periods
                                .stream()
                                .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                    return PeriodWithAllIds.builder()
                                            .id(period
                                                    .getId())
                                            .slotId(period
                                                    .getSlot()
                                                    .getId())
                                            .administratorId(period
                                                    .getAdministrator()
                                                    .getId())
                                            .scheduleId(period
                                                    .getSchedule()
                                                    .getId())
                                            .slotType(period
                                                    .getSlotType())
                                            .executorId(executorId)
                                            .build();})
                                .collect(Collectors
                                        .toList()))
                        .build();
            }
            else if (filterAndSorting.getFilter().getAdministratorId() != null)
            {
                Page<Period> periods = periodPageRepository.findAllByAdministratorId(filterAndSorting.getFilter().getAdministratorId(), PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
                if (periods.getTotalPages() < page)
                {
                    ErrorResponse errorResponse = ErrorResponse
                            .builder()
                            .timestamp(ZonedDateTime.now())
                            .status(404)
                            .error("Not Found")
                            .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                            .path("/period/all").build();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                return PeriodsWithPageAndSize
                        .builder()
                        .size(size)
                        .page(page)
                        .periodList(periods
                                .stream()
                                .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                    return PeriodWithAllIds.builder()
                                            .id(period
                                                    .getId())
                                            .slotId(period
                                                    .getSlot()
                                                    .getId())
                                            .administratorId(period
                                                    .getAdministrator()
                                                    .getId())
                                            .scheduleId(period
                                                    .getSchedule()
                                                    .getId())
                                            .slotType(period
                                                    .getSlotType())
                                            .executorId(executorId)
                                            .build();})
                                .collect(Collectors
                                        .toList()))
                        .build();
            }
            else if (filterAndSorting.getFilter().getExecutorId() != null)
            {
                Page<Period> periods = periodPageRepository.findAllByExecutorId(filterAndSorting.getFilter().getExecutorId(), PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
                if (periods.getTotalPages() < page)
                {
                    ErrorResponse errorResponse = ErrorResponse
                            .builder()
                            .timestamp(ZonedDateTime.now())
                            .status(404)
                            .error("Not Found")
                            .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                            .path("/period/all").build();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                return PeriodsWithPageAndSize
                        .builder()
                        .size(size)
                        .page(page)
                        .periodList(periods
                                .stream()
                                .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                    return PeriodWithAllIds.builder()
                                            .id(period
                                                    .getId())
                                            .slotId(period
                                                    .getSlot()
                                                    .getId())
                                            .administratorId(period
                                                    .getAdministrator()
                                                    .getId())
                                            .scheduleId(period
                                                    .getSchedule()
                                                    .getId())
                                            .slotType(period
                                                    .getSlotType())
                                            .executorId(executorId)
                                            .build();})
                                .collect(Collectors
                                        .toList()))
                        .build();
            }
            else if (filterAndSorting.getFilter().getBeginTime() != null)
            {
                Page<Period> periods = periodPageRepository.findAllBySlotBeginTime(filterAndSorting.getFilter().getBeginTime(), PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
                if (periods.getTotalPages() < page)
                {
                    ErrorResponse errorResponse = ErrorResponse
                            .builder()
                            .timestamp(ZonedDateTime.now())
                            .status(404)
                            .error("Not Found")
                            .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                            .path("/period/all").build();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                return PeriodsWithPageAndSize
                        .builder()
                        .size(size)
                        .page(page)
                        .periodList(periods
                                .stream()
                                .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                    return PeriodWithAllIds.builder()
                                            .id(period
                                                    .getId())
                                            .slotId(period
                                                    .getSlot()
                                                    .getId())
                                            .administratorId(period
                                                    .getAdministrator()
                                                    .getId())
                                            .scheduleId(period
                                                    .getSchedule()
                                                    .getId())
                                            .slotType(period
                                                    .getSlotType())
                                            .executorId(executorId)
                                            .build();})
                                .collect(Collectors
                                        .toList()))
                        .build();
            }
            else if (filterAndSorting.getFilter().getEndTime() != null)
            {
                Page<Period> periods = periodPageRepository.findAllBySlotEndTime(filterAndSorting.getFilter().getEndTime(), PageRequest.of(page, size, Sort.by(findDirection(direction), findField(field))));
                if (periods.getTotalPages() < page)
                {
                    ErrorResponse errorResponse = ErrorResponse
                            .builder()
                            .timestamp(ZonedDateTime.now())
                            .status(404)
                            .error("Not Found")
                            .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                            .path("/period/all").build();
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
                }
                return PeriodsWithPageAndSize
                        .builder()
                        .size(size)
                        .page(page)
                        .periodList(periods
                                .stream()
                                .map(period -> {String executorId = period.getExecutor() != null ? period.getExecutor().getId() : null;
                                    return PeriodWithAllIds.builder()
                                            .id(period
                                                    .getId())
                                            .slotId(period
                                                    .getSlot()
                                                    .getId())
                                            .administratorId(period
                                                    .getAdministrator()
                                                    .getId())
                                            .scheduleId(period
                                                    .getSchedule()
                                                    .getId())
                                            .slotType(period
                                                    .getSlotType())
                                            .executorId(executorId)
                                            .build();})
                                .collect(Collectors
                                        .toList()))
                        .build();

            }
        }
        ErrorResponse errorResponse = ErrorResponse
                .builder()
                .timestamp(ZonedDateTime.now())
                .status(404)
                .error("Not Found")
                .message("Страницы с таким номером при данном размере (размер по умолчанию - 10) нет")
                .path("/period/all").build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    private Sort.Direction findDirection(Direction direction)
    {
        switch (direction)
        {
            case ASC:
                return Sort.Direction.ASC;
            case DESC:
                return Sort.Direction.DESC;
            default:
                return Sort.Direction.DESC;

        }
    }

    private String findField(Field field)
    {
        switch (field)
        {
            case beginTime:
                return "slot.beginTime";
            case endTime:
                return "slot.endTime";
            case id:
                return "id";
            case slotId:
                return "slot.id";
            case scheduleId:
                return "schedule.id";
            case slotType:
                return "slotType";
            case administratorId:
                return "administrator.id";
            case executorId:
                return "executor.id";
        }
        return null;
    }

}
