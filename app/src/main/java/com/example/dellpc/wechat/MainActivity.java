package com.example.dellpc.wechat;

import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";


    //Request code
    //It is a flag which we'd placed uiAuth while we're logging in from diff UI.
    public static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER = 2;

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private int mPosition;

    private String mUsername;

    //entry point of a firebase
    private FirebaseDatabase mFirebaseDatabase;

    //referencing a specific database
    private DatabaseReference mDatabaseReference;

    //authenticating the state change
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotoStorageReference;
    private NotificationManager mNotificationManager;
    private Intent notificationIntent;

    //to read from each child
    private ChildEventListener mChildEventListener;
    FriendlyMessage friendlyMessage = new FriendlyMessage();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_main);

        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(getApplication());


        //main access point of our database
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();


        //giving reference till the child node of the firebase database.
        mDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        //chat_photos is our folder in storage to store photos.
        mChatPhotoStorageReference = mFirebaseStorage.getReference().child("chat_photos");


        mUsername = ANONYMOUS;


        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMessageEditText.setText("");
            }
        });

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        //With these four lines of code in the onClick method,
        // a file picker will be opened to help us choose between any locally stored JPEG images that are on the device.
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
                //friendlyMessage.setPhotoUrl(intent.getData().toString());
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                mDatabaseReference.push().setValue(friendlyMessage);

                // Clear input box
                mMessageEditText.setText("");


            }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

                FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                if (firebaseUser != null) {
                    //user is signed in
                    onSignedInInitialize(firebaseUser.getDisplayName());
                    //Toast.makeText(MainActivity.this, "You're Signed in Welcome to the Friendly Chat App", Toast.LENGTH_SHORT).show();

                } else {
                    //user is signed out

                    onSignedOutCleanup();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    //.setIsSmartLockEnabled(false)
                                    .setProviders(
                                            AuthUI.EMAIL_PROVIDER,
                                            AuthUI.GOOGLE_PROVIDER,
                                            AuthUI.FACEBOOK_PROVIDER
                                    ).build(),
                            RC_SIGN_IN);

                }

            }
        };


//        //working for notification.
//        mDatabaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                FriendlyMessage fm = dataSnapshot.getValue(FriendlyMessage.class);
//                //if(!fm.getName().equals(mUsername)){
//
//
//                }
//           // }
//
//
//            @Override
//            public void onCancelled(DatabaseError databaseError) {
//
//            }
//        });

//        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//
//        android.support.v4.app.NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(MainActivity.this)
//                .setContentTitle("New Message from: "+ friendlyMessage.getName())
//                .setContentText(friendlyMessage.getText())
//                .setOnlyAlertOnce(true)
//                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
//        notificationIntent = getIntent();
//        PendingIntent contentIntent = PendingIntent.getActivity(MainActivity.this,0,notificationIntent,PendingIntent.FLAG_UPDATE_CURRENT);
//        mBuilder.setContentIntent(contentIntent);
//
//        mBuilder.setAutoCancel(true);
//        mBuilder.setLocalOnly(false);
//
//        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//        mNotificationManager.notify(0,mBuilder.build());
        registerForContextMenu(mMessageListView);

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {

                Log.d("Signin", "onActivityResult: " + " OK");
                Toast.makeText(this, "Signed In", Toast.LENGTH_SHORT).show();

            } else if (requestCode == RESULT_CANCELED) {
                Toast.makeText(this, "Sign in cancelled", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
            Uri selectedImageUri = data.getData();
            //reference to the specific photo.
            //taking the refernce of chat photos and making a child and named it by last part of the part segment for the uri.
            //example if content:local_images/foo/4
            //so it will get the name 4
            //then the file name that will will storing in app will be 4.
            // at this part we'll got the ref location of the photo which we're going to save, and we've get the uri of the image which we're going to save.
            StorageReference photoRef = mChatPhotoStorageReference.child(selectedImageUri.getLastPathSegment());

            //upload the file to firebase Storage.
            photoRef.putFile(selectedImageUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    //with the help of this url we'll be saving it to our weChat database along with other messages.
                    Uri downloadUrl = taskSnapshot.getDownloadUrl();

                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUrl.toString());
                    mDatabaseReference.push().setValue(friendlyMessage);

//                    DownloadTask downloadTask = new DownloadTask();
//                    downloadTask.execute(downloadUrl.toString());

                }
            });
        }

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        detachDatabaseReadListener();
        mMessageAdapter.clear();
    }

    private void onSignedInInitialize(String username) {
        mUsername = username;
        attachDatabaseReadListener();
    }

    private void onSignedOutCleanup() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();

    }

    private void attachDatabaseReadListener() {
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                    //message will deserielize into the same pattern as in Friendly class we've created.
                    //and attaching the message to an adapter.
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);

