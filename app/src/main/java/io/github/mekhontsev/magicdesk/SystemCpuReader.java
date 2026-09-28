package io.github.mekhontsev.magicdesk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared procfs boundary for the process monitor and optional appearance telemetry. */
final class SystemCpuReader {
    static SystemCpuSnapshot read() throws IOException {
        try (var input = Files.newInputStream(Path.of("/proc/stat"))) {
            return parse(new String(input.readNBytes(4096), StandardCharsets.UTF_8).split("\n", 2)[0]);
        }
    }
    static SystemCpuSnapshot parse(String line) throws IOException {
        try {
            String[] f = line.trim().split("\\s+");
            if (f.length < 5 || !"cpu".equals(f[0])) throw new IllegalArgumentException();
            long total = 0;
            for (int i = 1; i < Math.min(f.length, 9); i++) {
                long value = Long.parseLong(f[i]);
                if (value < 0) throw new IllegalArgumentException();
                total = Math.addExact(total, value);
            }
            return new SystemCpuSnapshot(total,
                    Math.addExact(Long.parseLong(f[4]), f.length > 5 ? Long.parseLong(f[5]) : 0));
        } catch (RuntimeException error) { throw new IOException("invalid aggregate /proc/stat", error); }
    }
    private SystemCpuReader() { }
}
