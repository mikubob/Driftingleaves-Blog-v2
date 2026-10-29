package com.xuan;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 测试专用进程：全新临时 datadir、随机端口和回环监听，绝不连接已有服务。 */
final class IsolatedServices implements AutoCloseable {
    final int mysqlPort;
    final int redisPort;
    final String jdbcUrl;
    final Path directory;
    private final List<Process> processes = new ArrayList<>();

    IsolatedServices() throws Exception {
        directory = Files.createTempDirectory(Path.of("target"), "identity-it-").toAbsolutePath();
        mysqlPort = freePort();
        redisPort = freePort();
        jdbcUrl = "jdbc:mysql://127.0.0.1:" + mysqlPort + "/drifting_leaves_v2?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=DISABLED";
        Path mysql = Path.of(System.getProperty("test.mysql.bin")).toAbsolutePath();
        String base = mysql.getParent().getParent().toString().replace('\\', '/');
        String data = directory.resolve("mysql").toString().replace('\\', '/');
        try {
            Process initialize = start("mysql-init.log", mysql.toString(), "--no-defaults", "--initialize-insecure",
                    "--basedir=" + base, "--datadir=" + data);
            if (!initialize.waitFor(60, TimeUnit.SECONDS) || initialize.exitValue() != 0) {
                throw new IllegalStateException("隔离MySQL初始化失败，参见" + directory);
            }
            start("mysql.log", mysql.toString(), "--no-defaults", "--basedir=" + base, "--datadir=" + data,
                    "--port=" + mysqlPort, "--bind-address=127.0.0.1", "--mysqlx=OFF", "--default-time-zone=+00:00",
                    "--log-error=" + directory.resolve("mysql-error.log"), "--innodb-buffer-pool-size=64M");
            waitForMysql();
            Path redisConfig = directory.resolve("redis.conf");
            Files.writeString(redisConfig, "bind 127.0.0.1\nport " + redisPort + "\nsave \"\"\nappendonly no\ndir \""
                    + directory.toString().replace('\\', '/') + "\"\n");
            start("redis.log", System.getProperty("test.redis.bin"), redisConfig.toString());
            waitForPort(redisPort);
        } catch (Exception exception) {
            close();
            throw exception;
        }
    }

    private Process start(String log, String... args) throws IOException {
        Process process = new ProcessBuilder(args).redirectErrorStream(true)
                .redirectOutput(directory.resolve(log).toFile()).start();
        processes.add(process);
        return process;
    }

    private void waitForMysql() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        while (System.nanoTime() < deadline) {
            try (var connection = DriverManager.getConnection(jdbcUrl.replace("/drifting_leaves_v2?", "/?"), "root", "")) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource("../../sql/drifting_leaves_v2.sql"));
                return;
            } catch (java.sql.SQLException exception) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("隔离MySQL未就绪，参见" + directory);
    }

    private void waitForPort(int port) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            try (var socket = new java.net.Socket("127.0.0.1", port)) {
                return;
            } catch (IOException exception) {
                Thread.sleep(50);
            }
        }
        throw new IllegalStateException("隔离Redis未就绪，参见" + directory);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    @Override
    public void close() {
        for (Process process : processes.reversed()) {
            process.destroy();
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