//                    DownloadTask downloadTask = new DownloadTask();
//                    downloadTask.execute(friendlyMessage.getPhotoUrl());

//                    NotificationCompat.Builder mBuilder = (NotificationCompat.Builder) new NotificationCompat.Builder(MainActivity.this)
//                            .setSmallIcon(R.drawable.ic_email_white_18dp)
//                            .setContentTitle("WeChat")
//                            .setContentText(friendlyMessage.getName().toString());
//
//                    Intent resultIntent =new Intent(MainActivity.this, MainActivity.class);
//
//                    TaskStackBuilder stackBuilder = TaskStackBuilder.create(MainActivity.this);
//                    stackBuilder.addParentStack(MainActivity.this);
//
//                    stackBuilder.addNextIntent(resultIntent);
//
//                    PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT);
//
//                    mBuilder.setContentIntent(resultPendingIntent);
//
//                    NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//
//                    int mId=0;
//                    mNotificationManager.notify(mId, mBuilder.build());


                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            };

            mDatabaseReference.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            mDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if(v.getId() == R.id.messageListView);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.item_menu,menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.image_save:
                AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo ) item.getMenuInfo();
                //getting a current position.
                mPosition = info.position;
                //putting the position to the adapter.
                FriendlyMessage friendlyMessage1 = mMessageAdapter.getItem(mPosition);
                //calling the asyncing task class.
                DownloadTask downloadTask = new DownloadTask();
                downloadTask.execute(friendlyMessage1.getPhotoUrl());
            default:
                return super.onContextItemSelected(item);
        }

    }

    class DownloadTask extends AsyncTask<String, Integer, String> {

        ProgressDialog mProgressDialog;

        @Override
        protected String doInBackground(String... params) {
            String path =  params[0];
            int file_lenght = 0;
            try {
                URL url = new URL(path);
                URLConnection urlConnection = url.openConnection();
                urlConnection.connect();
                file_lenght = urlConnection.getContentLength();
                File newFolder = new File("sdcard/myphotos");
                if(!newFolder.exists()){
                    newFolder.mkdir();
                }

                File inputFile = new File(newFolder,"downloadedImage.jpg");
                InputStream mInputStream = new BufferedInputStream(url.openStream(),8192);
                byte[] data = new byte[1024];
                int total = 0;
                int count = 0;

                OutputStream outputStream = new FileOutputStream(inputFile);
                while ((count = mInputStream.read())!= -1){
                    total+= count;
                    outputStream.write(data,0,count);
                    int progress = (int)total*100/file_lenght;
                    publishProgress(progress);

                }
                mInputStream.close();
                outputStream.close();


            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "Download Complete ..";
        }

        @Override
        protected void onPreExecute() {
            mProgressDialog = new ProgressDialog(MainActivity.this);
            mProgressDialog.setTitle("downloading in Progresss..");
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setMax(100);
            mProgressDialog.setProgress(0);
            mProgressDialog.show();
        }

        @Override
        protected void onPostExecute(String result) {
            mProgressDialog.hide();;
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_SHORT).show();
            String path = "sdcard/myphotos/downloadedImage.jpg";
            //imageView.setImageDrawable(Drawable.createFromPath(path));

        }

        @Override
        protected void onCancelled(String aVoid) {
            super.onCancelled(aVoid);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            mProgressDialog.setProgress(values[0]);

        }
    }
}
