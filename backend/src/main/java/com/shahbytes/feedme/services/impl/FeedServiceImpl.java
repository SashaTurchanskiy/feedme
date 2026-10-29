package com.shahbytes.feedme.services.impl;

import com.shahbytes.feedme.dtos.TimeLinePageResponse;
import com.shahbytes.feedme.services.FeedService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedServiceImpl implements FeedService {

    @Override
    public TimeLinePageResponse getHomeFeed(String userId, String cursor, int limit) {
        return null;
    }
}
