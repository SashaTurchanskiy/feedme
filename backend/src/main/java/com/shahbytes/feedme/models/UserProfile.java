package com.shahbytes.feedme.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserProfile {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String handle;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 500)
    private String bio;

    @Column(nullable = false)
    private boolean hotUser;
}
