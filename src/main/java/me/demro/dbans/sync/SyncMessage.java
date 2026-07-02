package me.demro.dbans.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncMessage {

    private String type;
    private Map<String, Object> data;
}