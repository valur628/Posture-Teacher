package com.gnupr.postureteacher;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.view.View;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.SurfaceTexture;

import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.gnupr.postureteacher.Databases.EntityClass.MeasureDatasEntity;
import com.gnupr.postureteacher.Databases.EntityClass.MeasureRoundsEntity;
import com.gnupr.postureteacher.Databases.MeasureRoomDatabase;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MeasureActivity extends AppCompatActivity {
    private static final String TAG = "MeasureActivity";
    private static final String BINARY_GRAPH_NAME = "pose_tracking_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "pose_landmarks";
    private static final int NUM_FACES = 1;
    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    private static final boolean FLIP_FRAMES_VERTICALLY = true;

    static {
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    private SurfaceTexture previewFrameTexture;
    private SurfaceView previewDisplayView;
    private EglManager eglManager;
    private FrameProcessor processor;
    private ExternalTextureConverter converter;
    private ApplicationInfo applicationInfo;
    private CameraXPreviewHelper cameraHelper;


    Handler ui_Handler = null;
    //UI ????????? ??? ?????????
    boolean ui_HandlerCheck = true;
    //UI ????????? ?????????
    private boolean startThreadCheck = true;

    private boolean startDialogCheck = true;
    //????????? ??????????????? ?????? ??????


    private int timer_hour, timer_minute, timer_second;
    //????????? ??????
    private String text_hour, text_minute, text_second;
    //????????? ?????? ??????
    private String nowTime;
    //?????? ??????
    private int totalTime = 0;
    //?????? ??????
    private int globalTime = 0;
    //?????? ????????? ??????
    private int spareTime = 100;
    //?????? ?????? ??????
    private int spareTimeMinus = 1;
    //?????? ?????? ?????? ???
    private boolean spareTimeCheck = false;
    //?????? ?????? ???????????? ?????????
    private int tempTime = 0;
    //?????? ????????? ?????? ??????(????????? ?????? ??????)

    LocalDateTime timeMeasureDataStart = LocalDateTime.now();
    LocalDateTime timeMeasureDataEnd = LocalDateTime.now();
    //?????? ?????? ?????? ??????
    private boolean timeDataCheck = true;
    //?????? ?????? ???????????? ?????????

    LocalDateTime timeMeasureRoundStart = LocalDateTime.now();
    LocalDateTime timeMeasureRoundEnd = LocalDateTime.now();
    //?????? ?????? ??????
    private boolean timeRoundCheck = true;
    //?????? ?????? ???????????? ?????????


    private String[] divideTime;
    //??????????????? ????????? ??????
    private Timer timer = new Timer();
    private boolean pauseTimerCheck = false;
    //false = ?????????, true = ??????

    LocalDate nowLocalDate = LocalDate.now();
    LocalTime nowLocalTime = LocalTime.now();
    String formatedNowLocalTime = nowLocalDate.format(DateTimeFormatter.ofPattern("yyMMdd")) + nowLocalTime.format(DateTimeFormatter.ofPattern("HHmmss"));
    //??????, ?????? & ???????????? ?????? ??????+?????? ??????
    
    LocalDateTime measureRoundStart = LocalDateTime.now();
    LocalDateTime measureRoundEnd = LocalDateTime.now();
    //?????? ?????? ??????

    LocalDateTime measureDataStart = LocalDateTime.now();
    LocalDateTime measureDataEnd = LocalDateTime.now();
    //?????? ?????? ??????

    private String UseTimerTimeDB = "01:22:33";
    //????????? ????????? ?????? (??????:???:???)
    private final long finishtimeed = 2500;
    private long presstime = 0;

    private int finalStopCheck = 0;
    //?????? ?????? ?????? ??????
    //0 ???????????? ??????, 1 ?????? ??????, 2 ??????

    //private TextView tv2;
    //private TextView tv6;
    private TextView tv_TimeCounter;

    private ImageView iv1;
    private ImageView iv2;
    //private ImageView iv3;
    private ImageView iv4;
    private ImageView iv5;
    //private ImageView iv6;

    class markPoint {
        float x;
        float y;
        float z;
    }

    private NormalizedLandmark[] bodyAdvancePoint = new NormalizedLandmark[33];
    //?????? ???????????? ????????? ??????
    private markPoint[] bodyMarkPoint = new markPoint[35];
    //??? ???????????? ????????? ??????
    private float[] bodyRatioMeasurement = new float[33];
    //?????? ????????? ??????(????????? ???)
    private boolean[][][] markResult = new boolean[33][33][33];
    //?????? ?????? true/false ??????
    private boolean[] sideTotalResult = new boolean[2];
    //0=??????, 1=?????????
    private boolean[] OutOfRangeSave = new boolean[33];
    //?????? ????????? ?????? ?????? ??????
    private float[][] resultAngleSave = new float[2][6];
    //?????? ????????? ????????? 0.5??? ????????? ?????? ??????
    private int[] resultPosture = new int[4];
    //?????? ??? ?????? ?????? 0=?????????, 1=??????, 2=??????


    private float ratioPoint_1a, ratioPoint_1b, ratioPoint_2a, ratioPoint_2b;
    //?????? ????????? ?????? ????????? ?????? (??????, ?????????)


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getContentViewLayoutResId());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //tv2 = findViewById(R.id.tv2);
        //tv6 = findViewById(R.id.tv6);
        getTimeIntent();
        iv1= findViewById(R.id.imageView3);
        iv2= findViewById(R.id.imageView4);
        //iv3= findViewById(R.id.imageView5);
        iv4= findViewById(R.id.imageView6);
        iv5= findViewById(R.id.imageView7);
        //iv6= findViewById(R.id.imageView8);

        //tv.setText("000");
        if (startDialogCheck) {
            startDialog();
            startDialogCheck = false;
        }
        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        //tv.setText("111");
        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();
        //tv.setText("222");

        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        //tv.setText("333");
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);

        //tv.setText("444");
        PermissionHelper.checkAndRequestCameraPermissions(this);
        //tv.setText("555");
        AndroidPacketCreator packetCreator = processor.getPacketCreator();
        //tv.setText("666");
        Map<String, Packet> inputSidePackets = new HashMap<>();
        //tv.setText("888");
        processor.setInputSidePackets(inputSidePackets);
        //tv.setText("999");

        ui_Handler = new Handler();
        ThreadClass callThread = new ThreadClass();

        if (Log.isLoggable(TAG, Log.WARN)) {
            processor.addPacketCallback(
                    OUTPUT_LANDMARKS_STREAM_NAME,
                    (packet) -> {
                        byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                        try {
                            NormalizedLandmarkList poseLandmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                            //tv6.setText("a");
                            ratioPoint_1a = poseLandmarks.getLandmark(11).getY() * 1000f;
                            ratioPoint_1b = poseLandmarks.getLandmark(13).getY() * 1000f;
                            ratioPoint_2a = poseLandmarks.getLandmark(12).getY() * 1000f;
                            ratioPoint_2b = poseLandmarks.getLandmark(14).getY() * 1000f;
                            //tv6.setText("b");
                            for (int i = 0; i <= 32; i++) {
                                bodyMarkPoint[i] = new markPoint();
                                //tv6.setText("c");
                                bodyAdvancePoint[i] = poseLandmarks.getLandmark(i);
                                //tv6.setText("d");
                                bodyMarkPoint[i].x = bodyAdvancePoint[i].getX() * 1000f;
                                //tv6.setText("e");
                                bodyMarkPoint[i].y = bodyAdvancePoint[i].getY() * 1000f;
                                //tv6.setText("f");
                                bodyMarkPoint[i].z = bodyAdvancePoint[i].getZ() * 1000f;
                                //tv6.setText("g");
                                bodyRatioMeasurement[i] = bodyMarkPoint[i].x / (ratioPoint_1b - ratioPoint_1a);
                                //tv6.setText("h");
                                bodyRatioMeasurement[i] = bodyMarkPoint[i].y / (ratioPoint_1b - ratioPoint_1a);
                                //tv6.setText("i");
                                bodyRatioMeasurement[i] = bodyMarkPoint[i].z / (ratioPoint_1b - ratioPoint_1a);
                                //tv6.setText("k");
                                if ((-100f <= bodyMarkPoint[i].x && bodyMarkPoint[i].x <= 1100f) && (-100f <= bodyMarkPoint[i].y && bodyMarkPoint[i].y <= 1100f))
                                    OutOfRangeSave[i] = true;
                                else
                                    OutOfRangeSave[i] = false;
                            }
                            //tv.setText("X:" + bodyMarkPoint[25].x + " / Y:" + bodyMarkPoint[25].y + " / Z:" + bodyMarkPoint[25].z + "\n/ANGLE:" + getLandmarksAngleTwo(bodyMarkPoint[23], bodyMarkPoint[25], bodyMarkPoint[27], 'x', 'y'));

                            if (startThreadCheck) {
                                ui_Handler.post(callThread);
                                // ???????????? ?????? ??????????????? OS?????? ????????? ??????
                                startThreadCheck = false;
                            }
                        } catch (InvalidProtocolBufferException e) {
                            Log.e(TAG, "Couldn't Exception received - " + e);
                            return;
                        }
                    }
            );
        }
    }

    class ThreadClass extends Thread {
        //?????? UI ????????? ?????????
        @Override
        public void run() {
            //????????????
            if (sideTotalResult[1] && sideTotalResult[0]) {
                //tv6.setText("1");
                //tv2.setText("??? ?????? ???????????????.");
            } else if (sideTotalResult[1]) {
                //tv6.setText("2");
                //tv2.setText("????????? ?????? ???????????????.");
            } else if (sideTotalResult[0]) {
                //tv6.setText("3");
                //tv2.setText("?????? ?????? ???????????????.");
            } else {
                //tv6.setText("4");
                //tv2.setText("??? ?????? ??????????????????.");
            }

            if (bodyMarkPoint[11].z > bodyMarkPoint[12].z)
                getLandmarksAngleResult(0);
                //??????
            else
                getLandmarksAngleResult(1);
                //?????????


            if(20 <= globalTime) {
                if (!pauseTimerCheck) {
                    if (getResultPosture(resultPosture) == 2) {
                        if (spareTime >= 90) {
                            if (spareTimeCheck && finalStopCheck == 0) {
                                if (tempTime >= 6) {
                                    Toast.makeText(getApplicationContext(), "????????? ???????????????.", Toast.LENGTH_SHORT).show();
                                    saveMeasureDatas();
                                    spareTimeCheck = false;
                                } else if (tempTime < 6)
                                    tempTime++;
                            }
                        }
                        spareTime = 100;
                    } else if (getResultPosture(resultPosture) == 1) {
                        if (spareTime <= 0) {
                            if (!spareTimeCheck) {
                                Toast.makeText(getApplicationContext(), "????????? ???????????? ???????????????.", Toast.LENGTH_SHORT).show();
                                measureDataStart = LocalDateTime.now();
                                spareTimeCheck = true;
                            }
                        }
                        if (spareTime > 0) {
                            spareTime -= spareTimeMinus;
                        }
                        if (tempTime > 0) {
                            tempTime = 0;
                        }
                    }
                }
            }

            if(finalStopCheck == 0) {
                tv_TimeCounter.setText(nowTime);
            }
            else if(finalStopCheck == 1 || finalStopCheck == 2) {
                tv_TimeCounter.setText(timer_second + "??? ??? ????????????");
            }

            if(finalStopCheck == 1){
                if(20 <= globalTime) {
                    saveMeasureRounds();
                    if (spareTimeCheck) {
                        saveMeasureDatas();
                    }
                }
            }

            if(finalStopCheck == 2 && timer_second <= 0) {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
                pauseTimerCheck = true;
                ui_HandlerCheck = false;
                finish();
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
                startThreadCheck = true;
            }
            if(ui_HandlerCheck) {
                ui_Handler.post(this);
            }
        }
    }



    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            // ??????????????? ??????
            globalTime++;
            if(!pauseTimerCheck) {
                // 0??? ????????????
                if (timer_second != 0) {
                    //1?????? ??????
                    timer_second--;

                    // 0??? ????????????
                } else if (timer_minute != 0) {
                    // 1??? = 60???
                    timer_second = 60;
                    timer_second--;
                    timer_minute--;

                    // 0?????? ????????????
                } else if (timer_hour != 0) {
                    // 1?????? = 60???
                    timer_second = 60;
                    timer_minute = 60;
                    timer_second--;
                    timer_minute--;
                    timer_hour--;
                }

                if(globalTime == 20) {
                    measureRoundStart = LocalDateTime.now();
                }

                //???, ???, ?????? 10??????(????????????) ??????
                // ?????? ?????? 0??? ????????? ( 8 -> 08 )
                if (timer_second <= 9) {
                    text_second = "0" + timer_second;
                } else {
                    text_second = Integer.toString(timer_second);
                }

                if (timer_minute <= 9) {
                    text_minute = "0" + timer_minute;
                } else {
                    text_minute = Integer.toString(timer_minute);
                }

                if (timer_hour <= 9) {
                    text_hour = "0" + timer_hour;
                } else {
                    text_hour = Integer.toString(timer_minute);
                }
                nowTime = text_hour + ":" + text_minute + ":" + text_second + " (" + spareTime + "% / " + tempTime + "pt)";
            }

            if (timer_hour == 0 && timer_minute == 0 && timer_second == 0) {
                /*timerTask.cancel();//????????? ??????
                timer.cancel();//????????? ??????
                timer.purge();//????????? ??????*/
                //????????? ?????? ????????? ??? ???????????? ????????? ??? ????????? ???????????? ???????????? ?????? ???????????? ?????? ????????? ??? ????????? ?????? ??????
                if(finalStopCheck == 0) {
                    timer_second += 3;
                    finalStopCheck = 1;
                }
            }
        }
    };
    private void startDialog() {
        //?????? ???????????? ???????????? ??????
        tv_TimeCounter = findViewById(R.id.TimeCounter);
        AlertDialog.Builder msgBuilder = new AlertDialog.Builder(MeasureActivity.this)
                .setTitle("?????? ??? ??????")
                .setMessage("????????? ?????? ????????? ????????? ?????? ????????? 30??? ?????? ?????? ????????? ??????????????? ???????????????. ??? ????????? ????????? ???????????? ???????????? ????????????. ??? ?????? ???????????? ????????? ????????? ?????????????????? ????????????.")
                .setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                        divideTime = UseTimerTimeDB.split(":");
                        timer_hour = Integer.parseInt(divideTime[0]);
                        timer_minute = Integer.parseInt(divideTime[1]);
                        timer_second = Integer.parseInt(divideTime[2]);
                        totalTime = ((((timer_hour * 60) + timer_minute) * 60) + timer_second) * 1000;
                        timer.scheduleAtFixedRate(timerTask, 10000, 1000); //Timer ??????
                    }
                });
        AlertDialog msgDlg = msgBuilder.create();
        msgDlg.show();
    }

    public void onClickExit(View view) {
            if(20 <= globalTime) {
                if (finalStopCheck == 0) {
                    saveMeasureRounds();
                    if (spareTimeCheck) {
                        saveMeasureDatas();
                    }
                }
            }
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            pauseTimerCheck = true;
            ui_HandlerCheck = false;
            finish();
    }

    private void saveMeasureRounds() { //????????? ?????? ?????? ??????, ??????
        LocalDateTime MeasureRoundStartTime_num = measureRoundStart;
        measureRoundEnd = LocalDateTime.now();
        LocalDateTime MeasureRoundEndTime_num = measureRoundEnd;

        MeasureRoundsEntity MeasureRoundsTable = new MeasureRoundsEntity();
        MeasureRoundsTable.setMeasureRoundStartTime(MeasureRoundStartTime_num);
        MeasureRoundsTable.setMeasureRoundEndTime(MeasureRoundEndTime_num);
        MeasureRoomDatabase.getDatabase(getApplicationContext()).getMeasureRoundsDao().insert(MeasureRoundsTable);
        //MeasureRoomDatabase.getDatabase(getApplicationContext()).getMeasureRoundsDao().deleteAll(); ?????? ??????

        Toast.makeText(this, "?????? ?????? ??????", Toast.LENGTH_SHORT).show();
        finalStopCheck = 2;
    }


    private void saveMeasureDatas() { //????????? ?????? ?????? ?????? ??????, ??????
        LocalDateTime MeasureDataStartTime_num = measureDataStart;
        measureDataEnd = LocalDateTime.now();
        LocalDateTime MeasureDataEndTime_num = measureDataEnd;
        LocalDateTime MeasureRoundStartTimeFK_num = measureRoundStart;

        MeasureDatasEntity MeasureDatasTable = new MeasureDatasEntity();
        MeasureDatasTable.setMeasureDataStartTime(MeasureDataStartTime_num);
        MeasureDatasTable.setMeasureDataEndTime(MeasureDataEndTime_num);
        MeasureDatasTable.setMeasureRoundStartTimeFK(MeasureRoundStartTimeFK_num);
        MeasureRoomDatabase.getDatabase(getApplicationContext()).getMeasureDatasDao().insert(MeasureDatasTable);
        //MeasureRoomDatabase.getDatabase(getApplicationContext()).getMeasureDatasDao().deleteAll(); ?????? ??????

        Toast.makeText(this, "?????? ?????? ??????", Toast.LENGTH_SHORT).show();
    }

    public void angleCalculationResult(int firstPoint, int secondPoint, int thirdPoint, float oneAngle, float twoAngle) {
        if (getLandmarksAngleTwo(bodyMarkPoint[firstPoint], bodyMarkPoint[secondPoint], bodyMarkPoint[thirdPoint], 'x', 'y') >= oneAngle
                && getLandmarksAngleTwo(bodyMarkPoint[firstPoint], bodyMarkPoint[secondPoint], bodyMarkPoint[thirdPoint], 'x', 'y') <= twoAngle) {
            markResult[firstPoint][secondPoint][thirdPoint] = true;
        } else {
            markResult[firstPoint][secondPoint][thirdPoint] = false;
        }
    }

    public void getLandmarksAngleResult(int side) { //0=??????, 1=?????????
        //????????? true if??? ?????? ?????? ?????? ???, ????????? false if??? ?????? ?????? ?????? ???
        //????????? true if??? ?????? ????????? ????????? ???, ????????? false if??? ?????? ????????? ???????????? ???
        if (OutOfRangeSave[11 + side] == true && OutOfRangeSave[23 + side] == true && OutOfRangeSave[25 + side] == true) { //?????? ??????
            angleCalculationResult(11 + side, 23 + side, 25 + side, 70f, 140f); //90f 120f | 70f 140f | 80f 130f
            //??????-?????????-??????
            if (markResult[11 + side][23 + side][25 + side] == true) { //?????? ??????
                iv1.setImageResource(R.drawable.waist_green);
                resultPosture[0] = 2;
            } else {
                iv1.setImageResource(R.drawable.waist_red);
                resultPosture[0] = 1;
            }
        } else {
            //????????? ?????????(??????)
            iv1.setImageResource(R.drawable.waist_gray);
            markResult[11 + side][23 + side][25 + side] = true;
            resultPosture[0] = 0;
        }

        if (OutOfRangeSave[7 + side] == true && OutOfRangeSave[11 + side] == true && OutOfRangeSave[23 + side] == true) { //?????? ??????
            angleCalculationResult(7 + side, 11 + side, 23 + side, 125f, 175f); //130f 180f | 120f 180f | 140f 180f
            //?????????-??????-???
            if (markResult[7 + side][11 + side][23 + side] == true) { //?????? ??????
                iv2.setImageResource(R.drawable.neck_green);
                resultPosture[1] = 2;
            } else {
                iv2.setImageResource(R.drawable.neck_red);
                resultPosture[1] = 1;
            }
        } else {
            //????????? ?????????(??????)
            iv2.setImageResource(R.drawable.neck_gray);
            markResult[7 + side][11 + side][23 + side] = true;
            resultPosture[1] = 0;
        }
        /*
        if (OutOfRangeSave[11 + side] == true && OutOfRangeSave[13 + side] == true && OutOfRangeSave[15 + side] == true) { //?????? ??????
            angleCalculationResult(11 + side, 13 + side, 15 + side, 80f, 130f); //140f 180f | 120f 180f X //90f 120f
            //?????????-?????????-???
            if (markResult[11 + side][13 + side][15 + side] == true) { //?????? ??????
                iv3.setImageResource(R.drawable.elbow_green);
            } else {
                iv3.setImageResource(R.drawable.elbow_red);
            }
        } else {
            //????????? ?????????(??????)
            iv3.setImageResource(R.drawable.elbow_gray);
            markResult[11 + side][13 + side][15 + side] = true;
        }*/

        bodyMarkPoint[33 + side] = new markPoint();
        if(side == 0)
            bodyMarkPoint[33 + side].x = bodyAdvancePoint[7].getX() * 1000f + 300;
        else
            bodyMarkPoint[33 + side].x = bodyAdvancePoint[7].getX() * 1000f - 300;
        bodyMarkPoint[33 + side].y = bodyAdvancePoint[7].getY() * 1000f - 10;
        bodyMarkPoint[33 + side].z = bodyAdvancePoint[7].getZ() * 1000f + 10;
        if (OutOfRangeSave[7 + side] == true && OutOfRangeSave[11 + side] == true) { //?????? ??????
            if (!Double.isNaN(getLandmarksAngleTwo(bodyMarkPoint[33 + side], bodyMarkPoint[7 + side], bodyMarkPoint[11 + side], 'x', 'y'))) {
                if (getLandmarksAngleTwo(bodyMarkPoint[33 + side], bodyMarkPoint[7 + side], bodyMarkPoint[11 + side], 'x', 'y') >= 80f
                        && getLandmarksAngleTwo(bodyMarkPoint[33 + side], bodyMarkPoint[7 + side], bodyMarkPoint[11 + side], 'x', 'y') <= 130f)
                { //90f 140f | 80f 160f | 80f 120f | 80f 140f
                    markResult[7 + side][7 + side][11 + side] = true;
                } else {
                    markResult[7 + side][7 + side][11 + side] = false;
                }
                if (markResult[7 + side][7 + side][11 + side] == true) { //?????? ??????
                    iv4.setImageResource(R.drawable.ear_green);
                    resultPosture[2] = 2;
                } else {
                    iv4.setImageResource(R.drawable.ear_red);
                    resultPosture[2] = 1;
                }
            }
            //??????-???-?????????(x+300)
        } else {
            //????????? ?????????(??????)
            iv4.setImageResource(R.drawable.ear_gray);
            markResult[7 + side][7 + side][11 + side] = true;
            resultPosture[2] = 0;
        }

        if (OutOfRangeSave[23 + side] == true && OutOfRangeSave[25 + side] == true && OutOfRangeSave[27 + side] == true) { //?????? ??????
            angleCalculationResult(23 + side, 25 + side, 27 + side, 70f, 140f); //90f 120f | 70f 140f
            //?????????-??????-?????? ????????????
            if (markResult[23 + side][25 + side][27 + side] == true) { //?????? ??????
                iv5.setImageResource(R.drawable.knee_green);
                resultPosture[3] = 2;
            } else {
                iv5.setImageResource(R.drawable.knee_red);
                resultPosture[3] = 1;
            }
        } else {
            //????????? ?????????(??????)
            iv5.setImageResource(R.drawable.knee_gray);
            markResult[23 + side][25 + side][27 + side] = true;
            resultPosture[3] = 0;
        }
/*
        if (OutOfRangeSave[25 + side] == true && OutOfRangeSave[29 + side] == true && OutOfRangeSave[31 + side] == true) { //?????? ??????
            angleCalculationResult(25 + side, 29 + side, 31 + side, 90f, 130f); //100f 120f | 80f 140f
            //??????-?????????-??? ????????????
            if (markResult[25 + side][29 + side][31 + side] == true) { //?????? ??????
                iv6.setImageResource(R.drawable.ankle_green);
            } else {
                iv6.setImageResource(R.drawable.ankle_red);
            }
        } else {
            //????????? ?????????(??????)
            iv6.setImageResource(R.drawable.ankle_gray);
            markResult[25 + side][29 + side][31 + side] = true;
        }*/

        if (markResult[11 + side][23 + side][25 + side] && markResult[7 + side][11 + side][23 + side] && markResult[11 + side][13 + side][15 + side]
                && markResult[7 + side][7 + side][11 + side] && markResult[23 + side][25 + side][27 + side] && markResult[25 + side][29 + side][31 + side])
            sideTotalResult[side] = true;
        else
            sideTotalResult[side] = false;
    }

    public static float getLandmarksAngleTwo(markPoint p1, markPoint p2, markPoint p3, char a, char b) {
        float p1_2 = 0f, p2_3 = 0f, p3_1 = 0f;
        if (a == b) {
            return 0;
        } else if ((a == 'x' || b == 'x') && (a == 'y' || b == 'y')) {
            p1_2 = (float) Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
            p2_3 = (float) Math.sqrt(Math.pow(p2.x - p3.x, 2) + Math.pow(p2.y - p3.y, 2));
            p3_1 = (float) Math.sqrt(Math.pow(p3.x - p1.x, 2) + Math.pow(p3.y - p1.y, 2));
        } else if ((a == 'x' || b == 'x') && (a == 'z' || b == 'z')) {
            p1_2 = (float) Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.z - p2.z, 2));
            p2_3 = (float) Math.sqrt(Math.pow(p2.x - p3.x, 2) + Math.pow(p2.z - p3.z, 2));
            p3_1 = (float) Math.sqrt(Math.pow(p3.x - p1.x, 2) + Math.pow(p3.z - p1.z, 2));
        } else if ((a == 'y' || b == 'y') && (a == 'z' || b == 'z')) {
            p1_2 = (float) Math.sqrt(Math.pow(p1.y - p2.y, 2) + Math.pow(p1.z - p2.z, 2));
            p2_3 = (float) Math.sqrt(Math.pow(p2.y - p3.y, 2) + Math.pow(p2.z - p3.z, 2));
            p3_1 = (float) Math.sqrt(Math.pow(p3.y - p1.y, 2) + Math.pow(p3.z - p1.z, 2));
        }
        float radian = (float) Math.acos((p1_2 * p1_2 + p2_3 * p2_3 - p3_1 * p3_1) / (2 * p1_2 * p2_3));
        float degree = (float) (radian / Math.PI * 180);
        return degree;
    }

    public int getResultPosture(int[] rP) {
        int twoCount = 0, oneCount = 0, zeroCount = 0; //??????, ??????, ??????
        for(int i = 0;i<4;i++) {
            if (rP[i] == 2) {
                twoCount++;
            }
            else if (rP[i] == 1) {
                oneCount++;
            }
            else {
                zeroCount++;
            }
        }

        if(zeroCount == 4) {
            spareTimeMinus = 0;
            return 0;
        }
        else if(zeroCount == 3) {
            if(oneCount == 0) {
                spareTimeMinus = 0;
                return 2;
            }
            else {
                spareTimeMinus = 4;
                return 1;
            }
        }
        else if(zeroCount == 2) {
            if(oneCount == 0) {
                spareTimeMinus = 0;
                return 2;
            }
            else if(oneCount == 1) {
                spareTimeMinus = 2;
                return 1;
            }
            else {
                spareTimeMinus = 4;
                return 1;
            }
        }
        else if(zeroCount == 1) {
            if(oneCount == 0) {
                spareTimeMinus = 0;
                return 2;
            }
            else if(oneCount == 1) {
                spareTimeMinus = 1;
                return 1;
            }
            else if(oneCount == 2) {
                spareTimeMinus = 2;
                return 1;
            }
            else {
                spareTimeMinus = 4;
                return 1;
            }
        }
        else {
            if(oneCount == 0) {
                spareTimeMinus = 0;
                return 2;
            }
            else if(oneCount == 1) {
                spareTimeMinus = 1;
                return 1;
            }
            else if(oneCount == 2) {
                spareTimeMinus = 2;
                return 1;
            }
            else if(oneCount == 3) {
                spareTimeMinus = 3;
                return 1;
            }
            else {
                spareTimeMinus = 4;
                return 1;
            }
        }
    }

    @Override
    public void onBackPressed() {
        long tempTimeOBP = System.currentTimeMillis();
        long intervalTime = tempTimeOBP - presstime;

        if (0 <= intervalTime && finishtimeed >= intervalTime)
        {
            if(1 <= globalTime) {
                if(20 <= globalTime) {
                    if (finalStopCheck == 0) {
                        saveMeasureRounds();
                        if (spareTimeCheck) {
                            saveMeasureDatas();
                        }
                    }
                }
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                pauseTimerCheck = true;
                ui_HandlerCheck = false;
                finish();
            }
        }
        else
        {
            presstime = tempTimeOBP;
            Toast.makeText(getApplicationContext(), "??? ??? ??? ????????? ?????? ?????????", Toast.LENGTH_SHORT).show();
        }
    }

    public void getTimeIntent() {
        Intent intent = getIntent();
        int intentHour = intent.getIntExtra("hour", 1);
        int intentMinute = intent.getIntExtra("minute", 0);
        UseTimerTimeDB = intentHour + ":" + intentMinute + ":20";
    }

    //pose
    protected int getContentViewLayoutResId() {
        return R.layout.activity_measure;
    }

    @Override
    protected void onResume() {
        super.onResume();
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        converter.close();

        previewDisplayView.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    protected void onCameraStarted(SurfaceTexture surfaceTexture) {
        previewFrameTexture = surfaceTexture;
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    protected Size cameraTargetResolution() {
        return null;
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
        CameraHelper.CameraFacing cameraFacing = CameraHelper.CameraFacing.FRONT;
        cameraHelper.startCamera(
                this, cameraFacing, previewFrameTexture, cameraTargetResolution());
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    private static String getPoseLandmarksDebugString(NormalizedLandmarkList poseLandmarks) {
        String poseLandmarkStr = "Pose landmarks: " + poseLandmarks.getLandmarkCount() + "\n";
        int landmarkIndex = 0;
        for (NormalizedLandmark landmark : poseLandmarks.getLandmarkList()) {
            poseLandmarkStr +=
                    "\tLandmark ["
                            + landmarkIndex
                            + "]: ("
                            + landmark.getX()
                            + ", "
                            + landmark.getY()
                            + ", "
                            + landmark.getZ()
                            + ")\n";
            ++landmarkIndex;
        }
        return poseLandmarkStr;
    }
}