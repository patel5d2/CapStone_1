package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.EventRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.EventResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventResponse> upcoming(Long schoolId) {
        return eventRepository.findUpcoming(schoolId, Instant.now()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public EventResponse create(EventRequest request, String creatorEmail) {
        Event event = eventRepository.save(Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .startsAt(request.getStartsAt())
                .location(request.getLocation())
                .schoolId(request.getSchoolId())
                .listingId(request.getListingId())
                .createdByEmail(creatorEmail)
                .build());
        return toResponse(event);
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startsAt(event.getStartsAt())
                .location(event.getLocation())
                .schoolId(event.getSchoolId())
                .listingId(event.getListingId())
                .createdByEmail(event.getCreatedByEmail())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
