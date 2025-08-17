package moe.shizuku.manager;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.activity.ComponentActivity;
import java.lang.reflect.Method;
import moe.shizuku.manager.home.HomeActivity;

public class MainActivity extends ComponentActivity {
    @Override
    protected void onStart() {
        super.onStart();
        // Launch HomeActivity 500ms later
        final long delay = 500L;
        // Setting launch windowing mode to free-form
        int freeformStackId = 5;

        // Start Settings
        Intent intent = new Intent(Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT |
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK);
//            intent.putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        ActivityOptions options = ActivityOptions.makeBasic();
        try {
            Method method = ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class);
            method.invoke(options, freeformStackId);
        } catch (Exception ignored) { /* Gracefully fail */
        }
        Rect bounds = new Rect(0, 0, 1400, 1450);
        options = options.setLaunchBounds(bounds);
        try {
            startActivity(intent, options.toBundle());
        } catch (ActivityNotFoundException ignored) {
        }

        // Start HomeActivity
        Intent intentHome = new Intent(this, HomeActivity.class);
        intentHome.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT |
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ActivityOptions optionsHome = ActivityOptions.makeBasic();
        try {
            Method method = ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class);
            method.invoke(optionsHome, freeformStackId);
        } catch (Exception ignored) { /* Gracefully fail */
        }
        Rect boundsHome = new Rect(1405, 0, 1200, 1450);
        ActivityOptions finalOptionsHome = optionsHome.setLaunchBounds(boundsHome);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            try {
                startActivity(intentHome, finalOptionsHome.toBundle());
            } catch (ActivityNotFoundException ignored) { /* Gracefully fail */
            }
            this.onDestroy();
        }, delay);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
