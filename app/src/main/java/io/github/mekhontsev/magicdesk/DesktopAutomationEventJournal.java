package io.github.mekhontsev.magicdesk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Bounded process-local history for structured automation operations. */
final class DesktopAutomationEventJournal {
    private static final int MAX_EVENTS = 256;
    private static final int MAX_DETAIL_CHARS = 1_000;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<Event> EVENTS = new ArrayDeque<>();
    static final int MAX_PAGE_WAITERS = 2;
    private static long sLastId;
    private static int sWaiters;

    private DesktopAutomationEventJournal() {
    }

    static long record(
            final String type,
            final String operation,
            final boolean success,
            final String detail) {
        return record(type, operation, success, detail, null);
    }

    static long record(
            final String type,
            final String operation,
            final boolean success,
            final String detail,
            final JSONObject data) {
        final String eventType = clean(type);
        final String eventOperation = clean(operation);
        final String eventDetail = clean(detail);
        final JSONObject eventData = copy(data);
        synchronized (LOCK) {
            // The cursor identifies published history, not an in-flight writer.
            final Event event = new Event(
                    ++sLastId, System.currentTimeMillis(), eventType,
                    eventOperation, success, eventDetail, eventData);
            EVENTS.addLast(event);
            while (EVENTS.size() > MAX_EVENTS) {
                EVENTS.removeFirst();
            }
            LOCK.notifyAll();
            return event.id;
        }
    }

    static long awaitChange(
            final long observedId,
            final long timeoutMillis) throws InterruptedException {
        final long deadline = android.os.SystemClock.uptimeMillis()
                + Math.max(0L, timeoutMillis);
        synchronized (LOCK) {
            long remaining = timeoutMillis;
            while (sLastId <= observedId && remaining > 0L) {
                EventDrivenWaits.await(
                        LOCK,
                        EventDrivenWaits.Reason.AUTOMATION_EVENT,
                        remaining);
                remaining = deadline - android.os.SystemClock.uptimeMillis();
            }
            return sLastId;
        }
    }

    static JSONArray snapshot(final long afterId, final int requestedLimit)
            throws JSONException {
        return snapshotWithCursor(afterId, requestedLimit).events;
    }

    static Snapshot snapshotWithCursor(final long afterId, final int requestedLimit)
            throws JSONException {
        final int limit = Math.max(1, Math.min(MAX_EVENTS, requestedLimit));
        final List<Event> copy;
        final long latestId;
        synchronized (LOCK) {
            copy = new ArrayList<>(EVENTS);
            latestId = sLastId;
        }
        final JSONArray result = new JSONArray();
        final int first = Math.max(0, copy.size() - limit);
        for (int index = first; index < copy.size(); index++) {
            final Event event = copy.get(index);
            if (event.id > afterId) {
                result.put(event.toJson());
            }
        }
        return new Snapshot(latestId, result);
    }

    /**
     * Returns the oldest retained events after {@code afterId}, waiting up to
     * {@code waitMillis} for one to be published when none exists yet.
     */
    static Page page(
            final long afterId,
            final int requestedLimit,
            final long waitMillis) throws InterruptedException, JSONException {
        final int limit = Math.max(1, Math.min(MAX_EVENTS, requestedLimit));
        final long cursor = Math.max(0L, afterId);
        final List<Event> selected = new ArrayList<>();
        final long latestId;
        final boolean truncated;
        final boolean hasMore;
        boolean waited = false;
        synchronized (LOCK) {
            if (waitMillis > 0L && sLastId <= cursor) {
                if (sWaiters >= MAX_PAGE_WAITERS) {
                    throw new IllegalStateException("too many concurrent event waits; retry later");
                }
                sWaiters++;
                waited = true;
                try {
                    final long deadline = System.nanoTime()
                            + TimeUnit.MILLISECONDS.toNanos(waitMillis);
                    long remaining = waitMillis;
                    while (sLastId <= cursor && remaining > 0L) {
                        // EVENT_WAIT: a journal publication wakes the reader; expiry returns an
                        // empty page with waitExpired and never implies that nothing happened later.
                        EventDrivenWaits.await(
                                LOCK, EventDrivenWaits.Reason.AUTOMATION_EVENT, remaining);
                        remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                    }
                } finally {
                    sWaiters--;
                }
            }
            latestId = sLastId;
            final Event oldest = EVENTS.peekFirst();
            truncated = oldest != null && oldest.id > cursor + 1L;
            for (final Event event : EVENTS) {
                if (event.id > cursor && selected.size() < limit) {
                    selected.add(event);
                }
            }
            hasMore = !selected.isEmpty()
                    && selected.get(selected.size() - 1).id < latestId;
        }
        final JSONArray events = new JSONArray();
        for (final Event event : selected) {
            events.put(event.toJson());
        }
        final long next = selected.isEmpty()
                ? cursor : selected.get(selected.size() - 1).id;
        return new Page(latestId, events, next, truncated, hasMore,
                waited && selected.isEmpty());
    }

    static final class Page {
        final long latestId;
        final JSONArray events;
        final long nextAfterId;
        final boolean truncated;
        final boolean hasMore;
        final boolean waitExpired;

        Page(
                final long latestId,
                final JSONArray events,
                final long nextAfterId,
                final boolean truncated,
                final boolean hasMore,
                final boolean waitExpired) {
            this.latestId = latestId;
            this.events = events;
            this.nextAfterId = nextAfterId;
            this.truncated = truncated;
            this.hasMore = hasMore;
            this.waitExpired = waitExpired;
        }
    }

    static final class Snapshot {
        final long latestId;
        final JSONArray events;

        Snapshot(final long latestId, final JSONArray events) {
            this.latestId = latestId;
            this.events = events;
        }
    }

    static int pageWaiters() {
        synchronized (LOCK) {
            return sWaiters;
        }
    }

    static long latestId() {
        synchronized (LOCK) {
            return sLastId;
        }
    }

    private static String clean(final String value) {
        if (value == null) {
            return "";
        }
        final String normalized = value.replace('\u0000', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        return BoundedText.prefix(normalized, MAX_DETAIL_CHARS);
    }

    private static JSONObject copy(final JSONObject value) {
        if (value == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(value.toString());
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private static final class Event {
        final long id;
        final long timestampMillis;
        final String type;
        final String operation;
        final boolean success;
        final String detail;
        final JSONObject data;

        Event(
                final long id,
                final long timestampMillis,
                final String type,
                final String operation,
                final boolean success,
                final String detail,
                final JSONObject data) {
            this.id = id;
            this.timestampMillis = timestampMillis;
            this.type = type;
            this.operation = operation;
            this.success = success;
            this.detail = detail;
            this.data = data;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id)
                    .put("timestampMillis", timestampMillis)
                    .put("type", type)
                    .put("operation", operation)
                    .put("success", success)
                    .put("detail", detail)
                    .put("data", new JSONObject(data.toString()));
        }
    }
}
