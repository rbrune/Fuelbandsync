package de.rbrune.fuelbandsync;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.os.Vibrator;
//import android.support.v7.app.NotificationCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;

public class FuelbandsyncIntentService extends IntentService {

    public static final String ACTION_SYNC = "de.rbrune.fuelbandsync.action.sync";


    private static final String TAG = "FuelbandsyncService";

    private static final String FUELBAND_DEVICENAME = "Nike+ FuelBand";
    private static final UUID FUELBAND_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private InputStream mmInStream;
    private OutputStream mmOutStream;

    public FuelbandsyncIntentService() {
        super("FuelbandsyncIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SYNC.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                //final String param1 = intent.getStringExtra(EXTRA_PARAM1);
                //final String param2 = intent.getStringExtra(EXTRA_PARAM2);
                handleActionSync(device);
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionSync(BluetoothDevice device) {

        Log.d(TAG, "Received: Bluetooth device connected: " + device.getName());
        if(device.getName().equals(FUELBAND_DEVICENAME)) {
            Vibrator vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
            //vibrator.vibrate(150);

            fuelbandConnect(device);

            vibrator.vibrate(150);
        }
    }


    private void fuelbandConnect(BluetoothDevice device) {

        BluetoothSocket mmSocket;
        BluetoothSocket tmp;

        // Get a BluetoothSocket for a connection with the
        // given BluetoothDevice
        try {
            tmp = device.createRfcommSocketToServiceRecord(FUELBAND_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket create() failed", e);
            return;
        }
        mmSocket = tmp;

        // Make a connection to the BluetoothSocket
        try {
            // This is a blocking call and will only return on a
            // successful connection or an exception
            mmSocket.connect();
        } catch (IOException e) {
            // Close the socket
            try {
                mmSocket.close();
            } catch (IOException e2) {
                Log.e(TAG, "unable to close() socket during connection failure", e2);
                return;
            }
            return;
        }


        InputStream tmpIn;
        OutputStream tmpOut;

        // Get the BluetoothSocket input and output streams
        try {
            tmpIn = mmSocket.getInputStream();
            tmpOut = mmSocket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "temp sockets not created", e);
            return;
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;





        byte[] buffer = new byte[1024];
        int bytes = 0;

        //buffer[0] = (byte)0xaa;
        //buffer[1] = (byte)0x08;
        //buffer[2] = (byte)0x08;

        //bytes = fuelbandSend(buffer, 2);

        byte[] workoutData;


        NotificationManager mNotifyManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContentTitle("Fuelband Sync")
                .setContentText("Bluetooth data download in progress")
                .setSmallIcon(R.mipmap.ic_launcher);

        mBuilder.setProgress(0, 0, true);
        mNotifyManager.notify(0, mBuilder.build());

        workoutData = fuelbandGetRawWorkoutData();


        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "close() of connect socket failed", e);
            return;
        }

        mBuilder.setContentText("Analysing received Fuelband data");
        mNotifyManager.notify(0, mBuilder.build());


        fuelbandAnalyzeRawWorkoutData(workoutData);


        Intent resultIntent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentText("Fuelband data ready for upload");
        mBuilder.setProgress(0, 0, false);
        mBuilder.setAutoCancel(true);
        mBuilder.setContentIntent(resultPendingIntent);

        mNotifyManager.notify(0, mBuilder.build());

    }

    private int fuelbandSend(byte[] t_buffer, int t_len) {
        int bytes = 0;

        //Log.i(TAG, "Trying to send something:");

        try {
            mmOutStream.write(t_buffer, 0, t_len);
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
            return 0;
        }

        //Log.i(TAG, "Trying to receive something");

        try {
            bytes = mmInStream.read(t_buffer);
        } catch (IOException e) {
            Log.e(TAG, "disconnected", e);
            return 0;
        }

        //Log.i(TAG, "Reveived " + bytes + " bytes of data!" );

        return bytes;
    }

    byte[] concatenateByteArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public static byte[] subByteArray(byte[] array, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(array, offset, result, 0, length);
        return result;
    }

    private long unsignedLongFromByteArray(byte[] bytes, int t_start, int t_len) {
        long res = 0;
        if (bytes == null)
            return res;

        for (int i = t_start; i < t_start+t_len; i++) {
            res = (res * 256) + ((bytes[i] & 0xff));
        }
        return res;
    }

    private byte[] fuelbandGetRawWorkoutData() {
        byte[] workoutData = new byte[0];
        byte[] tmpData;

        byte[] buffer = new byte[1024];
        int ret_bytes = 0;

        Log.i(TAG, "Requesting Fuelband data");

        buffer[0] = (byte)0xaa;
        buffer[1] = (byte)0x19;
        buffer[2] = (byte)0x00;
        buffer[3] = (byte)0x00;
        buffer[4] = (byte)0x00;

        while(buffer[1] != (byte)0x00) {
            buffer[1] = (byte)0x19;

            //Log.i(TAG, "Requesting workout @ offset: " + unsignedIntFromByteArray(buffer, 2, 3) );

            ret_bytes = fuelbandSend(buffer, 5);
            tmpData = subByteArray(buffer, 6, ret_bytes - 6);
            workoutData = concatenateByteArrays(workoutData, tmpData);


        }

        Log.i(TAG, "Received " + workoutData.length + " bytes of workout data");

        return workoutData;
    }


    private String getDate(long timeStamp){

        try{
            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Date netDate = (new Date(timeStamp));
            return sdf.format(netDate);
        }
        catch(Exception ex){
            return "xx";
        }
    }


    private int fuelbandAnalyzeRawWorkoutData(byte[] workoutData) {

        Log.i(TAG, "Analyzing received Fuelband data");

        FuelbandsyncDatabase mydb;
        mydb = new FuelbandsyncDatabase(this);



        long smpl_time_prev = 0;
        int cur_pt = 0;

        while (cur_pt + 2 <= workoutData.length) {
            if ((workoutData[cur_pt] == (byte)0x07) && (workoutData[cur_pt+1] == (byte)0x7d)) {
                //Log.i(TAG, "Envelope marker");
                cur_pt += 2;
            }
            else if ((workoutData[cur_pt] == (byte)0xc0) && (workoutData[cur_pt+1] == (byte)0x03) && (workoutData[cur_pt+2] == (byte)0x00) && (workoutData[cur_pt+3] == (byte)0x08)) {
                //Log.i(TAG, "Session marker: start");
                cur_pt += 4;
            }
            else if ((workoutData[cur_pt] == (byte)0xc0) && (workoutData[cur_pt+1] == (byte)0x03) && (workoutData[cur_pt+2] == (byte)0x00) && (workoutData[cur_pt+3] == (byte)0x09)) {
                //Log.i(TAG, "Session marker: end");
                cur_pt += 4;
            }
            else if ((workoutData[cur_pt] == (byte)0xf0) && (workoutData[cur_pt+1] == (byte)0x0f)) {
                //Log.i(TAG, "CRC");
                cur_pt += 4;
            }
            else if ((workoutData[cur_pt] == (byte)0xff) && (workoutData[cur_pt+1] == (byte)0xff)) {
                //Log.i(TAG, "Filler");
                cur_pt += 2;
            }
            else if ((workoutData[cur_pt] == (byte)0xa0) && (workoutData[cur_pt+1] == (byte)0x30)) {

                int  smpl_id    = (int)unsignedLongFromByteArray(workoutData, cur_pt+ 2,  2);
                long smpl_time  = 1000*unsignedLongFromByteArray(workoutData, cur_pt+ 4,  4);
                int  smpl_fuel  = (int)unsignedLongFromByteArray(workoutData, cur_pt+ 8,  2);
                int  smpl_cal   = (int)unsignedLongFromByteArray(workoutData, cur_pt+10,  2);
                int  smpl_steps = (int)unsignedLongFromByteArray(workoutData, cur_pt+12,  2);
                int  smpl_uknw1 = (int)unsignedLongFromByteArray(workoutData, cur_pt+14,  2);
                int  smpl_uknw2 = (int)unsignedLongFromByteArray(workoutData, cur_pt+16,  2);


                //Log.i(TAG, "Data " + smpl_id + " " + getDate(smpl_time) + " F=" + smpl_fuel + " C=" + smpl_cal + " S=" + smpl_steps + " U1=" + smpl_uknw1 + " U2=" + smpl_uknw2 );

                mydb.insertDatapoint(smpl_time, smpl_time_prev, smpl_fuel, smpl_cal, smpl_steps, smpl_uknw1, smpl_uknw2);

                cur_pt += 18;
            }
            else {
                Log.i(TAG, "UNKNOWN DATA TAG");
                cur_pt += 2;
            }


        }



        Log.i(TAG, "Row count in Fuelbandsync database " + mydb.numberOfRows());

        ArrayList<Long> test;
        test = mydb.getAllUnsyncedDatapoints();

        Log.i(TAG, "Unsynced row count in Fuelbandsync database " + test.size());

        //Log.i(TAG, "First entry " + test.get(0));
        //mydb.updateDatapointSync(test.get(0), 0);

        //ContentValues contentValues = mydb.getDatapoint(test.get(0));
        //Long endtime = contentValues.getAsLong(mydb.FBSYNC_COLUMN_TIME_END);
        //Long starttime = contentValues.getAsLong(mydb.FBSYNC_COLUMN_TIME_START);
        //Integer steps = contentValues.getAsInteger(mydb.FBSYNC_COLUMN_STEPS);

        //Log.i(TAG, "First entry: " + starttime + " " + endtime + " " + steps);

        Log.i(TAG, "Fine");


        return 0;
    }

}
