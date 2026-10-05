package com.jonathansoriano.enterprisedevgroupproject;

import org.springframework.boot.SpringApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

// Entry point for `./mvnw spring-boot:test-run`: starts the Vite dev server on :5173
// (which proxies /api and /student here) and then the API on :8080, so one command
// runs the whole stack. Open http://localhost:5173.
public class TestEnterpriseDevGroupProjectApplication {

    public static void main(String[] args) throws Exception {
        File frontend = new File("frontend");
        Path env = frontend.toPath().resolve(".env");
        if (Files.notExists(env)) {
            Files.copy(frontend.toPath().resolve(".env.example"), env, StandardCopyOption.COPY_ATTRIBUTES);
        }
        if (!new File(frontend, "node_modules").isDirectory()) {
            if (npm(frontend, "ci").waitFor() != 0) {
                throw new IllegalStateException("npm ci failed in frontend/");
            }
        }

        Process vite = npm(frontend, "run dev");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            vite.descendants().forEach(ProcessHandle::destroy);
            vite.destroy();
        }));

        SpringApplication.run(EnterpriseDevGroupProjectApplication.class, args);
    }

    // Through a shell so an nvm-managed npm is found even when it is not on the JVM's PATH.
    private static Process npm(File dir, String command) throws IOException {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        String[] cmd = windows
                ? new String[] {"cmd", "/c", "npm " + command}
                : new String[] {"sh", "-c",
                        "NVM_DIR=\"${NVM_DIR:-$HOME/.nvm}\"; [ -s \"$NVM_DIR/nvm.sh\" ] && . \"$NVM_DIR/nvm.sh\"; exec npm " + command};
        return new ProcessBuilder(cmd).directory(dir).inheritIO().start();
    }
}
