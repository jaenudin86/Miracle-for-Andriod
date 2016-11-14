package com.nursetech.miracle.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.CountDownTimer;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.nursetech.miracle.app.Database.Actions;
import com.nursetech.miracle.app.Database.ToDoItem;

import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

/*
 * On alert: when =0 and arg1 = 10 arg2 = 2
 */
public class MiracleManager {
    private String MEDICATION = "Ibuprofen";
    private State currentState = State.INITIAL;
    private int alertCount = 0;
    private String incomingSequence = "";
    private MainActivity activity;
    private ArrayAdapter<String> mArrayAdapter;
    private final int NOTIFICATION_ID = 001;
    NotificationManagerCompat notificationManager;
    private MobileServiceClient mClient;

    public enum State {
        INITIAL, STANDBY, ALERT, PROPER_REMOVAL_OR_REPLACE, PREMATURE_REMOVAL_OR_REPLACE
    }
    private CountDownTimer timer = new CountDownTimer(300, 300) {

        public void onTick(long millisUntilFinished) {
        }

        public void onFinish() {
            if(incomingSequence.charAt(0)=='1' && incomingSequence.charAt(incomingSequence.length()-1)=='7')
                currentState = State.INITIAL;
            else{
                State newState = validCodeSums.get(sumDigits(incomingSequence));
                if (newState == null)
                    Log.d("DARIEN", "ERROR ON: " + incomingSequence);
                postMessage(newState);
            }
            incomingSequence = "";
        }
    };
    private HashMap<Integer, State> validCodeSums = new HashMap<Integer, State>() {{
        put(1, State.ALERT);
        put(10, State.ALERT);
        put(11, State.INITIAL);
        put(20, State.INITIAL);
        put(21, State.PREMATURE_REMOVAL_OR_REPLACE);
        put(31, State.PROPER_REMOVAL_OR_REPLACE);
        put(41, State.PROPER_REMOVAL_OR_REPLACE);
        put(22, State.PROPER_REMOVAL_OR_REPLACE);
    }};

    public void testNotification() {
        postMessage(State.INITIAL);
    }

    public MiracleManager(MainActivity activity, ArrayAdapter<String> mArrayAdapter) {
        this.activity = activity;
        this.mArrayAdapter = mArrayAdapter;
        notificationManager = NotificationManagerCompat.from(activity);
        try {
            mClient = new MobileServiceClient("https://ownum.azurewebsites.net", activity);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public void processMessage(Message message) {
        timer.cancel();
        timer.start();
        incomingSequence += message.arg1;
        Log.d("DARIEN", "CODE: " + incomingSequence);
    }

    public void postMessage(State incomingState) {
        String date = getDate();
        if(incomingState != null) {
            ToDoItem item = new ToDoItem();
            item.text = "Test Program";
            item.complete = false;

            item.actionName = incomingState.name();
            item.actionValue = incomingState.ordinal();
            item.medicineId = 1;
            item.userId = 1;
            mClient.getTable(ToDoItem.class).insert(item, (entity, exception, response) -> {
                if (exception == null) {
                    Toast.makeText(activity, "Success", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "Failure", Toast.LENGTH_SHORT).show();
                    System.out.println("YOLOZ " + exception.getLocalizedMessage());
                    System.out.println("YOLOZ2 " + exception.getStackTrace());
                    System.out.println("YOLOZ3 " + exception.getCause());
                }
            });
        }

        switch (currentState) {
            case INITIAL:
                if (incomingState.equals(State.INITIAL)) {
                    final String message = "Device starting up at " + date;
                    mArrayAdapter.add(message);
                    currentState = State.STANDBY;
                    generateNotification(incomingState.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
                }
                break;
            case STANDBY:
                if (incomingState.equals(State.ALERT)) {
                    alertCount++;
                    final String message = "Alert " + alertCount + " at " + date;
                    mArrayAdapter.add(message);
                    currentState = State.ALERT;
                    generateNotification(incomingState.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
                } else if (incomingState.equals(State.PREMATURE_REMOVAL_OR_REPLACE)) {
                    final String message = "Premature removal at " + date;
                    mArrayAdapter.add(message);
                    currentState = State.PREMATURE_REMOVAL_OR_REPLACE;
                    generateNotification(incomingState.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
                }
                break;
            case ALERT:
                if (incomingState.equals(State.PROPER_REMOVAL_OR_REPLACE)) {
                    final String message = "Container removed at " + date;
                    mArrayAdapter.add(message);
                    currentState = State.PROPER_REMOVAL_OR_REPLACE;
                    generateNotification(incomingState.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
                } else if (incomingState.equals(State.ALERT)) {
                    final String message = "??? hit an Invalid State " + date;
                    mArrayAdapter.add(message);
                    currentState = State.STANDBY;
                    generateNotification(incomingState.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
                }
                break;
            case PROPER_REMOVAL_OR_REPLACE:
                if (incomingState.equals(State.PROPER_REMOVAL_OR_REPLACE) || incomingState.equals(State.PREMATURE_REMOVAL_OR_REPLACE)) {
                    final String message = "Container returned at " + date;
                    mArrayAdapter.add(message);
                    currentState = State.STANDBY;
                    generateNotification(incomingState.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
                }
                break;
            case PREMATURE_REMOVAL_OR_REPLACE:
                if (incomingState.equals(State.PREMATURE_REMOVAL_OR_REPLACE)) {
                    final String message = "Container returned at " + date;
                    mArrayAdapter.add(message);
                    currentState = State.STANDBY;
                }
                break;
            default:
                break;
        }
    }

    private String getDate() {
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(c.getTime());
    }

    private int sumDigits(String input){
        int sum = 0;
        for(char s : input.toCharArray())
            sum+=s-'0';
        return sum;
    }

    private void generateNotification(int eventId, int ic_event, CharSequence eventTitle, CharSequence eventDetails) {
        Intent viewIntent = new Intent(activity, MainActivity.class);
        //viewIntent.putExtra(EXTRA_EVENT_ID, eventId);
        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(activity, 0, viewIntent, 0);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(activity)
                        .setSmallIcon(ic_event)
                        .setContentTitle(eventTitle)
                        .setContentText(eventDetails)
                        .setContentIntent(viewPendingIntent)
                        .setVibrate(new long[] {0, 500, 50, 500} );

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
}
