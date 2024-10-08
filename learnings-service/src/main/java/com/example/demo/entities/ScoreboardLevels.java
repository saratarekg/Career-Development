package com.example.demo.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name="scoreboard_levels")
public class ScoreboardLevels {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    private String levelName;

    private int minScore;

    public ScoreboardLevels(String levelName, int minScore){
        this.levelName = levelName;
        this.minScore = minScore;

    }

}
