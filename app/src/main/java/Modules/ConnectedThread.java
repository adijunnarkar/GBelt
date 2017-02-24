package Modules;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

public class ConnectedThread extends Thread implements Runnable,
        Serializable {
    private final BluetoothSocket mmSocket;

    private final OutputStream mmOutStream;

    public ConnectedThread(BluetoothSocket socket) {
        mmSocket = socket;

        OutputStream tmpOut = null;

        try {

            tmpOut = socket.getOutputStream();
        } catch (IOException e) {

        }
        mmOutStream = tmpOut;
    }

    public void run() {
        //   byte[] buffer = new byte[1024];  // buffer store for the stream
        //   int bytes; // bytes returned from read()

        // Keep listening to the InputStream until an exception occurs
//            Toast.makeText(this, "Hi Hans sent", Toast.LENGTH_SHORT).show();
        while (true) {
//                write("#Hi Hans~".toString().getBytes());
//
//                try {
//                    Thread.sleep(500);
//                } catch(InterruptedException ex) {
//                    Thread.currentThread().interrupt();
//                }
        }
    }

    /* Call this from the main activity to send data to HC 05 */
    public void write(byte[] bytes) {
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) { }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) { }
    }
}