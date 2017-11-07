package de.rbrune.fuelbandsync;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class FuelbandsyncReceiver extends BroadcastReceiver {

    private static final String TAG = "FuelbandsyncReceiver";

    private static final String FUELBAND_DEVICENAME = "Nike+ FuelBand";


    public FuelbandsyncReceiver() {
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {

        String action = intent.getAction();
        Log.d(TAG, action);

        if(action.equals("android.bluetooth.device.action.ACL_CONNECTED")){

            final PendingResult result = goAsync();
            Thread thread = new Thread() {
                public void run() {
                    //int i;

                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.d(TAG, "Received: Bluetooth device connected: " + device.getName());
                    if(device.getName().equals(FUELBAND_DEVICENAME)) {
                        Intent intent = new Intent(context, FuelbandsyncIntentService.class);
                        intent.setAction(FuelbandsyncIntentService.ACTION_SYNC);
                        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
                        context.startService(intent);
                    }





                    //result.setResultCode(i);
                    result.finish();
                }
            };
            thread.start();


        }

        //throw new UnsupportedOperationException("Not yet implemented");
    }



}
