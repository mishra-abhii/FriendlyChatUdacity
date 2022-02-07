/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.friendly_chat_firebase;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final int RC_PHOTO_PICKER = 2;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference messages;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private ChildEventListener messagesListener;
    private FirebaseStorage firebaseStorage;
    private StorageReference storageReference;

    private final int AUTH_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //initializes the firebase db
        firebaseDatabase = FirebaseDatabase.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();

        //.getReference gets me the reference to the root of DB, and then takes me to chile -- "messages"
        messages = firebaseDatabase.getReference().child("messages");
        storageReference = firebaseStorage.getReference().child("chat_photos"); // this is the location where chatPhotos get stored

        mUsername = ANONYMOUS;

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete Action using"), RC_PHOTO_PICKER);
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
        /**
         * WRITING Data to DB
         */
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                messages.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });

        /**
         * READING data from DB
         *
         * Firstly reading was done here,
         * but after auth, you need to read only when SIGNED IN!!! so moved down there!!
         */



        //initializing authstateListener
        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                //this firebaseAuth var contains whether at that moment the user is authenticated or not
                //getting current user
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
//                    User signed in
                    //Fetching Username of who typed the message!
                    String userName = user.getDisplayName();

                    //Helper Method -->
                    onSignedInInitialize(userName);
                }
                else {
//                    User is signed out
//                    As user is signed out, Sign in screen must be presented to him
//                    Therefore using FirebaseUI for sign in

                    //Helper Method -->
                    onSignedOutCleanup();

                    //to display sign in flow ---> FirebaseUI
                    startActivityForResult(AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setIsSmartLockEnabled(false)
                            .setAvailableProviders(
                                    Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.EmailBuilder().build()
                                    )
                            )
                            .build(), AUTH_REQUEST_CODE);

//                    Intent signInIntent =
//                            AuthUI.getInstance()
//                                    .createSignInIntentBuilder()
//                                    //Smart lock saves login creds for future, but for here we dont need it!!
//                                    .setIsSmartLockEnabled(false)
//                                    .setAvailableProviders(Arrays.asList(
//                                            new AuthUI.IdpConfig.GoogleBuilder().build(),
////                                            new AuthUI.IdpConfig.FacebookBuilder().build(),
////                                            new AuthUI.IdpConfig.TwitterBuilder().build(),
////                                            new AuthUI.IdpConfig.MicrosoftBuilder().build(),
////                                            new AuthUI.IdpConfig.YahooBuilder().build(),
////                                            new AuthUI.IdpConfig.AppleBuilder().build(),
//                                            new AuthUI.IdpConfig.EmailBuilder().build(),
////                                            new AuthUI.IdpConfig.PhoneBuilder().build(),
//
//                                            //see cant we remove this??, it shows error!!!!
//                                            new AuthUI.IdpConfig.AnonymousBuilder().build()))
//                                    .build();
                }
            }
        };

    }

    //To resolve the problem :  When we pressed BACK, it always brought us to Sign In In a LOOP
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == AUTH_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Signed In!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED){
                Toast.makeText(this, "Sign In CANCELLED!", Toast.LENGTH_SHORT).show();
                finish();
            }

        }

        //why is it put inside above if? that is when we are returning from the Sign In screen!
        //But here we are already Signed In

        // Finally found. It must be out of that if. Their code was wrong

        //Try it putting out of this and TRY!!
        if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
            //uri of image
            Uri selectedImageUri = data.getData();
            //Reference to Specific Photos
            StorageReference photoRef = storageReference.child(selectedImageUri.getLastPathSegment());
            //content://local_images/foo/4 --- so getLastPath... will give us the last part i.e. 4


            //can be written in one line too
            UploadTask uploadTask = photoRef.putFile(selectedImageUri);
//            uploadTask.addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
//                @Override
//                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//                    //THIS MAY NOT WORK SEEE
//                    //ORiginally its taskSnapshot.downloadUrl   NOW DEPRECATED!!
////                    Task<Uri> downloadUrl = photoRef.getDownloadUrl();
//
//                    //writing it to database now
//                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUrl.toString());
//                    messages.push().setValue(friendlyMessage);
//                }
//            });

            //getDownloadUrl for TaskSnapshot has been removed, so heres new method
            Task<Uri> urlTask = uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }

                    return photoRef.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if (task.isSuccessful()) {
                        Uri downloadUri = task.getResult();

                        Log.i("-----------------------", "onComplete: DownloadUri --->" + downloadUri);
                    //writing it to database now
                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUri.toString());
                    messages.push().setValue(friendlyMessage);
                    } else {
                        //Handle Failures
                    }
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
                //sign out
                AuthUI.getInstance().signOut(this);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * SEEEE why was this done??
     */

    @Override
    protected void onResume() {
        super.onResume();
        firebaseAuth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (authStateListener != null)
            firebaseAuth.removeAuthStateListener(authStateListener);

        //these are helpful also when the activity is destroyed!!
        //Like when on app ROTATION
        detachDatabaseReadListener();
        //coz, when onResume, it will reset the adapter with the values, so to avoid duplicate!
        mMessageAdapter.clear();
    }

    /**
     * These are to perform actions depending on signed state
     * @param userName
     */

    private void onSignedInInitialize(String userName) {
        //when signed in, use the userName and Attach read listener to read the messages in database
        mUsername = userName;
        attachDatabaseReadListener();
    }

    private void onSignedOutCleanup() {
        //when signed out, do cleanup. That is set username to ANONYMOUS, and tear down the chat screan(DETACH read listener)
        mUsername = ANONYMOUS;
        //Clearing adapter so No one can access messages on signed out
        //AND so no duplicate messages appear when signed in and out Multiple time
        // Everytime we sign in, Adapter will update its value from database!!!!
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }


    private void attachDatabaseReadListener() {
        //see what reason to use this?
        if (messagesListener == null) {
            /**
             * READING data from DB
             *
             * Firstly reading was done here,
             * but after auth, you need to read only when SIGNED IN!!! so moved down there!!
             */
            messagesListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    FriendlyMessage friendlyMessage = snapshot.getValue(FriendlyMessage.class);
                    //To display data in the app

                    //DONE IN COURSE
                    mMessageAdapter.add(friendlyMessage);
                    //I tried, BOTH WORK SAME!!!
//                THis updates views only when focus comes to the screen, that is after pressing back button to
//                remove keypad
//                friendlyMessages.add(friendlyMessage);
                }

                //We are not using these things here!!
                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                }
                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                }
                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            };

            //This attaches childEventListener to the messages child(THAT is the databaseReference pointint to messages)
            messages.addChildEventListener(messagesListener);
        }
    }

    private void detachDatabaseReadListener() {
        //remove eventListener from the database reference so, no changes are listened to!
        if(messagesListener != null) {
            messages.removeEventListener(messagesListener);

            //see what reason to use this condition;
            messagesListener = null;
        }
    }
}
