package edu.berkeley.cs.amplab.carat.android;

import java.util.HashMap;
import java.util.Map;

import edu.berkeley.cs.amplab.carat.android.protocol.CommunicationManager;
import edu.berkeley.cs.amplab.carat.android.sampling.Sampler;
import edu.berkeley.cs.amplab.carat.android.sampling.SamplingLibrary;
import edu.berkeley.cs.amplab.carat.android.storage.CaratDataStorage;
import edu.berkeley.cs.amplab.carat.thrift.Reports;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TextView;

/**
 * Application class for Carat Android App. Place App-global static constants
 * and methods here.
 * 
 * @author Eemil Lagerspetz
 * 
 */
public class CaratApplication extends Application {

    // Not in Android 2.2:
    public static final int IMPORTANCE_PERCEPTIBLE = 130;

    // Used for bugs and hogs, and drawing
    public enum Type {
        OS, MODEL, HOG, BUG, SIMILAR, JSCORE
    }

    public static final String CARAT_OLD = "edu.berkeley.cs.amplab.carat";

    // Alarm event for sampling when battery has not changed for
    // SAMPLE_INTERVAL_MS.
    public static final String ACTION_CARAT_SAMPLE = "edu.berkeley.cs.amplab.carat.android.ACTION_SAMPLE";
    // If true, install Sampling events to occur at boot. Currently not used.
    public static final String PREFERENCE_SAMPLE_FIRST_RUN = "carat.sample.first.run";

    // Report Freshness timeout. Default: 10 minutes
    public static final long FRESHNESS_TIMEOUT = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
    // If true, register this as a new device on the Carat server
    public static final String PREFERENCE_FIRST_RUN = "carat.first.run";

    // Send samples every 15 minutes
    public static final long COMMS_INTERVAL = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
    // When waking up from screen off, wait 5 seconds for wifi etc to come up
    public static final long COMMS_WIFI_WAIT = 5 * 1000;
    // Send up to 10 samples at a time
    public static final int COMMS_MAX_UPLOAD_BATCH = 10;

    // NOTE: This needs to be initialized before CommunicationManager.
    public static CaratDataStorage s = null;
    // NOTE: The CommunicationManager requires a working instance of
    // CaratDataStorage.
    public CommunicationManager c = null;

    private static CaratMainActivity main = null;
    private static CaratMyDeviceActivity myDevice = null;
    private static CaratBugsOrHogsActivity bugsActivity = null;
    private static CaratBugsOrHogsActivity hogsActivity = null;
    private static CaratSuggestionsActivity actionList = null;

    private static Sampler sampler = null;

    private static final Map<Integer, String> importanceToString = new HashMap<Integer, String>();
    {
        importanceToString.put(RunningAppProcessInfo.IMPORTANCE_EMPTY,
                "Not running");
        importanceToString.put(RunningAppProcessInfo.IMPORTANCE_BACKGROUND,
                "Background process");
        importanceToString.put(RunningAppProcessInfo.IMPORTANCE_SERVICE,
                "Service");
        importanceToString.put(RunningAppProcessInfo.IMPORTANCE_VISIBLE,
                "Visible task");
        importanceToString.put(RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                "Foreground app");

        importanceToString.put(IMPORTANCE_PERCEPTIBLE, "Perceptible task");
    }

    public static String importanceString(int importance) {
        String s = importanceToString.get(importance);
        if (s == null || s.length() == 0) {
            Log.e("Importance not found:", "" + importance);
            s = "Unknown";
        }
        return s;
    }

    // default icon:
    public static String CARAT_PACKAGE = "edu.berkeley.cs.amplab.carat.android";

    /*
     * FIXME: Storing and retrieving totalAndused here only for testing. They
     * should really be stored in CaratDB and retrieved as part of sampling.
     */
    public int[] totalAndUsed = null;
    /*
     * FIXME: Storing and retrieving CPU here only for testing. It should really
     * be stored in CaratDB and retrieved as part of sampling.
     */
    public int cpu = 0;

    // Utility methods

    /**
     * Return a Drawable that contains an app icon for the named app. If not
     * found, return the Drawable for the Carat icon.
     * 
     * @param appName
     *            the application name
     * @return the Drawable for the application's icon
     */
    public static Drawable iconForApp(Context c, String appName) {
        try {
            return c.getPackageManager().getApplicationIcon(appName);
        } catch (NameNotFoundException e) {
            return c.getResources().getDrawable(R.drawable.ic_launcher);
        }
    }

