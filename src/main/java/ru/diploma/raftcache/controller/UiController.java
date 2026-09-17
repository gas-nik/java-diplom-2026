package ru.diploma.raftcache.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestTemplate;
import ru.diploma.raftcache.service.ClusterConfig;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class UiController {
    private final RestTemplate rest = new RestTemplate();

    @GetMapping("/")
    public String dashboard(Model model) throws InterruptedException {
        Thread.sleep(200);
        Map<String, Object> nodes = new HashMap<>();
        for (int port : ClusterConfig.PORTS) {
            try {
                nodes.put("node-" + port,
                        rest.getForObject(ClusterConfig.getBaseUrl(port) + "/raft/status", Map.class));
            } catch (Exception e) {
                nodes.put("node-" + port, "OFFLINE");
            }
        }
        model.addAttribute("nodes", nodes);
        return "dashboard";
    }

}
