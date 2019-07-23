package com.dmartinez.morpheusalert;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.*;
import android.os.Handler;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.neurosky.thinkgear.*;

import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity {

//Main components
    BluetoothAdapter            bluetoothAdapter;
    TGDevice                    device;
//Widgets
    TextView                    tv; //tvLog
    TextView                    tv_signal_val, tv_attention_val, tv_theta_val, tv_alpha_low_val,
                                tv_alpha_high_val, tv_status_val, tv_beta_low_val, tv_beta_high_val,
                                tv_ratio_val, tv_counter_val, tv_time_val;
    ImageView                   ivSignal;
//Alarm related
    Vibrator                    vib;
    MediaPlayer                 alarm;
//Logic variables
    boolean                     rawEnabled = false, baselineDone = false;
    int                         attention = 0, thresholdCounter = 8, thresholdAttention = 40,
                                secsElapsed = 0, //seconds elapsed since device started
                                triggerCounter = 0,//Main counter to trigger the alarm;
                                ratioCnt = 0, attCnt = 0, //ratio and attention counters
                                signalQuality = -1,//signal quality
                                baselineElements = 0; //number of elements to get the baseline
    double                      sratioFactor = 0.86, //sleep ratio factor
                                alertFactor = 0.85,  //sleep attention factor
                                meanRatioBaseline = 0.0, //mean ratio baseline
                                meanAttBaseline = 0.0, //mean attention baseline
                                ratio = 0.0, thresholdRatio = 0.29;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv = (TextView) findViewById(R.id.tvLog_val);
        tv_signal_val = (TextView) findViewById(R.id.tvSignal_val);
        tv_attention_val = (TextView) findViewById(R.id.tvAttention_val);
        tv_theta_val = (TextView) findViewById(R.id.tvTheta_val);
        tv_alpha_low_val = (TextView) findViewById(R.id.tvAlphaLow_val);
        tv_alpha_high_val = (TextView) findViewById(R.id.tvAlphaHigh_val);
        tv_status_val = (TextView) findViewById(R.id.tvStatus_val);
        tv_beta_high_val = (TextView) findViewById(R.id.tvBetaHigh_val);
        tv_beta_low_val = (TextView) findViewById(R.id.tvBetaLow_val);
        tv_ratio_val = (TextView) findViewById(R.id.tvRatio_val);
        tv_counter_val = (TextView) findViewById(R.id.tvCounter);
        tv_time_val = (TextView) findViewById(R.id.tvTime);

        ivSignal = (ImageView) findViewById(R.id.imageViewSignal);
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        alarm = MediaPlayer.create(this, R.raw.alarm);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if( bluetoothAdapter == null ) {

            // Alert user that Bluetooth is not available
            Toast.makeText( this, "Bluetooth not available", Toast.LENGTH_LONG ).show();
            //finish();
           // return;

        } else {

            // create the TGDevice
            device = new TGDevice(bluetoothAdapter, handler);
        }
    }

    void SoundAlarm()
    {
        vib.vibrate(500);
       // alarm.start();

    }


    Handler handler = new Handler() {
        @Override
        public  void handleMessage(android.os.Message msg)
        {
            switch (msg.what)
            {
                case TGDevice.MSG_STATE_CHANGE:

                    switch( msg.arg1 ) {
                        case TGDevice.STATE_IDLE:
                            break;
                        case TGDevice.STATE_CONNECTING:
                            //tv.append( "Connecting...\n" );
                            tv_status_val.setText("Connecting...");
                            break;
                        case TGDevice.STATE_CONNECTED:
                            //tv.append( "Connected.\n" );
                            tv_status_val.setText("Connected");
                            device.start();
                            break;
                        case TGDevice.STATE_NOT_FOUND:
                            tv.setText( "Could not connect any of the paired BT devices.  Turn them on and try again.\n" );
                            break;
                        case TGDevice.STATE_ERR_NO_DEVICE:
                            tv.setText( "No Bluetooth devices paired.  Pair your device and try again.\n" );
                            break;
                        case TGDevice.STATE_ERR_BT_OFF:
                            tv.setText( "Bluetooth is off.  Turn on Bluetooth and try again." );
                            break;

                        case TGDevice.STATE_DISCONNECTED:
                            //tv.append( "Disconnected.\n" );
                            tv_status_val.setText("Disconnected");

                    }

                    break;

                case TGDevice.MSG_POOR_SIGNAL:
                    //tv.append( "PoorSignal: " + msg.arg1 + "\n" );
                    try {

                        signalQuality = msg.arg1;

                        tv_signal_val.setText(String.valueOf(msg.arg1));

                        if(msg.arg1 == 0)
                            ivSignal.setImageResource(R.drawable.connected_v1);
                        else
                            if(msg.arg1 > 0 && msg.arg1 <= 50)
                                ivSignal.setImageResource(R.drawable.connecting3_v1);
                        else
                            if(msg.arg1 > 50 && msg.arg1 <= 100)
                                ivSignal.setImageResource(R.drawable.connecting2_v1);
                        else
                            if(msg.arg1 >50 && msg.arg1 <=150)
                                ivSignal.setImageResource(R.drawable.connecting1_v1);
                        else
                            ivSignal.setImageResource(R.drawable.nosignal_v1);
                    }
                    catch (NullPointerException e)
                    {
                        tv_signal_val.setText("N/D");
                        signalQuality = 200;
                    }
                    break;

                case TGDevice.MSG_RAW_DATA:
                	/* Handle raw EEG/EKG data here*/
                    break;


                case TGDevice.MSG_ATTENTION:
                    //tv.append( "Attention: " + msg.arg1 + "\n" );
                    tv_attention_val.setText(String.valueOf(msg.arg1));
                    attention = msg.arg1;
                    //if(msg.arg1 == 100)
                      //  SoundAlarm();
                    break;

                case TGDevice.MSG_MEDITATION:
                    //tv.append( "Meditation: " + msg.arg1 + "\n" );
                    break;

                case TGDevice.MSG_BLINK:
                    //tv.append( "Blink: " + msg.arg1 + "\n" );
                    break;

                case TGDevice.MSG_EEG_POWER:
                    TGEegPower ep = (TGEegPower)msg.obj;
                    DecimalFormat df = new DecimalFormat("####0.00");

                    tv_theta_val.setText(String.valueOf(ep.theta));
                    tv_alpha_low_val.setText(String.valueOf(ep.lowAlpha));
                    tv_alpha_high_val.setText(String.valueOf(ep.highAlpha));
                    tv_beta_high_val.setText(String.valueOf(ep.highBeta));
                    tv_beta_low_val.setText(String.valueOf(ep.lowBeta));
                    ratio = 0.0;
                    if((ep.lowBeta+ep.highBeta) > 0)
                        ratio = (double)(ep.lowAlpha+ep.highAlpha+ep.theta)/(ep.lowBeta+ep.highBeta);
                    tv_ratio_val.setText(df.format(ratio));

                    ++secsElapsed;
                    tv_time_val.setText( String.valueOf(secsElapsed));

                    if(!baselineDone)
                        createBaseline(secsElapsed,ratio, signalQuality);
                    else
                        checkCondition(ratio, attention, signalQuality);


                    break;

                default:
                    break;

            }  /*end switch on msg.what*/



        }  /*end handleMessage()*/

    }; /* end Handler */

    void createBaseline(int time, double ratio_, int signal)
    {
        if(signal == 0)
        {
            changeFont(tv_status_val,1);
            tv_status_val.setText("Creating Baseline");
            if(baselineElements < 60)
            {
                //meanAttBaseline += attention_;
                meanRatioBaseline += ratio_;
                baselineElements++;
            }
            else
            {
                DecimalFormat df = new DecimalFormat("####0.00");
                //meanAttBaseline = meanAttBaseline / baselineElements;
                meanRatioBaseline = meanRatioBaseline / baselineElements;
                baselineDone = true;

                tv.setText("Ratio: "+df.format(meanRatioBaseline));
            }
        }

    }

    void checkCondition(double ratio_, int attention_, int signal)
    {
        changeFont(tv_status_val,2);
        tv_status_val.setText("Awake");
        double increment_ratio = 0.0;
        increment_ratio = ratio_ / meanRatioBaseline -1.0;
        if(signal == 0)
        {
            if(increment_ratio > thresholdRatio)
                ratioCnt++;
            else
                if(ratioCnt>0)
                    ratioCnt--;

            if(attention_ < thresholdAttention )
                attCnt++;
            else
                if(attCnt>0)
                    attCnt--;

            tv_counter_val.setText(String.valueOf(ratioCnt+attCnt));

            if(ratioCnt+attCnt >= thresholdCounter) { //set trigger value
                changeFont(tv_status_val,2);
                tv_status_val.setText("Drowsy");
                SoundAlarm();
                ratioCnt = 0;
                attCnt = 0;
            }
        }
    }

    void changeFont(TextView textV, int type) //type 1: small font ; type 2: big font
    {
        switch (type)
        {
            case 1:
                textV.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                break;
            case 2:
                textV.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                break;
            default:
                break;
        }
    }


    public void connectDevice(View view)
    {
        Button b = (Button) findViewById(R.id.btnConnect);
        if( device.getState() != TGDevice.STATE_CONNECTING && device.getState() != TGDevice.STATE_CONNECTED ) {

            device.connect( rawEnabled );

            b.setText("Disconnect");
        }
        else
        if( device.getState() == TGDevice.STATE_CONNECTING || device.getState() == TGDevice.STATE_CONNECTED )
        {
            device.close();
            b.setText("Connect");
        }


    }
}
