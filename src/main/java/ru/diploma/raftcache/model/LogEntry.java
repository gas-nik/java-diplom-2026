package ru.diploma.raftcache.model;

import lombok.Data;

@Data
public class LogEntry {
    private int index;
    private int term;
    private String command;
}
