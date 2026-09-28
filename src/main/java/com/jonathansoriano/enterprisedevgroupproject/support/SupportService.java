package com.jonathansoriano.enterprisedevgroupproject.support;

import com.jonathansoriano.enterprisedevgroupproject.support.dto.AnonymousRequestRequest;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.AnonymousRequestResponse;
import com.jonathansoriano.enterprisedevgroupproject.support.dto.SupportResourceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SupportService {

    private final SupportResourceRepository resourceRepository;
    private final AnonymousRequestRepository requestRepository;

    public SupportService(SupportResourceRepository resourceRepository, AnonymousRequestRepository requestRepository) {
        this.resourceRepository = resourceRepository;
        this.requestRepository = requestRepository;
    }

    public List<SupportResourceResponse> listResources(Long schoolId) {
        List<SupportResource> resources = schoolId == null
                ? resourceRepository.findAll()
                : resourceRepository.findBySchoolId(schoolId);
        return resources.stream().map(this::toResourceResponse).collect(Collectors.toList());
    }

    public AnonymousRequestResponse createRequest(AnonymousRequestRequest request, String requesterEmail) {
        AnonymousRequest saved = requestRepository.save(AnonymousRequest.builder()
                .requesterEmail(requesterEmail)
                .category(request.getCategory())
                .description(request.getDescription())
                .schoolId(request.getSchoolId())
                .status(RequestStatus.OPEN)
                .build());
        return toRequestResponse(saved);
    }

    public List<AnonymousRequestResponse> listPublicRequests(Long schoolId, RequestStatus status) {
        return requestRepository.search(schoolId, status).stream()
                .map(this::toRequestResponse)
                .collect(Collectors.toList());
    }

    public List<AnonymousRequestResponse> listMine(String requesterEmail) {
        return requestRepository.findByRequesterEmailOrderByCreatedAtDesc(requesterEmail).stream()
                .map(this::toRequestResponse)
                .collect(Collectors.toList());
    }

    public void fulfill(Long id) {
        AnonymousRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found: " + id));
        request.setStatus(RequestStatus.FULFILLED);
        requestRepository.save(request);
    }

    private SupportResourceResponse toResourceResponse(SupportResource resource) {
        return SupportResourceResponse.builder()
                .id(resource.getId())
                .schoolId(resource.getSchoolId())
                .category(resource.getCategory())
                .name(resource.getName())
                .description(resource.getDescription())
                .contactInfo(resource.getContactInfo())
                .address(resource.getAddress())
                .latitude(resource.getLatitude())
                .longitude(resource.getLongitude())
                .build();
    }

    private AnonymousRequestResponse toRequestResponse(AnonymousRequest request) {
        return AnonymousRequestResponse.builder()
                .id(request.getId())
                .category(request.getCategory())
                .description(request.getDescription())
                .schoolId(request.getSchoolId())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .build();
    }
}
