package io.github.mekhontsev.magicdesk;

import android.app.Activity;
import android.app.ActivityManager.TaskDescription;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;

/** Public task-description API differences; no shell or window-organizer dependency. */
final class FrameworkTaskDescriptionApi {
    private FrameworkTaskDescriptionApi() { }

    @SuppressWarnings("deprecation")
    static void publish(Activity activity, String label, int resource, Bitmap bitmap,
            int primary, int background) {
        TaskDescription.Builder builder = new TaskDescription.Builder()
                .setLabel(label).setPrimaryColor(primary).setBackgroundColor(background);
        if (bitmap == null) {
            activity.setTaskDescription(builder.setIcon(resource).build());
        } else if (Build.VERSION.SDK_INT >= 37) {
            activity.setTaskDescription(builder.setIcon(Icon.createWithBitmap(bitmap)).build());
        } else {
            // API 34-36 cannot combine a bitmap and background in one public constructor.
            // The bitmap constructor leaves background unset; Activity preserves the published color.
            activity.setTaskDescription(builder.build());
            activity.setTaskDescription(new TaskDescription(label, bitmap, primary));
        }
    }
}
