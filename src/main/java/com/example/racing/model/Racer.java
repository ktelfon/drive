package com.example.racing.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Data
public class Racer {
    @Id
    private UUID id;
}
