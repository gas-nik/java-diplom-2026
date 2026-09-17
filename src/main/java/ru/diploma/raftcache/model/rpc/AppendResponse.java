package ru.diploma.raftcache.model.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AppendResponse {
    private int term;
    private boolean success;
}