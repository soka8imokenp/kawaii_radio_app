package uz.kawaii.radio.kr01;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;

/**
 * Hands a downloaded update APK to Android's package installer.
 * Android 8+ only lets an app start the installer after the user allowed
 * "Install unknown apps" for it, so that permission is checked and its settings page can be opened.
 */
@CapacitorPlugin(name = "ApkInstaller")
public class ApkInstallerPlugin extends Plugin {

    private boolean granted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getContext().getPackageManager().canRequestPackageInstalls();
    }

    @PluginMethod
    public void canInstall(PluginCall call) {
        JSObject res = new JSObject();
        res.put("granted", granted());
        call.resolve(res);
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                getActivity().startActivity(intent);
            } catch (Exception e) {
                call.reject("Cannot open settings: " + e.getMessage(), "SETTINGS");
                return;
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void install(PluginCall call) {
        String path = call.getString("path", "");
        File file = path.startsWith("file:") ? new File(Uri.parse(path).getPath()) : new File(path);
        if (!file.exists() || file.length() == 0) {
            call.reject("Update file not found", "NOT_FOUND");
            return;
        }
        if (!granted()) {
            call.reject("Install permission not granted", "NO_PERMISSION");
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Cannot start installer: " + e.getMessage(), "FAILED");
        }
    }
}
