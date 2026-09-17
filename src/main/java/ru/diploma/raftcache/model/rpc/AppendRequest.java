package ru.diploma.raftcache.model.rpc;

import lombok.Data;
import java.util.List;
import ru.diploma.raftcache.model.LogEntry;



@Data
public class AppendRequest {
    private int term;
    private String leaderId;
    private int prevLogIndex;
    private int prevLogTerm;
    private List<LogEntry> entries;
    private int leaderCommit;

}