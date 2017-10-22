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
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
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
import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.storage.UploadTask.TaskSnapshot;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

  public static final String ANONYMOUS = "anonymous";
  public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
  public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";
  public static final int RC_SIGN_IN = 1;
  private static final String TAG = "MainActivity";
  private static final int RC_PHOTO_PICKER = 2;

  private ListView mMessageListView;
  private MessageAdapter mMessageAdapter;
  private ProgressBar mProgressBar;
  private ImageButton mPhotoPickerButton;
  private EditText mMessageEditText;
  private Button mSendButton;

  private String mUsername;

  private FirebaseDatabase mFirebaseDatabase;
  private DatabaseReference mDatabaseReference;
  private ChildEventListener mChildEventListener;
  private FirebaseAuth mFirebaseAuth;
  private FirebaseAuth.AuthStateListener mAuthStateListener;
  private FirebaseStorage mFirebaStorage;
  private StorageReference mStorageReference;
  private FirebaseRemoteConfig mFirebaseRemoteConfig;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mUsername = ANONYMOUS;

    // get access to firebase database
    mFirebaseDatabase = FirebaseDatabase.getInstance();

    // get access to firebase auth
    mFirebaseAuth = FirebaseAuth.getInstance();

    // get access to firebase storage
    mFirebaStorage = FirebaseStorage.getInstance();

    // get reference to Firebase Remote Config
    mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

    // get access to the specific part of the database
    // get reference to the root node : mFirebaseDatabase.getReference()
    mDatabaseReference = mFirebaseDatabase.getReference().child("messages");
    // get reference to the corresponding folder in storage
    mStorageReference = mFirebaStorage.getReference().child("chat_photos");

    // Initialize references to views
    mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
    mMessageListView = (ListView) findViewById(R.id.messageListView);
    mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
    mMessageEditText = (EditText) findViewById(R.id.messageEditText);
    mSendButton = (Button) findViewById(R.id.sendButton);

    // ImagePickerButton shows an image picker to upload a image for a message
    mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(Intent.createChooser(intent, "Complete action using"),
            RC_PHOTO_PICKER);
      }
    });

    // Initialize message ListView and its adapter
    List<FriendlyMessage> friendlyMessages = new ArrayList<>();
    mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
    mMessageListView.setAdapter(mMessageAdapter);

    // Initialize progress bar
    mProgressBar.setVisibility(ProgressBar.INVISIBLE);

    // Enable Send button when there's text to send
    //------------TextWatcher prevents to send empty message -----------------//
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
    mMessageEditText
        .setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

    // Send button sends a message and clears the EditText
    mSendButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        // create a message by getting the text of the edittext and setting the username
        // set the photo url to null for now
        FriendlyMessage friendlyMessage =
            new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);

        // save this data in db
        // create unique id for message using 'push'
        // set the value of this id to friendlyMessage
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
          // signed in
          Toast.makeText(MainActivity.this, "Yeay, signed in", Toast.LENGTH_SHORT).show();
          onSignedInSetup(firebaseUser.getDisplayName());
        } else {
          onSignedOutCleanUp();
          // signed out
          startActivityForResult(
              AuthUI.getInstance()
                  .createSignInIntentBuilder()
                  .setIsSmartLockEnabled(false)
                  .setAvailableProviders(
                      Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                          new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
                  .build(),
              RC_SIGN_IN);
        }
      }
    };

    // set the settings for the Firebase Remote Config
    FirebaseRemoteConfigSettings firebaseRemoteConfigSettings = new FirebaseRemoteConfigSettings.Builder()
        .setDeveloperModeEnabled(BuildConfig.DEBUG)
        .build();

    //apply setting
    mFirebaseRemoteConfig.setConfigSettings(firebaseRemoteConfigSettings);

    // define parameter values for remote config
    Map<String, Object> defaultConfigMap = new HashMap<>();
    defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);

    mFirebaseRemoteConfig.setDefaults(defaultConfigMap);
    fetchConfig();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == RC_SIGN_IN) {
      if (resultCode == RESULT_OK) {
        Toast.makeText(MainActivity.this, "Signed in user", Toast.LENGTH_SHORT).show();
      } else if (resultCode == RESULT_CANCELED) {
        Toast.makeText(MainActivity.this, "Signed in calcelled", Toast.LENGTH_SHORT).show();
        finish();
      } else if (requestCode == RC_PHOTO_PICKER && RESULT_OK == resultCode) {

        // get selected image uri
        Uri selectedImageUri = data.getData();

        StorageReference photoRef =
            mStorageReference.child(selectedImageUri.getLastPathSegment());

        // upload file to Firebase Storage
        UploadTask uploadTask = photoRef.putFile(selectedImageUri);

        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnFailureListener(new OnFailureListener() {

          @Override
          public void onFailure(@NonNull Exception exception) {
            // Handle unsuccessful uploads
          }

        }).addOnSuccessListener(new OnSuccessListener<TaskSnapshot>() {

          @Override
          public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
            // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
            Uri downloadUrl = taskSnapshot.getDownloadUrl();

            // create message with the stored image
            FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername,
                downloadUrl.toString());

            // save that message into db
            mDatabaseReference.push().setValue(friendlyMessage);

          }
        });

      }
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
    int id = item.getItemId();
    switch (id) {
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

  private void onSignedInSetup(String username) {
    mUsername = username;
    attachDatabaseReadListener();
  }

  private void onSignedOutCleanUp() {
    mUsername = ANONYMOUS;
    // clear adapter to prevent duplicate messages after login - logout multiple times
    mMessageAdapter.clear();
    // remove listener
    detachDatabaseReadListener();

  }

  private void attachDatabaseReadListener() {
    if (mChildEventListener == null) {
      mChildEventListener = new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot dataSnapshot, String s) {
          //get data from the database
          FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
          // add data to the adapter
          mMessageAdapter.add(friendlyMessage);
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
      // add child event listener to the 'message' node of the database
      // in case of change in message children it will call the listener
      mDatabaseReference.addChildEventListener(mChildEventListener);
    }
  }

  private void detachDatabaseReadListener() {
    if (mChildEventListener != null) {
      mDatabaseReference.removeEventListener(mChildEventListener);
      mChildEventListener = null;
    }

  }

  // Fetch the config to determine the allowed length of messages.
  public void fetchConfig() {
    long cacheExpiration = 3600; // 1 hour in seconds
    // If developer mode is enabled reduce cacheExpiration to 0 so that each fetch goes to the
    // server. This should not be used in release builds.
    if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
      cacheExpiration = 0;
    }
    mFirebaseRemoteConfig.fetch(cacheExpiration)
        .addOnSuccessListener(new OnSuccessListener<Void>() {
          @Override
          public void onSuccess(Void aVoid) {
            // Make the fetched config available
            // via FirebaseRemoteConfig get<type> calls, e.g., getLong, getString.
            mFirebaseRemoteConfig.activateFetched();

            // Update the EditText length limit with
            // the newly retrieved values from Remote Config.
            applyRetrievedLengthLimit();
          }
        })
        .addOnFailureListener(new OnFailureListener() {
          @Override
          public void onFailure(@NonNull Exception e) {
            // An error occurred when fetching the config.
            Log.w(TAG, "Error fetching config", e);

            // Update the EditText length limit with
            // the newly retrieved values from Remote Config.
            applyRetrievedLengthLimit();
          }
        });
  }

  /**
   * Apply retrieved length limit to edit text field. This result may be fresh from the server or it
   * may be from cached values.
   */
  private void applyRetrievedLengthLimit() {
    Long friendly_msg_length = mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
    mMessageEditText.setFilters(
        new InputFilter[]{new InputFilter.LengthFilter(friendly_msg_length.intValue())});
    Log.d(TAG, FRIENDLY_MSG_LENGTH_KEY + " = " + friendly_msg_length);
  }
}
