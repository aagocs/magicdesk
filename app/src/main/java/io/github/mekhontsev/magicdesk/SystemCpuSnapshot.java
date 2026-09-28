package io.github.mekhontsev.magicdesk;

import android.os.Parcel;
import android.os.Parcelable;

/** Aggregate counters only; reading these never enumerates processes. */
public record SystemCpuSnapshot(long total, long idle) implements Parcelable {
    static final SystemCpuSnapshot UNKNOWN = new SystemCpuSnapshot(-1, -1);
    boolean available() { return total >= 0 && idle >= 0 && idle <= total; }
    public static final Creator<SystemCpuSnapshot> CREATOR = new Creator<>() {
        @Override public SystemCpuSnapshot createFromParcel(Parcel source) {
            return new SystemCpuSnapshot(source.readLong(), source.readLong());
        }
        @Override public SystemCpuSnapshot[] newArray(int size) { return new SystemCpuSnapshot[size]; }
    };
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel destination, int flags) {
        destination.writeLong(total); destination.writeLong(idle);
    }
}
