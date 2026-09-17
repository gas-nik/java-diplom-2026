package ru.diploma.raftcache.service;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.diploma.raftcache.model.LogEntry;
import ru.diploma.raftcache.model.NodeRole;
import ru.diploma.raftcache.model.rpc.*;

import java.io.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter @Setter
@Component
@Scope("singleton")
public class RaftState implements Runnable {

    private int serverPort;
    private SecureRandom random = new SecureRandom();

    private volatile int currentTerm = 0;
    private volatile String votedFor = null;
    private final List<LogEntry> log = new CopyOnWriteArrayList<>();
    private volatile NodeRole role = NodeRole.FOLLOWER;

    private ScheduledFuture<?> electionTask;
    private ScheduledFuture<?> heartbeatTask;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private LocalDateTime lastHeartbeat = LocalDateTime.now();

    private final AtomicInteger votesReceived = new AtomicInteger(0);

    public RaftState(@Value("${server.port}") int serverPort) {
        this.serverPort = serverPort;
        restoreStateFromDisk();

        scheduler.schedule(this::startElectionTimer, 3000, TimeUnit.MILLISECONDS);
    }


    public synchronized VoteResponse handleVoteRequest(VoteRequest req) {
        boolean grant = false;

        if (req.getTerm() < currentTerm) {
            return new VoteResponse(currentTerm, false);
        }

        if (req.getTerm() > currentTerm) {
            stepDown(req.getTerm());
        }

        if ((votedFor == null || votedFor.equals(req.getCandidateId()))) {
            grant = true;
            votedFor = req.getCandidateId();
            persistStateToDisk();
        }

        resetElectionTimer();
        return new VoteResponse(currentTerm, grant);
    }

    public synchronized AppendResponse handleAppendRequest(AppendRequest req) {
        if (req.getTerm() < currentTerm) {
            return new AppendResponse(currentTerm, false);
        }

        stepDown(req.getTerm());
        resetElectionTimer();

        if (!log.isEmpty()) {
            LogEntry last = log.get(log.size() - 1);
            if (last.getIndex() != req.getPrevLogIndex() || last.getTerm() != req.getPrevLogTerm()) {
                return new AppendResponse(currentTerm, false);
            }
        }

        for (LogEntry entry : req.getEntries()) {
            if (log.isEmpty() || log.get(log.size() - 1).getIndex() < entry.getIndex()) {
                log.add(entry);
            }
        }

        if (req.getLeaderCommit() > getLastApplied()) {
            applyLog(req.getLeaderCommit());
        }

        persistStateToDisk();
        return new AppendResponse(currentTerm, true);
    }

    private void startNewElection() {
        System.out.println("[Node " + serverPort + "] Starting election.");
        role = NodeRole.CANDIDATE;
        currentTerm++;
        votedFor = "node-" + serverPort;

        votesReceived.set(1);

        RestTemplate rest = new RestTemplate();

        for (int peerPort : ClusterConfig.PORTS) {
            if (peerPort == serverPort) continue;

            final int targetPort = peerPort;

            scheduler.submit(() -> {
                try {
                    VoteRequest req = new VoteRequest(currentTerm, "node-" + serverPort, 0, 0);
                    String url = ClusterConfig.getBaseUrl(targetPort) + "/raft/vote";

                    ResponseEntity<VoteResponse> resp = rest.postForEntity(url, req, VoteResponse.class);
                    handleVoteResponse(resp.getBody());
                } catch (Exception e) {
                }
            });
        }
        scheduler.schedule(this::checkElectionResult, 1000, TimeUnit.MILLISECONDS);
    }

    private synchronized void handleVoteResponse(VoteResponse resp) {
        if (resp == null) return;
        if (resp.getTerm() > currentTerm) {
            stepDown(resp.getTerm());
            return;
        }
        if (role == NodeRole.LEADER) {
            return;
        }
        if (resp.getTerm() == currentTerm && role == NodeRole.CANDIDATE && resp.isVoteGranted()) {
            int received = votesReceived.incrementAndGet();

            int quorumSize = (ClusterConfig.PORTS.size() / 2) + 1;

            System.out.println("[Node " + serverPort + "] Got vote (" + received + "/" + quorumSize + ") in term " + currentTerm);

            if (received >= quorumSize) {
                becomeLeader();
            }
        }
    }

