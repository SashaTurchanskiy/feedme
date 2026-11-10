package com.shahbytes.feedme.services.impl;

import com.shahbytes.feedme.dtos.FeedItemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.regex.Pattern;

public class FeedCursorCodec {
    //createdAtMills|postId|userId|authorId
    private static final String SEPARATOR = "|";
    private static final String SEPARATOR_REGEX = Pattern.quote(SEPARATOR);

    private FeedCursorCodec(){

    }

    static FeedCursor parse(String cursor){
        if (cursor == null || cursor.isBlank()){
            return null;
        }
        String[] parts = cursor.split(SEPARATOR_REGEX, 2);

        if (parts.length != 2 || parts[1].isBlank()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor format");
        }

        try {
            long epocMillis = Long.parseLong(parts[0]);
            return new FeedCursor(Instant.ofEpochMilli(epocMillis), parts[1]);
        }catch (RuntimeException ex){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor format");
        }
    }

    static String encode(FeedItemResponse item){
        return encode(item.createdAt(), item.postId());
    }

    static String encode(Instant createAt, String postId){
        return createAt.toEpochMilli() + SEPARATOR + postId;
    }

    record FeedCursor(Instant createdAt, String postId){

    }

}
