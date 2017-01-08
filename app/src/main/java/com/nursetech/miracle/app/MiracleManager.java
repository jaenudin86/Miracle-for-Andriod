package com.nursetech.miracle.app;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.ArrayAdapter;

import com.google.common.collect.ImmutableMap;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import static com.nursetech.miracle.app.MiracleManager.State.ALERT;
import static com.nursetech.miracle.app.MiracleManager.State.END_OF_CYCLE;
import static com.nursetech.miracle.app.MiracleManager.State.INSERTION;
import static com.nursetech.miracle.app.MiracleManager.State.REMOVAL;
import static com.nursetech.miracle.app.MiracleManager.State.STANDBY_WAITING_FOR_CONTAINER_TO_RETURN;
import static com.nursetech.miracle.app.MiracleManager.State.UNKNOWN;


public class MiracleManager extends Handler{
    private String MEDICATION = "Ibuprofen";
    private int alertCount = 0;
    private MainActivity mActivity;
    private ArrayAdapter<String> mArrayAdapter;
    private final int NOTIFICATION_ID = 001;
    private NotificationManagerCompat notificationManager;
	private static final String TAG = MiracleManager.class.getSimpleName();
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);



    public enum State {
        STANDBY_WAITING_FOR_CONTAINER_TO_RETURN, ALERT, REMOVAL, INSERTION, END_OF_CYCLE, UNKNOWN
    }

    final private Map<String, State> mStateHashMap = new ImmutableMap.Builder<String, State>()
        .put("EVENT,1,1", ALERT)
        .put("EVENT,1,2", STANDBY_WAITING_FOR_CONTAINER_TO_RETURN)
        .put("EVENT,0,0", REMOVAL)
        .put("EVENT,0,1", INSERTION)
        .put("EVENT,1,3", END_OF_CYCLE)
        .put("EVENT,0,-1", UNKNOWN).build();

    MiracleManager(MainActivity activity, ArrayAdapter<String> arrayAdapter) {
        mActivity = activity;
        mArrayAdapter = arrayAdapter;
        notificationManager = NotificationManagerCompat.from(activity);
    }

	@Override
	public void handleMessage(Message msg) {
		final String parsedMessage = ((String) msg.obj).replace("EE","E");
		Log.d(TAG, parsedMessage);
		String message;
		final String date = DATE_FORMAT.format(Calendar.getInstance().getTime());
		final State state = mStateHashMap.get(parsedMessage);
		if(state != null) {
			switch (state) {
				case ALERT:
					message = "Alert " + alertCount++ + " at " + date;
					mArrayAdapter.add(message);
					generateNotification(ALERT.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
					break;
				case REMOVAL:
					message = "Container removed at " + date;
					mArrayAdapter.add("Container removed at " + date);
					//generateNotification(REMOVAL.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
					break;
				case INSERTION:
					message = "Container returned at " + date;
					mArrayAdapter.add(message);
					//generateNotification(INSERTION.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
					break;
				case END_OF_CYCLE:
					message = "End of dosing cycle!";
					mArrayAdapter.add(message);
					generateNotification(END_OF_CYCLE.ordinal(), R.drawable.ic_launcher, MEDICATION, message);
					alertCount=0;
					break;
			}
		}
	}

    private void generateNotification(int eventId, int ic_event, CharSequence eventTitle, CharSequence eventDetails) {
        Intent viewIntent = new Intent(mActivity, MainActivity.class);
        //viewIntent.putExtra(EXTRA_EVENT_ID, eventId);
        PendingIntent viewPendingIntent =
                PendingIntent.getActivity(mActivity, 0, viewIntent, 0);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(mActivity)
                        .setSmallIcon(ic_event)
                        .setContentTitle(eventTitle)
                        .setContentText(eventDetails)
                        .setContentIntent(viewPendingIntent)
                        .setVibrate(new long[] {0, 500, 50, 500} );

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }
}
