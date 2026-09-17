package ru.diploma.raftcache.model.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoteResponse {
    private int term;
    private boolean voteGranted;
}