package dev.kreutz.cloudbreaksmssenderapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * This class automatically starts the SmsSenderService after boot
 *
 * @see SmsSenderService
 */
public class Autostart extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            SmsSenderService.start(context);
        }
    }
}
