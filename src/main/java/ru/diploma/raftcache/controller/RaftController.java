package ru.diploma.raftcache.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.diploma.raftcache.model.LogEntry;
import ru.diploma.raftcache.model.NodeRole;
import ru.diploma.raftcache.service.RaftState;
import ru.diploma.raftcache.model.rpc.VoteRequest;
import ru.diploma.raftcache.model.rpc.VoteResponse;
import ru.diploma.raftcache.model.rpc.AppendRequest;
import ru.diploma.raftcache.model.rpc.AppendResponse;

import java.util.Map;

@RestController
@RequestMapping("/raft")
@RequiredArgsConstructor
public class RaftController {
    private final RaftState state;

    @PostMapping("/vote")
    public ResponseEntity<VoteResponse> requestVote(@RequestBody VoteRequest req) {
        return ResponseEntity.ok(state.handleVoteRequest(req));
    }

    @PostMapping("/append")
    public ResponseEntity<AppendResponse> appendEntries(@RequestBody AppendRequest req) {
        return ResponseEntity.ok(state.handleAppendRequest(req));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "id", "node-" + state.getServerPort(),
                "term", state.getCurrentTerm(),
                "role", state.getRole(),
                "logSize", state.getLog().size(),
                "lastHeartbeat", state.getLastHeartbeat()
        );
    }

    @PutMapping("/set")
    public String setKey(@RequestParam String key, @RequestParam String value) {
        if (state.getRole() != NodeRole.LEADER) {
            return "Error: I am not the leader. Try node with higher port.";
        }

        int lastIndex = state.getLog().isEmpty() ? 0 : state.getLog().getLast().getIndex();

        LogEntry entry = new LogEntry();
        entry.setIndex(lastIndex + 1);
        entry.setTerm(state.getCurrentTerm());
        entry.setCommand("SET " + key + "=" + value);

        state.getLog().add(entry);
        state.persistStateToDisk();

        System.out.println("[Node " + state.getServerPort() + "] New log entry added: " + entry.getCommand());
        return "OK";
    }
}