    private void checkElectionResult() {
        if (role != NodeRole.CANDIDATE) return;

        int received = votesReceived.get();
        int quorumSize = (ClusterConfig.PORTS.size() / 2) + 1;

        if (received >= 1 && received < quorumSize) {
            System.out.println("[Node " + serverPort + "] Election timeout. No responses, but I have my vote. Declaring self leader.");
            becomeLeader();
        }

        if (received == 0) {
            System.out.println("[Node " + serverPort + "] Election failed: no votes received. Resetting to follower.");
            role = NodeRole.FOLLOWER;
            startElectionTimer();
        }
    }

    private void becomeLeader() {
        System.out.println("[Node " + serverPort + "] Became LEADER for term " + currentTerm);
        role = NodeRole.LEADER;
        startHeartbeats();
    }

    private void stepDown(int higherTerm) {
        if (higherTerm > currentTerm) {
            System.out.println("[Node " + serverPort + "] Stepping down. New term is " + higherTerm);
            currentTerm = higherTerm;
            role = NodeRole.FOLLOWER;
            votedFor = null;

            votesReceived.set(0);

            cancelTasks();
            startElectionTimer();
            persistStateToDisk();
        }
    }

    private void startElectionTimer() {
        cancelTasks();
        int timeout = 1000 + random.nextInt(1000);
        electionTask = scheduler.schedule(this::startNewElection, timeout, TimeUnit.MILLISECONDS);
    }

    private void resetElectionTimer() {
        if (role != NodeRole.LEADER && electionTask != null) {
            electionTask.cancel(false);
            startElectionTimer();
            lastHeartbeat = LocalDateTime.now();
        }
    }

    private void startHeartbeats() {
        cancelTasks();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> sendHeartbeats(),
                0, 50, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeats() {
        if (role != NodeRole.LEADER) return;

        for (int peerPort : ClusterConfig.PORTS) {
            if (peerPort == serverPort) continue;

            scheduler.submit(() -> {
                try {
                    RestTemplate rest = new RestTemplate();
                    AppendRequest req = new AppendRequest();
                    req.setTerm(currentTerm);
                    req.setLeaderId("node-" + serverPort);

                    LogEntry last = log.isEmpty() ? null : log.get(log.size() - 1);
                    req.setPrevLogIndex(last != null ? last.getIndex() : 0);
                    req.setPrevLogTerm(last != null ? last.getTerm() : 0);

                    req.setEntries(List.of());
                    req.setLeaderCommit(getLastApplied());

                    String url = ClusterConfig.getBaseUrl(peerPort) + "/raft/append";
                    rest.postForObject(url, req, AppendResponse.class);

                } catch (Exception e) {
                }
            });
        }
    }

    private void cancelTasks() {
        if (electionTask != null) electionTask.cancel(true);
        if (heartbeatTask != null) heartbeatTask.cancel(true);
    }

    private int getLastApplied() {
        return log.isEmpty() ? 0 : log.get(log.size() - 1).getIndex();
    }

    private void applyLog(int commitIndex) {
    }

    public void persistStateToDisk() {
        try {
            File file = new File("state_" + serverPort + ".json");
            PrintWriter writer = new PrintWriter(file);
            writer.println("{ \"term\": " + currentTerm + ", \"votedFor\": \"" + votedFor + "\" }");
            writer.close();
        } catch (IOException ignored) {}
    }

    private void restoreStateFromDisk() {
        try {
            File file = new File("state_" + serverPort + ".json");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line = reader.readLine();
                if (line.contains("\"term\"")) {
                    this.currentTerm = Integer.parseInt(line.split(":")[1].replaceAll("[^0-9]", ""));
                }
                reader.close();
            }
        } catch (IOException ignored) {}
    }

    @PreDestroy
    public void shutdown() {
        cancelTasks();
        scheduler.shutdownNow();
    }

    @Override
    public void run() { }
}