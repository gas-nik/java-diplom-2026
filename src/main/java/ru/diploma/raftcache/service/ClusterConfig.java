package ru.diploma.raftcache.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClusterConfig {
    public static final List<Integer> PORTS = List.of(9090, 9091, 9092);

    public static String getBaseUrl(int port) {
        return "http://localhost:" + port;
    }
}