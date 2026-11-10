package com.shahbytes.feedme.controllers;

import com.shahbytes.feedme.dtos.TimeLinePageResponse;
import com.shahbytes.feedme.services.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;


    @GetMapping("/home")
    public TimeLinePageResponse getHomeFeed(@RequestParam String userId, @RequestParam(required = false) String cursor,
                                            @RequestParam(defaultValue = "5") int limit){
        return feedService.getHomeFeed(userId, cursor, limit);
    }

    @GetMapping("/user/{userId}")
    public TimeLinePageResponse getUserFeed(
            @PathVariable String userId, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "5") int limit){

        return feedService.getUserFeed(userId, cursor, limit);
    }
}