    /**
     * Return a human readable application label for the named app. If not
     * found, return appName.
     * 
     * @param appName
     *            the application name
     * @return the human readable application label
     */
    public static String labelForApp(Context c, String appName) {
        try {
            ApplicationInfo i = c.getPackageManager().getApplicationInfo(
                    appName, 0);
            if (i != null)
                return c.getPackageManager().getApplicationLabel(i).toString();
            else
                return appName;
        } catch (NameNotFoundException e) {
            return appName;
        }
    }

    public static void setMyDeviceText(final int viewId, final String text) {
        if (myDevice != null) {
            main.runOnUiThread(new Runnable() {
                public void run() {
                    TextView t = (TextView) myDevice.findViewById(viewId);
                    if (t != null)
                        t.setText(text);
                }

            });
        }
    }

    public static void refreshBugs() {
        if (bugsActivity != null) {
            main.runOnUiThread(new Runnable() {
                public void run() {
                    bugsActivity.refresh();
                }

            });
        }
    }

    public static void refreshHogs() {
        if (hogsActivity != null) {
            main.runOnUiThread(new Runnable() {
                public void run() {
                    hogsActivity.refresh();
                }

            });
        }
    }

    public static void setActionInProgress() {
        if (main != null) {
            main.runOnUiThread(new Runnable() {
                public void run() {
                    // Updating done
                    main.setTitleUpdating("My Device");
                    main.setProgress(0);
                    main.setProgressBarVisibility(true);
                    main.setProgressBarIndeterminateVisibility(true);
                }
            });
        }
    }

    public static void refreshActions() {
        if (actionList != null) {
            main.runOnUiThread(new Runnable() {
                public void run() {
                    actionList.refresh();
                }
            });
        }
    }

    public static void setActionProgress(final int progress, final String what,
            final boolean fail) {
        if (main != null) {
            main.runOnUiThread(new Runnable() {
                public void run() {
                    if (fail)
                        main.setTitleUpdatingFailed(what);
                    else
                        main.setTitleUpdating(what);
                    main.setProgress(progress * 100);
                }
            });
        }
    }

    public static void setActionFinished() {
        if (main != null) {
            main.runOnUiThread(new Runnable() {
                public void run() {
                    // Updating done
                    main.setTitleNormal();
                    main.setProgress(100);
                    main.setProgressBarVisibility(false);
                    main.setProgressBarIndeterminateVisibility(false);
                }
            });
        }
    }

    public static void setMain(CaratMainActivity a) {
        main = a;
    }

    public static void setMyDevice(CaratMyDeviceActivity a) {
        myDevice = a;
    }

    public static void setBugs(CaratBugsOrHogsActivity a) {
        bugsActivity = a;
    }

    public static void setHogs(CaratBugsOrHogsActivity a) {
        hogsActivity = a;
    }

    public static void setActionList(CaratSuggestionsActivity a) {
        actionList = a;
    }

    // Application overrides

