package fbk.climbloggerpro;

import android.os.Environment;

import java.io.File;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Created by user on 06/10/2015.
 */
public class ConfigVals {

    static String libVersion = "0.2.2";

    private static File root = Environment.getExternalStorageDirectory();

    static TimeZone tz = TimeZone.getTimeZone("Europe/Rome");

    static Calendar rightNow = Calendar.getInstance(tz);// .getInstance();*/
    public static String folderName = root.getAbsolutePath()+	"/CLIMB_log_data/"+rightNow.get(Calendar.YEAR)+"_"+ (rightNow.get(Calendar.MONTH) + 1) +"_"+ rightNow.get(Calendar.DAY_OF_MONTH) +"/";

    static final int NODE_TIMEOUT = 7 * 1000;
    static final int MON_NODE_TIMEOUT = 15 * 1000;
    static final int CONNECT_TIMEOUT = 30 * 1000;

    static final long MAX_WAKE_UP_DELAY_SEC = 259200;

    static final int vibrationTimeout = 15;

    static final int consecutiveBroadcastMessageTimeout_ms = 7000;

    static final int ALLERT_BATTERY_VOLTAGE_LEVEL_mV = 2000;

    static final int UI_UPDATE_INTERVAL_MS = 500;

    public final static String CLIMB_CHILD_DEVICE_NAME = "CLIMBC";
    public final static String CLIMB_MASTER_DEVICE_NAME = "CLIMBM";

    public static class Service {
        final static public UUID CLIMB                = UUID.fromString("0000FFF0-0000-1000-8000-00805f9b34fb");
    };

    public static class Characteristic {
        final static public UUID CIPO			      = UUID.fromString("0000FFF1-0000-1000-8000-00805f9b34fb");
        final static public UUID PICO		      = UUID.fromString("0000FFF2-0000-1000-8000-00805f9b34fb");

    }

    public static class Descriptor {// questo non deve essere cambiato perch� � un UUID definito dal protocollo (cambiandolo non si riesce pi� a ottenere il CCC descriptor)
        final static public UUID CHAR_CLIENT_CONFIG       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    }

}
