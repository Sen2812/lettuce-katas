package com.example.valkey.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 集成测试公共工具：连通性探测，以及通过 WSL/Podman 控制测试集群。 */
final class IntegrationTestSupport {

  static final String CLUSTER_PASSWORD = "change_me";

  private IntegrationTestSupport() {}

  /** 探测目标主机的端口是否可以建立 TCP 连接。 */
  static boolean reachable(String host, int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 1000);
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /** 在 WSL 中执行 podman 命令，返回标准输出；失败时抛出包含输出的异常。 */
  static String podman(String... args) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>(List.of("wsl.exe", "-e", "bash", "-lc"));
    command.add("podman " + String.join(" ", args));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IllegalStateException("podman " + String.join(" ", args) + " failed with exit code "
          + exitCode + ":\n" + output);
    }
    return output;
  }

  /** 集群节点信息（由 CLUSTER NODES 输出解析而来）。 */
  record ClusterNode(String nodeId, int port, boolean master, String masterId, List<int[]> slotRanges) {

    boolean covers(int slot) {
      for (int[] range : slotRanges) {
        if (slot >= range[0] && slot <= range[1]) {
          return true;
        }
      }
      return false;
    }
  }

  /** 通过指定种子端口的节点读取并解析 CLUSTER NODES。 */
  static List<ClusterNode> clusterNodes(int seedPort) throws IOException, InterruptedException {
    String output = podman("exec", "valkey-node-" + seedPort, "valkey-cli", "-p",
        String.valueOf(seedPort), "-a", CLUSTER_PASSWORD, "--no-auth-warning", "cluster", "nodes");
    List<ClusterNode> nodes = new ArrayList<>();
    for (String line : output.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      String[] parts = trimmed.split("\\s+");
      if (parts.length < 8) {
        continue;
      }
      String address = parts[1];
      int port =
          Integer.parseInt(address.substring(address.indexOf(':') + 1, address.indexOf('@')));
      boolean master = parts[2].contains("master");
      String masterId = master ? null : parts[3];
      List<int[]> ranges = new ArrayList<>();
      for (int i = 8; i < parts.length; i++) {
        int dash = parts[i].indexOf('-');
        try {
          if (dash < 0) {
            int slot = Integer.parseInt(parts[i]);
            ranges.add(new int[] {slot, slot});
          } else {
            ranges.add(new int[] {Integer.parseInt(parts[i].substring(0, dash)),
                Integer.parseInt(parts[i].substring(dash + 1))});
          }
        } catch (NumberFormatException e) {
          // 迁移标记等非槽位字段，忽略
        }
      }
      nodes.add(new ClusterNode(parts[0], port, master, masterId, ranges));
    }
    return nodes;
  }

  /** 查询 key 所属的集群槽位。 */
  static int clusterKeySlot(int seedPort, String key) throws IOException, InterruptedException {
    String output =
        podman("exec", "valkey-node-" + seedPort, "valkey-cli", "-p", String.valueOf(seedPort),
            "-a", CLUSTER_PASSWORD, "--no-auth-warning", "cluster", "keyslot", key);
    return Integer.parseInt(output.trim());
  }

  /** 在指定端口的节点上执行 CLUSTER FAILOVER（副本提升为主节点）。 */
  static void clusterFailover(int nodePort) throws IOException, InterruptedException {
    podman("exec", "valkey-node-" + nodePort, "valkey-cli", "-p", String.valueOf(nodePort), "-a",
        CLUSTER_PASSWORD, "--no-auth-warning", "cluster", "failover");
  }

  /** 轮询等待指定端口的节点角色变为 master / slave。 */
  static void awaitRole(int seedPort, int targetPort, boolean expectMaster, Duration timeout)
      throws IOException, InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      for (ClusterNode node : clusterNodes(seedPort)) {
        if (node.port() == targetPort && node.master() == expectMaster) {
          return;
        }
      }
      Thread.sleep(250);
    }
    throw new IllegalStateException("node on port " + targetPort + " did not become "
        + (expectMaster ? "master" : "slave") + " within " + timeout);
  }

  /** 构造一个带随机 hash tag 的测试 key，保证 Jackson 序列化前后槽位一致。 */
  static String drillKey(String purpose) {
    return "{drill" + Long.toHexString(System.nanoTime()) + purpose + "}:value";
  }
}
