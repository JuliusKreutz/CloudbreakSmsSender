package dev.kreutz.cloudbreaksmssenderapp;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.telephony.SmsManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import dev.kreutz.cloudbreaksmssendershared.*;

/**
 * The main service that runs as a foreground service forever
 */
public class SmsSenderService extends Service implements Runnable {

    /**
     * If the service was started
     */
    private static boolean started = false;

    /**
     * DatagramPacket used for receiving multicast
     */
    private static final DatagramPacket PACKET = new DatagramPacket(new byte[0], 0);

    /**
     * Intent to use for sent sms
     */
    private static final String SMS_SENT = "dev.kreutz.smssenderapp.SMS_SENT";

    /**
     * Lock used for sms
     */
    private Semaphore smsSemaphore;

    /**
     * Status of the last sms
     */
    private boolean smsOk;

    /**
     * This method makes sure, that the service is started exactly once
     *
     * @param context The context this service should be started from
     */
    public static void start(Context context) {
        if (started)
            return;

        started = true;

        Intent serviceIntent = new Intent(context, SmsSenderService.class);
        ContextCompat.startForegroundService(context, serviceIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String channelId = "SmsSenderChannel";

        NotificationChannelCompat.Builder channelBuilder =
                new NotificationChannelCompat.Builder(channelId,
                        NotificationManager.IMPORTANCE_DEFAULT).setName(channelId);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.createNotificationChannel(channelBuilder.build());

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
        startForeground(startId, notificationBuilder.build());

        new Thread(this).start();

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void run() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                smsOk = getResultCode() == Activity.RESULT_OK;
                smsSemaphore.release();
            }
        };

        registerReceiver(receiver, new IntentFilter(SMS_SENT));

        while (!Thread.interrupted()) {
            waitForAddress();
            eventLoop();
        }

        unregisterReceiver(receiver);
    }

    /**
     * Waits for a multicast packet and if received saved the address in PACKET
     *
     * @see #PACKET
     */
    private void waitForAddress() {
        String tag = "lock";
        WifiManager wifiManager = getSystemService(WifiManager.class);
        WifiManager.WifiLock wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, tag);
        WifiManager.MulticastLock multicastLock = wifiManager.createMulticastLock(tag);
        multicastLock.setReferenceCounted(true);

        wifiLock.acquire();
        multicastLock.acquire();

        for (; ; ) {
            try (MulticastSocket socket = new MulticastSocket(Const.MULTICAST_PORT)) {
                InetAddress address = InetAddress.getByName(Const.MULTICAST_ADDRESS);

                socket.joinGroup(address);
                socket.receive(PACKET);
                socket.leaveGroup(address);

                break;
            } catch (ClosedByInterruptException e) {
                break;
            } catch (IOException ignored) {
            }
        }

        multicastLock.release();
        wifiLock.release();
    }

    /**
     * Process ping and send sms requests
     */
    private void eventLoop() {
        try (Socket socket = new Socket(PACKET.getAddress(), Const.TCP_PORT);
             ObjectOutputStream writer = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream reader = new ObjectInputStream(socket.getInputStream())) {

            for (; ; ) {
                Object request = reader.readObject();

                if (request == null)
                    break;

                if (request instanceof NameRequest) {
                    writer.writeObject(new NameResponse(getName()));
                } else if (request instanceof GroupsRequest) {
                    writer.writeObject(new GroupsResponse(new TreeSet<>(getGroups().keySet())));
                } else if (request instanceof NumbersRequest) {
                    NumbersRequest numbersRequest = (NumbersRequest) request;
                    Set<String> numbers = numbersRequest.getGroups().stream()
                            .map(this::getAllNumbersFromGroupId)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet());
                    writer.writeObject(new NumbersResponse(numbers));
                } else if (request instanceof SendSmsRequest) {
                    SendSmsRequest sendSmsRequest = (SendSmsRequest) request;
                    smsSemaphore = new Semaphore(0);
                    int smsAmount = sendSms(sendSmsRequest.getNumber(), sendSmsRequest.getText());
                    smsSemaphore.acquire(smsAmount);

                    writer.writeObject(new SendSmsResponse(smsOk));
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Sends sms to all the numbers
     *
     * @param number The number to send the sms to
     * @param text   The text of the sms
     */
    private int sendSms(String number, String text) {
        SmsManager smsManager = getSystemService(SmsManager.class);
        ArrayList<String> messages = smsManager.divideMessage(text);

        ArrayList<PendingIntent> intents = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            intents.add(PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), PendingIntent.FLAG_IMMUTABLE));
        }

        smsManager.sendMultipartTextMessage(number, null, messages, intents, null);
        return messages.size();
    }

    /**
     * Get the phones name saved in shared preferences
     *
     * @return Phone name
     */
    private String getName() {
        SharedPreferences sharedPreferences = getSharedPreferences("name", MODE_PRIVATE);
        return sharedPreferences.getString("name", null);
    }

    /**
     * @return All the groups with their id with at least 1 number in it.
     */
    private Map<String, String> getGroups() {
        ContentResolver resolver = getContentResolver();

        String selection = ContactsContract.Groups.SUMMARY_WITH_PHONES + "!=0";
        Cursor cursor = resolver.query(ContactsContract.Groups.CONTENT_SUMMARY_URI, null, selection, null, null);

        Map<String, String> groups = new HashMap<>();
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            int index = cursor.getColumnIndex(ContactsContract.Groups.TITLE);
            String groupTitle = cursor.getString(index);

            index = cursor.getColumnIndex(ContactsContract.Groups._ID);
            String groupId = cursor.getString(index);

            groups.put(groupTitle, groupId);
        }

        cursor.close();

        return groups;
    }

    /**
     * Query all numbers from a group
     *
     * @param group The group you want all the numbers from
     * @return A set of all numbers in that group
     */
    public Set<String> getAllNumbersFromGroupId(String group) {
        String groupId = getGroups().get(group);

        ContentResolver resolver = getContentResolver();

        String[] projection = new String[]{ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID};
        String selection = ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID + "=? and "
                + ContactsContract.CommonDataKinds.GroupMembership.MIMETYPE + "='"
                + ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE + "'";
        String[] selectionArgs = new String[]{groupId};
        Cursor groupCursor = resolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, selectionArgs, null);

        Set<String> numbers = new HashSet<>();
        for (groupCursor.moveToFirst(); !groupCursor.isAfterLast(); groupCursor.moveToNext()) {
            int index = groupCursor.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.CONTACT_ID);
            long contactId = groupCursor.getLong(index);

            projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
            selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?";
            selectionArgs = new String[]{String.valueOf(contactId)};
            Cursor numberCursor = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection, selectionArgs, null);

            for (numberCursor.moveToFirst(); !numberCursor.isAfterLast(); numberCursor.moveToNext()) {
                index = numberCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                String number = numberCursor.getString(index);
                numbers.add(number);
            }

            numberCursor.close();
        }

        groupCursor.close();

        return numbers;
    }
}