    /**
     * 1. Create CaratDataStorage and read reports from disk. Does not seem too
     * slow.
     * 
     * 2. Take a sample in a new thread so that the GUI has fresh data TODO:
     * Sampling is currently delayed until we get battery stats. What to do on
     * the first time?
     * 
     * 3. Create CommunicationManager for communicating with the Carat server
     * TODO: Uses fake data at the moment. TODO: When and by which class to
     * record UUID, OS, MODEL for this?
     * 
     * 4. Communicate with the server to fetch new reports if current ones are
     * outdated, and to send old stored and the new just-recorded sample. See
     * CaratMainActivity for this task. TODO: latest sample for GUI usage.
     */
    @Override
    public void onCreate() {
        s = new CaratDataStorage(this);

        new Thread() {
            public void run() {
                totalAndUsed = SamplingLibrary.readMeminfo();
                cpu = (int) (SamplingLibrary.readUsage() * 100);

                /*
                 * Schedule recurring sampling event:
                 */
                SharedPreferences p = PreferenceManager
                        .getDefaultSharedPreferences(CaratApplication.this);
                boolean firstRun = p.getBoolean(PREFERENCE_SAMPLE_FIRST_RUN,
                        true);
                // do this always for now for debugging purposes:
                // if (firstRun) {
                // What to start when the event fires (this is unused at the
                // moment)
                Intent intent = new Intent(getApplicationContext(),
                        Sampler.class);
                intent.setAction(ACTION_CARAT_SAMPLE);
                // In reality, you would want to have a static variable for the
                // request
                // code instead of 192837
                PendingIntent sender = PendingIntent.getBroadcast(
                        CaratApplication.this, 192837, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                // Cancel if this has been set up. Do not use timer at all any
                // more.
                sender.cancel();
                
                // Let sampling happen on battery change
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
                sampler = new Sampler();
                // unregisterReceiver(sampler);
                registerReceiver(sampler, intentFilter);
            }
        }.start();

        new Thread() {
            public void run() {
                c = new CommunicationManager(CaratApplication.this);
            }
        }.start();

        super.onCreate();
    }

    private static final String TAG = "CaratApp";

    private static final String TRY_AGAIN = " will try again in "
            + (FRESHNESS_TIMEOUT / 1000) + "s.";

    public void refreshUi() {
        boolean connecting = false;

        refreshActions();
        String networkStatus = SamplingLibrary
                .getNetworkStatus(getApplicationContext());
        if (networkStatus == SamplingLibrary.NETWORKSTATUS_CONNECTED
                && getApplicationContext() != null) {
            // Show we are updating...
            CaratApplication.setActionInProgress();
            try {
                c.refreshAllReports();
                // Log.d(TAG, "Reports refreshed.");
            } catch (Throwable th) {
                // Any sort of malformed response, too short string,
                // etc...
                Log.w(TAG, "Failed to refresh reports: " + th + TRY_AGAIN);
                th.printStackTrace();
            }
            connecting = false;

        } else if (networkStatus
                .equals(SamplingLibrary.NETWORKSTATUS_CONNECTING)) {
            Log.w(TAG, "Network status: " + networkStatus
                    + ", trying again in 10s.");
            connecting = true;
        }

        // do this regardless
        setReportData();
        // Update UI elements
        CaratApplication.refreshActions();
        CaratApplication.refreshBugs();
        CaratApplication.refreshHogs();
        CaratApplication.setActionProgress(90, "finishing up", false);

        if (!connecting)
            CaratApplication.setActionFinished();

        if (connecting) {
            // wait for wifi to come up
            try {
                Thread.sleep(CaratApplication.COMMS_WIFI_WAIT);
            } catch (InterruptedException e1) {
                // ignore
            }
            connecting = false;

            // Show we are updating...
            CaratApplication.setActionInProgress();
            try {
                c.refreshAllReports();
                // Log.d(TAG, "Reports refreshed.");
            } catch (Throwable th) {
                // Any sort of malformed response, too short string,
                // etc...
                Log.w(TAG, "Failed to refresh reports: " + th + TRY_AGAIN);
                th.printStackTrace();
            }
            connecting = false;

            // do this regardless
            setReportData();
            // Update UI elements
            refreshActions();
            refreshBugs();
            refreshHogs();
            setActionProgress(90, "finishing up", false);
        }
    }

    public static void setReportData() {
        final Reports r = s.getReports();
        Log.d(TAG, "Got reports: " + r);
        long freshness = CaratApplication.s.getFreshness();
        long l = System.currentTimeMillis() - freshness;
        final long h = l / 3600000;
        final long min = (l - h * 3600000) / 60000;
        double bl = 0;
        int jscore = 0;
        if (r != null) {
            // Try exact battery life
            if (r.jScoreWith != null) {
                double exp = r.jScoreWith.expectedValue;
                if (exp > 0.0)
                    bl = 100 / r.getModel().expectedValue;
                else if (r.getModel() != null) {
                    exp = r.getModel().expectedValue;
                    Log.d(TAG, "Model expected value: " + exp);
                    if (exp > 0.0)
                        bl = 100 / r.getModel().expectedValue;
                }
                // If not possible, try model battery life
            }
            jscore = ((int) (r.getJScore() * 100));
        }

        int blh = (int) (bl / 3600);
        bl -= blh * 3600;
        int blmin = (int) (bl / 60);
        int bls = (int) (bl - blmin * 60);
        final String blS = blh + "h " + blmin + "m " + bls + "s";
        setMyDeviceText(R.id.jscore_value, jscore + "");
        // Log.v(TAG, "Freshness: " + freshness);
        if (freshness <= 0)
            setMyDeviceText(R.id.updated, "(Never updated)");
        else if (min == 0)
            setMyDeviceText(R.id.updated, "(Updated just now)");
        else
            setMyDeviceText(R.id.updated, "(Updated " + h + "h " + min
                    + "m ago)");
        setMyDeviceText(R.id.batterylife_value, blS);
    }

    @Override
    public void onLowMemory() {
        // TODO Auto-generated method stub
        super.onLowMemory();
    }

    @Override
    public void onTerminate() {
        // TODO Auto-generated method stub
        super.onTerminate();
    }
}
