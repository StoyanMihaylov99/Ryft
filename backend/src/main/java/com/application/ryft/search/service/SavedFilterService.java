package com.application.ryft.search.service;

import com.application.ryft.search.dto.CreateSavedFilterRequest;
import com.application.ryft.search.dto.SavedFilterResponse;
import java.util.List;
import java.util.UUID;

public interface SavedFilterService {

    /**
     * Any project member, including Viewer — the caller's own filters (private or shared) plus every
     * other member's shared one, ordered by name.
     */
    List<SavedFilterResponse> list(UUID callerId, String projectKey);

    /**
     * Any project member may save a private filter ({@code request.isShared() == false}); sharing it with
     * the whole project requires the caller not be a Viewer — see {@link CreateSavedFilterRequest}.
     */
    SavedFilterResponse create(UUID callerId, String projectKey, CreateSavedFilterRequest request);

    /** The filter's owner only, regardless of project role — see {@code SavedFilterServiceImpl}. */
    void delete(UUID callerId, String projectKey, UUID filterId);
}
