package com.example.yelimhan.bomtogether;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class AnswerActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    private QuestionInfo questionInfo;
    private ImageView imageView;
    private TextView questionText;
    private EditText editText;
    private StorageReference storageReference = FirebaseStorage.getInstance("gs://bomtogether-5f74b.appspot.com").getReference();
    private MediaPlayer mediaPlayer = new MediaPlayer();
    private boolean isPlaying = false;
    private boolean isPrepared = false;
    private UserInfo userInfo;
    private SeekBar seekBar;
    String userIndexId ="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_answer);
        Intent intent = getIntent();
        questionInfo = (QuestionInfo) intent.getSerializableExtra("question");
        userIndexId = intent.getStringExtra("USERINDEX");
        imageView = findViewById(R.id.answerimage);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AnswerActivity.this, ImageManager.class);
                intent.putExtra("question", questionInfo);
                startActivity(intent);
            }
        });
        GlideApp.with(this)
                .load(storageReference.child(questionInfo.q_pic))
                .into(imageView);


        // 현재 접속중인 사용자의 정보를 받아옴. 없으면 null
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String userId = user.getEmail();
        // firebase database 참조 객체
        DatabaseReference table = FirebaseDatabase.getInstance().getReference("UserInfo");
        if(user != null) {
            Query query = table.orderByChild("u_googleId").equalTo(userId);
            query.addListenerForSingleValueEvent(new ValueEventListener() {       // 역할 알아오기
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        userInfo = snapshot.getValue(UserInfo.class);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                }
            });
        }

        setMediaPlayer();

        // STT 일단 뺌(view도)
        //questionText.setText("음성녹음 STT한 질문글");

        // 확인버튼 누르면 답변 디비에 저장
        editText = findViewById(R.id.answertext);
        findViewById(R.id.checkbutton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!editText.getText().toString().equals("")) {
                    mDatabase = FirebaseDatabase.getInstance().getReference("AnswerInfo");
                    String newAnswer = mDatabase.push().getKey();
                    AnswerInfo answerInfo = new AnswerInfo(newAnswer, questionInfo.q_id, userInfo.u_googleId, editText.getText().toString());
                    mDatabase.child(newAnswer).setValue(answerInfo);
                    mDatabase = FirebaseDatabase.getInstance().getReference("QuestionInfo");
                    mDatabase.child(questionInfo.q_id).child("checkAnswer").setValue(true);
                    DatabaseReference userRef;
                    userRef = FirebaseDatabase.getInstance().getReference("UserInfo");
                    userRef.child(userIndexId).child("urgent_qid").setValue("");
                    // startActivity(new Intent(AnswerActivity.this,QuestionListActivity.class));
                    // 질문자에게 푸쉬 알람 보내는 함수
                    sendPushAlert(questionInfo.q_id);
                    Toast.makeText(getApplicationContext(), "답변이 등록되었습니다.", Toast.LENGTH_SHORT).show();
                    AnswerActivity.this.finish();
                }
            }
        });
    }

    // 재생 관련 함수
    public void setMediaPlayer() {
        final Button playbutton = findViewById(R.id.playbutton);
        seekBar = findViewById(R.id.seekbar);

        // 음성 파일 재생
        storageReference.child(questionInfo.q_voice).getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                try {
                    mediaPlayer.setDataSource(getApplicationContext(), uri);
                    mediaPlayer.prepare();
                    Log.d("file path : ", questionInfo.q_voice);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                seekBar.setMax(mediaPlayer.getDuration());
                seekBar.setProgress(0);
                isPrepared = true;
                findViewById(R.id.recordlinear).setVisibility(View.VISIBLE);
            }
        });
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if(isPrepared) {
                    isPlaying = false;
                    mediaPlayer.seekTo(0);
                    seekBar.setProgress(0);
                    playbutton.setSelected(false);
                }
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(isPrepared && isPlaying) {
                    isPlaying = false;
                    mediaPlayer.pause();
                    playbutton.setSelected(false);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(isPrepared) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                    if (seekBar.getProgress() == seekBar.getMax()) {
                        isPlaying = false;
                        playbutton.setSelected(false);
                    }
                }
            }
        });
        playbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isPrepared) {
                    if (isPlaying) {
                        isPlaying = false;
                        mediaPlayer.pause();
                        v.setSelected(false);
                    } else {
                        isPlaying = true;
                        new PlayRecord().start();
                        mediaPlayer.start();
                        v.setSelected(true);
                    }
                }
            }
        });
    }

    // 질문자에게 푸쉬 알람 보내는 함수
    public void sendPushAlert(String answerkey) {
        final String FCM_MESSAGE_URL = "https://fcm.googleapis.com/fcm/send";
        final String SERVER_KEY = "AAAAR0VMe3w:APA91bEYVBBHdmhozJLMAsH4ZPxvPvRuUSyPbN9sKh64v7ZktJ2jhc-HCtF12-0Ig-vBK73EUMFaMW93QEehc0V8yDvY-wGalhJJLpDw6X53taufe24R9QmSRJa8UYOCxAWwhhdv-FBA";
        FirebaseDatabase.getInstance().getReference("QuestionInfo").child(answerkey)
                .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                    QuestionInfo questionInfo = dataSnapshot.getValue(QuestionInfo.class);
                    String writer = questionInfo.q_writer;
                    Log.d("testt", "writer : " +    writer);
                    FirebaseDatabase.getInstance().getReference("UserInfo")
                            .orderByChild("u_googleId").equalTo(writer)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                        String userkey = snapshot.getKey();
                                        Log.d("testt", "userkey : "+userkey);
                                        FirebaseDatabase.getInstance().getReference("UserInfo").child(userkey).child("u_haveQuestion").setValue(2);
                                        UserInfo userInfo = snapshot.getValue(UserInfo.class);

                                        final String usertoken = userInfo.u_token;
                                        Log.d("testt", "usertoken : "+userInfo.u_token);

                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    // FMC 메시지 생성 start
                                                    JSONObject root = new JSONObject();
                                                    JSONObject notification = new JSONObject();
                                                    notification.put("body", "답변이 도착했어요. 눌러서 답변을 확인하세요!");
                                                    notification.put("title", getString(R.string.app_name));
                                                    root.put("data", notification);
                                                    root.put("to", usertoken);
                                                    // FMC 메시지 생성 end

                                                    URL Url = new URL(FCM_MESSAGE_URL);
                                                    HttpURLConnection conn = (HttpURLConnection) Url.openConnection();
                                                    conn.setRequestMethod("POST");
                                                    conn.setDoOutput(true);
                                                    conn.setDoInput(true);
                                                    conn.addRequestProperty("Authorization", "key=" + SERVER_KEY);
                                                    conn.setRequestProperty("Accept", "application/json");
                                                    conn.setRequestProperty("Content-type", "application/json");
                                                    OutputStream os = conn.getOutputStream();
                                                    os.write(root.toString().getBytes("utf-8"));
                                                    os.flush();
                                                    conn.getResponseCode();
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }).start();
                                    }
                                }@Override public void onCancelled(@NonNull DatabaseError databaseError) {}});
            }@Override public void onCancelled(@NonNull DatabaseError databaseError) {}});
    }

    public void stt() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        SpeechRecognizer mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {

            }

            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onRmsChanged(float rmsdB) {

            }

            @Override
            public void onBufferReceived(byte[] buffer) {

            }

            @Override
            public void onEndOfSpeech() {

            }

            @Override
            public void onError(int error) {

            }

            @Override
            public void onResults(Bundle results) {
                String stt = SpeechRecognizer.RESULTS_RECOGNITION;
                ArrayList<String> mResult = results.getStringArrayList(stt);
                String[] rs = new String[mResult.size()];
                mResult.toArray(rs);
                questionText.setText(""+rs[0]);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {

            }

            @Override
            public void onEvent(int eventType, Bundle params) {

            }
        });
        mRecognizer.startListening(i);
    }

    @Override
    public void onBackPressed() {
        imageView.setOnClickListener(null);
        isPrepared = false;
        mediaPlayer.stop();
        isPlaying = false;
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        imageView.setOnClickListener(null);
        super.onDestroy();
    }

    public class PlayRecord extends Thread {
        @Override
        public void run() {
            try {   // 시작할 때 버퍼링 임시 해결
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while(isPlaying&&isPrepared) {
                seekBar.setProgress(mediaPlayer.getCurrentPosition());
            }

        }
    }

}
