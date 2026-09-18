package com.application.ryft.sprints.service;

import com.application.ryft.sprints.entity.Sprint;
import com.application.ryft.sprints.exception.SprintNotFoundException;
import com.application.ryft.sprints.repository.SprintRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Shared by every sprints-module service that needs to load a {@link Sprint} by id. */
@Component
class SprintLookupSupport {

    private final SprintRepository sprintRepository;

    SprintLookupSupport(SprintRepository sprintRepository) {
        this.sprintRepository = sprintRepository;
    }

    Sprint requireSprint(UUID sprintId) {
        return sprintRepository.findById(sprintId).orElseThrow(() -> new SprintNotFoundException(sprintId));
    }
}
