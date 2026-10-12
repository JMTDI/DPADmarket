package market.dpad.android;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

public class InstallStatusReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_STATUS = "market.dpad.android.INSTALL_STATUS";
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) { confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(confirmation); }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, intent.getStringExtra("app_name") + " installed", Toast.LENGTH_LONG).show();
        } else {
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(context, "Install did not complete" + (message == null ? "" : ": " + message), Toast.LENGTH_LONG).show();
        }
    }
}
