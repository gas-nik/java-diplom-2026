package ru.diploma.raftcache.model.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoteRequest {
    private int term;
    private String candidateId;
    private int lastLogIndex;
    private int lastLogTerm;
}