package com.medicpro.myassistant;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends android.app.Activity implements TextToSpeech.OnInitListener {
    private DatabaseHelper db;
    private SpeechRecognizer speech;
    private TextToSpeech tts;
    private LinearLayout content;
    private TextView heard;
    private TextView summary;
    private final int AUDIO_REQ=10, NOTIF_REQ=11;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new DatabaseHelper(this);
        tts = new TextToSpeech(this, this);
        buildUi();
        askPermissions();
        refresh();
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(20,32,60)); v.setPadding(8,8,8,8);
        if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v;
    }
    private Button button(String s) { Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(16); return b; }

    private void buildUi() {
        ScrollView sc=new ScrollView(this); sc.setBackgroundColor(Color.rgb(245,247,251));
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(28,34,28,50); sc.addView(content);
        TextView title=text("My Assistant",28,true); content.addView(title);
        TextView sub=text("बोलिए, मैं आपका हिसाब और काम याद रखूँगा।",15,false); sub.setTextColor(Color.DKGRAY); content.addView(sub);

        summary=text("",18,true); summary.setPadding(20,25,20,25); summary.setBackgroundColor(Color.WHITE); content.addView(summary,new LinearLayout.LayoutParams(-1,-2));
        heard=text("आपने क्या कहा, यहाँ दिखेगा।",15,false); heard.setGravity(Gravity.CENTER); heard.setPadding(10,18,10,8); content.addView(heard);

        Button mic=button("🎤  बोलिए"); mic.setTextSize(22); mic.setTextColor(Color.WHITE); mic.setBackgroundColor(Color.rgb(31,94,255));
        mic.setOnClickListener(v->startListening()); LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,150); mp.setMargins(0,12,0,12); content.addView(mic,mp);

        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        Button type=button("✍️ लिखकर"); Button business=button("🏪 Business"); Button tasks=button("✅ काम");
        type.setOnClickListener(v->manualInput()); business.setOnClickListener(v->businessDialog()); tasks.setOnClickListener(v->showTasks());
        row.addView(type,new LinearLayout.LayoutParams(0,-2,1)); row.addView(business,new LinearLayout.LayoutParams(0,-2,1)); row.addView(tasks,new LinearLayout.LayoutParams(0,-2,1)); content.addView(row);
        content.addView(text("हाल की एंट्री",20,true));
        setContentView(sc);
    }

    private void askPermissions() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_REQ);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIF_REQ);
    }

    private void startListening() {
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { askPermissions(); return; }
        if(!SpeechRecognizer.isRecognitionAvailable(this)) { Toast.makeText(this,"Voice recognition उपलब्ध नहीं है। लिखकर एंट्री करें।",Toast.LENGTH_LONG).show(); return; }
        if(speech!=null) speech.destroy(); speech=SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle p){ heard.setText("सुन रहा हूँ…"); }
            public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){heard.setText("समझ रहा हूँ…");}
            public void onError(int e){heard.setText("आवाज़ समझ नहीं आई। फिर बोलें या लिखकर डालें।");}
            public void onResults(Bundle r){ ArrayList<String> a=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(a!=null&&!a.isEmpty()) handle(a.get(0)); }
            public void onPartialResults(Bundle p){} public void onEvent(int e, Bundle p){}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hi-IN"); i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false); speech.startListening(i);
    }

    private void manualInput() {
        EditText e=new EditText(this); e.setHint("जैसे: रमेश को 2500 रुपये cash दिए"); e.setPadding(30,15,30,15);
        new AlertDialog.Builder(this).setTitle("लिखकर बताइए").setView(e).setPositiveButton("समझो",(d,w)->handle(e.getText().toString())).setNegativeButton("रद्द",null).show();
    }

    private void handle(String raw) {
        heard.setText("“"+raw+"”"); VoiceParser.ParsedCommand p=VoiceParser.parse(raw,db.getBusinesses());
        if("General".equals(p.business)) p.business=db.getDefaultBusiness();
        if(p.intent==VoiceParser.Intent.TRANSACTION) confirmTransaction(p);
        else if(p.intent==VoiceParser.Intent.TASK) confirmTask(p);
        else if(p.intent==VoiceParser.Intent.QUERY) answerQuery(p);
        else { speak("मैं इसे समझ नहीं पाया। आप रकम और काम थोड़ा साफ बोलें।"); Toast.makeText(this,"उदाहरण: रमेश को 2500 रुपये cash दिए",Toast.LENGTH_LONG).show(); }
    }

    private String directionLabel(String d){ if("IN".equals(d))return"मिला"; if("OUT".equals(d))return"दिया"; if("DUE_IN".equals(d))return"लेना है"; return"देना है"; }
    private void confirmTransaction(VoiceParser.ParsedCommand p) {
        String msg="₹"+DatabaseHelper.money(p.amount)+"\n"+directionLabel(p.direction)+(p.person.isEmpty()?"":" • "+p.person)+"\n"+p.mode+" • "+p.business;
        new AlertDialog.Builder(this).setTitle("क्या इसे सेव करूँ?").setMessage(msg).setPositiveButton("✅ सही, सेव करें",(d,w)->{
            db.addTransaction(p.direction,p.amount,p.person,p.mode,p.business,p.raw); refresh(); speak("ठीक है, एंट्री सेव कर दी।");
        }).setNegativeButton("❌ नहीं",null).show();
    }

    private void confirmTask(VoiceParser.ParsedCommand p) {
        if(p.dueAt==null) p.dueAt=LocalDateTime.now().plusDays(1).withHour(9).withMinute(0);
        String when=p.dueAt.format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a"));
        LocalDateTime finalDue=p.dueAt;
        new AlertDialog.Builder(this).setTitle("काम सेव करें?").setMessage(p.taskTitle+"\n"+when).setPositiveButton("✅ सेव करें",(d,w)->{
            long ms=finalDue.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); long id=db.addTask(p.taskTitle,ms); scheduleReminder(id,p.taskTitle,ms); refresh(); speak("काम याद रख लिया।");
        }).setNegativeButton("❌ नहीं",null).show();
    }

    private void answerQuery(VoiceParser.ParsedCommand p) {
        String ans;
        switch(p.queryType) {
            case "TODAY_TASKS": ans=db.todayTasksText(); break;
            case "PERSON": ans=db.personSummary(p.person); break;
            case "DUE_IN": ans="कुल लेना है ₹"+DatabaseHelper.money(db.sumAll("DUE_IN")); break;
            case "DUE_OUT": ans="कुल देना है ₹"+DatabaseHelper.money(db.sumAll("DUE_OUT")); break;
            case "TODAY_OUT": ans="आज आपने ₹"+DatabaseHelper.money(db.sumToday("OUT"))+" दिए या खर्च किए हैं।"; break;
            default: ans="आज ₹"+DatabaseHelper.money(db.sumToday("IN"))+" आए और ₹"+DatabaseHelper.money(db.sumToday("OUT"))+" गए।";
        }
        new AlertDialog.Builder(this).setTitle("My Assistant").setMessage(ans).setPositiveButton("ठीक है",null).show(); speak(ans);
    }

    private void scheduleReminder(long id, String title, long when) {
        if(when<=System.currentTimeMillis()) return;
        Intent i=new Intent(this,ReminderReceiver.class); i.putExtra("title",title);
        PendingIntent pi=PendingIntent.getBroadcast(this,(int)(id%Integer.MAX_VALUE),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE); am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
    }

    private void businessDialog() {
        String[] names=db.getBusinesses();
        new AlertDialog.Builder(this).setTitle("Default Business चुनें").setSingleChoiceItems(names,-1,(d,which)->{ db.setDefaultBusiness(names[which]); d.dismiss(); refresh(); })
            .setPositiveButton("+ नया Business",(d,w)->addBusinessDialog()).setNegativeButton("बंद",null).show();
    }
    private void addBusinessDialog() {
        EditText e=new EditText(this); e.setHint("Business का नाम");
        new AlertDialog.Builder(this).setTitle("नया Business").setView(e).setPositiveButton("जोड़ें",(d,w)->{ if(db.addBusiness(e.getText().toString())) db.setDefaultBusiness(e.getText().toString().trim()); refresh(); }).setNegativeButton("रद्द",null).show();
    }

    private void showTasks() {
        java.util.List<DatabaseHelper.TaskRow> rows=db.upcomingTasks();
        if(rows.isEmpty()){new AlertDialog.Builder(this).setTitle("काम").setMessage("कोई pending काम नहीं है।").setPositiveButton("ठीक है",null).show();return;}
        String[] labels=new String[rows.size()]; DateTimeFormatter f=DateTimeFormatter.ofPattern("dd MMM, hh:mm a");
        for(int i=0;i<rows.size();i++){ LocalDateTime dt=LocalDateTime.ofInstant(Instant.ofEpochMilli(rows.get(i).dueAt),ZoneId.systemDefault()); labels[i]=rows.get(i).title+"\n"+dt.format(f); }
        new AlertDialog.Builder(this).setTitle("Pending काम — पूरा करने के लिए tap करें").setItems(labels,(d,which)->{db.markTaskDone(rows.get(which).id);refresh();speak("काम पूरा कर दिया।");}).setNegativeButton("बंद",null).show();
    }

    private void refresh() {
        summary.setText("आज आया  ₹"+DatabaseHelper.money(db.sumToday("IN"))+"\nआज गया   ₹"+DatabaseHelper.money(db.sumToday("OUT"))+"\nलेना है   ₹"+DatabaseHelper.money(db.sumAll("DUE_IN"))+"     देना है   ₹"+DatabaseHelper.money(db.sumAll("DUE_OUT"))+"\nBusiness: "+db.getDefaultBusiness());
        while(content.getChildCount()>7) content.removeViewAt(7);
        java.util.List<String> lines=db.recentLines();
        if(lines.isEmpty()) content.addView(text("अभी कोई एंट्री नहीं है। 🎤 बोलकर पहली एंट्री करें।",15,false));
        else for(String line:lines){TextView v=text(line,15,false);v.setBackgroundColor(Color.WHITE);v.setPadding(18,18,18,18);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,5,0,5);content.addView(v,lp);}
    }

    private void speak(String s) { if(tts!=null) tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"myassistant"); }
    @Override public void onInit(int status) { if(status==TextToSpeech.SUCCESS){ int r=tts.setLanguage(new Locale("hi","IN")); if(r<0) tts.setLanguage(Locale.ENGLISH); } }
    @Override protected void onDestroy(){ super.onDestroy(); if(speech!=null)speech.destroy(); if(tts!=null){tts.stop();tts.shutdown();} }
}
