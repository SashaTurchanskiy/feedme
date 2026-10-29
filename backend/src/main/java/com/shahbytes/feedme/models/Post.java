package com.shahbytes.feedme.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
public class Post {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserProfile author;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    public Post(String id, UserProfile author, String content) {
        this.id = id;
        this.author = author;
        this.content = content;
    }

    @PrePersist
    void onCreate(){
        if (createdAt == null){
            createdAt = Instant.now();
        }
    }

}
