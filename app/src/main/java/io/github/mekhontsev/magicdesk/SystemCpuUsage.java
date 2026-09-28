package io.github.mekhontsev.magicdesk;

/** A rate needs two valid observations from the same source lifetime. Unknown is not idle. */
final class SystemCpuUsage {
    private SystemCpuSnapshot previous = SystemCpuSnapshot.UNKNOWN;
    float sample(SystemCpuSnapshot next) {
        var before = previous;
        previous = next;
        if (!before.available() || !next.available() || next.total() <= before.total() || next.idle() < before.idle()) return -1;
        long total = next.total() - before.total(), idle = next.idle() - before.idle();
        return idle > total ? -1 : 1 - (float) idle / total;
    }
}
