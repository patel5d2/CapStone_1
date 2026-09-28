package com.jonathansoriano.enterprisedevgroupproject.community;

import com.jonathansoriano.enterprisedevgroupproject.community.dto.EventRequest;
import com.jonathansoriano.enterprisedevgroupproject.community.dto.EventResponse;
import com.jonathansoriano.enterprisedevgroupproject.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> upcoming(@RequestParam(required = false) Long schoolId) {
        return ResponseEntity.ok(eventService.upcoming(schoolId));
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request,
                                                 @AuthenticationPrincipal Jwt clerkSession) {
        EventResponse created = eventService.create(request, CurrentUser.emailOf(clerkSession));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
