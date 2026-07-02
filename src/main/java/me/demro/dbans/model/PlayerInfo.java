package me.demro.dbans.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInfo {
    private UUID uuid;
    private String name;
    private String ip;
    private long lastSeen;
}