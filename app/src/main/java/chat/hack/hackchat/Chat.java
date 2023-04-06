package chat.hack.hackchat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.core.app.NotificationCompat;
import androidx.core.view.GravityCompat;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;

import chat.hack.hackchat.chat.hack.hackchat.ads.HackChatInterstitialAd;
import chat.hack.hackchat.chat.hack.hackchat.ads.HackChatInterstitialAdvertable;

public class Chat extends AppCompatActivity implements OnlineNavDrawerFragment.GetDataInterface,
        OnlineNavDrawerFragment.PassDataInterface, HackChatInterstitialAdvertable {
    private ListView lvMessages;
    private EditText etMessage;
    private ImageButton bSend;

    private final static String url = "wss://hack.chat/chat-ws";

    private WebSocketClient ws;
    private String myNick = "", channel = "", lastSent = "";
    private ArrayList<String> onlineList;

    private ArrayList<MessageItem> messageList;
    private ListViewAdapter adapter;
    private Toolbar toolbar;

    private DrawerLayout mDrawerLayout;

    private int unread = 0;
    private boolean appInBackground = false;
    private boolean allowNotifications = true;

    private boolean reconnect = false; // to display "Recoonected" after connection broken. Also, prevents listview from being cleared.
    // Set it to "false" again when joining with a different/same nickname again.
    private boolean exitUsingBackKey = false; // onDisconnect() is called again after this activity is closed using back key; this creates
    // a websocket connection again. So the chat starts in the background again.

    public static final String prefsFile = "chatroomNicknameFile";
    public static final String KEY_CHATROOM = "lastChatroom";
    public static final String KEY_NICKNAME = "lastNickname";
    public static final String KEY_JSON = "lastJson";

    private HackChatInterstitialAd interstitialAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat);

        interstitialAd = new HackChatInterstitialAd(this, getResources().getString(R.string.chatroomAdUniId));

        Bundle bundle = getIntent().getExtras();
        channel = bundle.getString(KEY_CHATROOM);
        setTitle(channel);

        toolbar = (Toolbar) findViewById(R.id.app_bar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        OnlineNavDrawerFragment drawerFragment = (OnlineNavDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.fragmentUsers);
        drawerFragment.setUp(mDrawerLayout, toolbar);

        lvMessages = (ListView) findViewById(R.id.lvMessages);
        etMessage = (EditText) findViewById(R.id.etMessage);
        bSend = (ImageButton) findViewById(R.id.bSend);

        onlineList = new ArrayList<>();
        messageList = new ArrayList<>();
        adapter = new ListViewAdapter(messageList, this);
        lvMessages.setAdapter(adapter);

        // add @username when a message is clicked
        lvMessages.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String text = etMessage.getText().toString();
                text += "@" + ((MessageItem) parent.getAdapter().getItem(position)).getSender() + " ";
                etMessage.setText(text);
                etMessage.setSelection(etMessage.getText().length());
            }
        });

        // show dialog to copy text to clipboard when a message is longpressed
        lvMessages.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> adapterView, View view, final int position, long l) {
                final AlertDialog.Builder alert = new AlertDialog.Builder(Chat.this);
                final AlertDialog dialog = alert.create();

                final TextView tvCopy = new TextView(getBaseContext());
                tvCopy.setText("Copy to clipboard");
                tvCopy.setTextAppearance(Chat.this, android.R.style.TextAppearance_Large);
                tvCopy.setLayoutParams(new TableLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                tvCopy.setBackgroundResource(R.drawable.copy_dialog_textview);

                tvCopy.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String text = ((MessageItem) adapterView.getAdapter().getItem(position)).getMessage();

                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText(text, text);
                        clipboard.setPrimaryClip(clip);

                        dialog.dismiss();
                    }
                });

                alert.setView(tvCopy, 5, 5, 5, 5);
                alert.show();

                return true;
            }
        });

        // to show unread count; see pushMessage() below
        lvMessages.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (unread != 0) {
                    int lastVisibleItem = firstVisibleItem + visibleItemCount;
                    int remainingItems = totalItemCount - lastVisibleItem;
                    if (remainingItems < unread) {
                        unread = remainingItems;
                        if (unread == 0) {
                            setTitle(channel);
                        } else {
                            setTitle("(" + unread + ") " + channel);
                        }
                    }
                }
            }
        });

        bSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etMessage.getText().toString();
                try {
                    JSONObject command = new JSONObject();
                    command.put("cmd", "chat");
                    command.put("text", text);
                    send(command);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                lastSent = text;
                etMessage.setText("");
            }
        });

        // Save this chatroom/channel in SharedPreferences
        SharedPreferences prefs = getSharedPreferences(prefsFile, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_CHATROOM, channel);
        editor.commit();

        // Get username
        getNickname(false);
        etMessage.setFocusable(true);

        // start() is now called inside getNickname()
        // start();
    }

    void start() {
//        List<BasicNameValuePair> extraHeaders = Arrays.asList(
//                new BasicNameValuePair("Cookie", "session=abcd")
//        );

        ws = new WebSocketClient(URI.create(url), new WebSocketClient.Listener() {
            @Override
            public long getInterval() {
                return 4*60*1000; // every 4 minutes
            }

            @Override
            public void onInterval() {
                try {
                    send(new JSONObject("{cmd: 'ping'}"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConnect() {
                String data = "{cmd: 'join', channel: " + channel + ", nick: \"" + myNick + "\"}";
                myNick = myNick.split("#")[0];

                try {
                    send(new JSONObject(data));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(String message) {
                try {
                    JSONObject obj = new JSONObject(message);
                    String cmd = obj.getString("cmd");

                    switch (cmd) {
                        case "chat":
                            String cls = "";
                            if (obj.has("admin")) {
                                cls = "admin";
                            } else if (obj.getString("nick").equals(myNick)) {
                                cls = "me";
                            }
                            if (obj.has("trip"))
                                pushMessage(obj.getString("nick"), obj.getString("text"), obj.getString("time"), cls,
                                        obj.getString("trip"));
                            else
                                pushMessage(obj.getString("nick"), obj.getString("text"), obj.getString("time"), cls, "");
                            break;

                        case "info":
                            pushMessage("*", obj.getString("text"), obj.getString("time"), "info", "");
                            break;

                        case "warn":
                            String text = obj.getString("text");
                            if (text.equals(("Nickname taken"))) {
                                text = "Nickname already taken. Please press the reconnect button at the top.";
                                onlineList.clear();
                            }
                            pushMessage("!", text, obj.getString("time"), "warn", "");
                            break;

                        case "onlineSet":
                            onlineList.clear();
                            text = "";
                            JSONArray jsonNicks = obj.getJSONArray("nicks"); // NOTE: here it is "nicks", not "nick"
                            for (int i = 0; i < jsonNicks.length(); i++) {
                                onlineList.add(jsonNicks.getString(i));
                                text += jsonNicks.getString(i) + ", ";
                            }

                            if (reconnect == false) {
                                // clear ListView as chat with new username is starting
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        messageList.clear();
                                        adapter.notifyDataSetChanged();
                                    }
                                });

                                // Show online users only when connecting first time. No need to display after attempt to
                                // reconnect has been successful. But, must display when nick has been changed.
                                pushMessage("*", "Users online: " + jsonNicks.length(), obj.getString("time"), "info", "");
                            } else {
                                pushMessage("!", "Reconnected successfully!", obj.getString("time"), "warn", "");
                            }
                            break;

                        case "onlineAdd":
                            onlineList.add(obj.getString("nick"));
                            pushMessage("*", obj.getString("nick") + " joined", obj.getString("time"), "info", "");
                            break;

                        case "onlineRemove":
                            onlineList.remove(obj.getString("nick"));
                            pushMessage("*", obj.getString("nick") + " left", obj.getString("time"), "info", "");
                            break;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(byte[] data) {

            }

            @Override
            public void onDisconnect(int code, String reason) {
                pushMessage("!", "Server disconnected. Attempting to reconnect...", "", "warn", "");
                reconnect = true;
                if (exitUsingBackKey == false) {
                    start();
                }
            }

            @Override
            public void onError(Exception error) {
                if (hasInternet() == false) {
                    pushMessage("!", "No internet connection. Press reconnect button at the top.", "", "warn", "");
                }
//                pushMessage("*", error.toString(), "", "info", "'");
            }
        }, null);

        ws.connect();

    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
        } else {
            interstitialAd.show();
            super.onBackPressed();
            exitUsingBackKey = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        appInBackground = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        appInBackground = false;

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(327);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myNick.equals("") == false && ws != null) { // ws == null when start() is not called; this happens when invalid nick is entered
            ws.disconnect();
        }
    }

    void send(final JSONObject obj) {
        if (ws != null && hasInternet()) {
            ws.send(obj.toString());
        } else if (hasInternet() == false) {
            Toast.makeText(Chat.this, "No internet connection!", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    void pushMessage(String nick, String text, String time, String cls, String trip) {
        final MessageItem messageItem = new MessageItem();
        messageItem.setSender(nick);
        messageItem.setMessage(text);
        messageItem.setTime(time);
        messageItem.setCls(cls);
        messageItem.setTrip(trip);
        messageItem.setMyNick(myNick);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageList.add(messageItem);
                adapter.notifyDataSetChanged();
                lvMessages.post(new Runnable() {
                    @Override
                    public void run() {
                        // ideally it should be "adapter.getCount() - 1", but somehow that doesn't scroll when at the bottom
                        if (lvMessages.getLastVisiblePosition() == adapter.getCount() - 2 &&
                                lvMessages.getChildAt(lvMessages.getChildCount() - 1).getBottom() <= lvMessages.getHeight()) {
                            lvMessages.smoothScrollToPosition(adapter.getCount() - 1);
                        } else {
                            if (lvMessages.getLastVisiblePosition() != adapter.getCount() - 1) {
                                unread++;
                                setTitle("(" + unread + ") " + channel);
                            }
                        }
                    }
                });
            }
        });

        // don't display notification when user closes chatroom (last condition inside "if")
        if (appInBackground && allowNotifications && text.equals("Server disconnected. Attempting to reconnect...") == false &&
                text.equals("Reconnected successfully!") == false) {
            Intent notificationIntent = new Intent(this, Chat.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationCompat.Builder notification = new NotificationCompat.Builder(this)
                    .setContentTitle(channel + ": Unread messages")
//                    .setContentText("apple")
                    .setSmallIcon(R.mipmap.ic_launcher)
//                    .setLargeIcon(R.mipmap.ic_launcher)
                    .setAutoCancel(true)
                    .setContentIntent(intent)
                    .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND | Notification.FLAG_AUTO_CANCEL);
            notificationManager.notify(327, notification.build());
        }

    }

    public void getNickname(final boolean fromReconnect) {
        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Nickname");
//        alert.setMessage("Enter nickname");

        final InputMethodManager imm = (InputMethodManager) getSystemService(this.INPUT_METHOD_SERVICE);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        // setFilters to limit the number of characters
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        input.setHint("Enter a nickname");

        SharedPreferences prefs = getSharedPreferences(prefsFile, MODE_PRIVATE);
        String lastNickname = prefs.getString(KEY_NICKNAME, "");

        if (lastNickname.length() > 0) {
            myNick = lastNickname;
            input.setText(lastNickname);
            input.setSelection(input.getText().length());
        }


        alert.setView(input, 15, 15, 15, 15);
        alert.setPositiveButton("Chat", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

                imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                if (myNick.equals("") != true && ws != null) { // if connected, close previous connection
                    ws.disconnect();
                }
                myNick = input.getText().toString().trim();

                if (!myNick.matches("[A-Za-z0-9_#]+")) {
                    pushMessage("!", "Nickname must consist of up to 24 letters, numbers, and underscores (no spaces, etc.). " +
                            "Press the reconnect button at the top.", "", "warn", "");
                    return;
                }

                SharedPreferences prefs = getSharedPreferences(prefsFile, MODE_PRIVATE);
                if (prefs.getString(KEY_NICKNAME, "") == "") {
                    // don't clear message list if reconnecting with same nickname
                    reconnect = false; // this is WRONG! Don't remember why I put this here; It serves no purpose!
                    // Too afraid to remove it now :)
                }
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_NICKNAME, myNick);
                editor.commit();

                start();
            }
        });

        alert.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                        //imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                        if (fromReconnect == false)
                            finish();
                    }
                });

        final AlertDialog dialog = alert.create();
        dialog.setCancelable(false);
        // alert.show();  // if alert.show() is called, it causes NPE; calling dialog.show() works fine!
        dialog.show();
        /*
            getButton() can only be called after alert.show()
            Diabling positive button when no nickname is entered in the EditText
         */
        if (myNick.equals("")) {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(false);
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(true);
                } else {
                    dialog.getButton(Dialog.BUTTON_POSITIVE).setEnabled(false);
                }
            }
        });

        input.requestFocus();
        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_share) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
//            i.putExtra(Intent.EXTRA_SUBJECT, "hack.chat");
            String text = "Hack Chat! Join me for a chat on:\n" +
                    "https://hack.chat/?" + channel + "\n";
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, "Share with:"));

            return true;
        } else if (id == R.id.action_reconnect) {
            getNickname(true);
            return true;
        } else if (id == R.id.action_notifications) {
            if (item.getTitle().equals("Mute notifications")) {
                allowNotifications = false;
                item.setTitle("Allow notifications");
                item.setIcon(R.mipmap.ic_notifications_off);
            } else {
                allowNotifications = true;
                item.setTitle("Mute notifications");
                item.setIcon(R.mipmap.ic_notifications_on);
            }
        } else if (id == R.id.action_json) {
            sendRawJSON();
        }

        return super.onOptionsItemSelected(item);
    }

    void sendRawJSON() {
        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("JSON");

        final InputMethodManager imm = (InputMethodManager) getSystemService(this.INPUT_METHOD_SERVICE);

        final EditText input = new EditText(this);
        // setFilters to limit the number of characters
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        input.setHint("Enter json to send");

        SharedPreferences prefs = getSharedPreferences(prefsFile, MODE_PRIVATE);
        String lastJSON = prefs.getString(KEY_JSON, "");

        if (lastJSON.length() > 0) {
            input.setText(lastJSON);
            input.setSelection(input.getText().length());
        }

        alert.setView(input, 15, 15, 15, 15);
        alert.setPositiveButton("Send", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String data = input.getText().toString();

                try {
                    send(new JSONObject(data));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                SharedPreferences prefs = getSharedPreferences(prefsFile, MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_JSON, data);
                editor.commit();

                imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

            }
        });

        alert.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                        //imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                    }
                });

        alert.setNeutralButton("New JSON", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        final AlertDialog dialog = alert.create();
        dialog.setCancelable(false);
        // alert.show();  // if alert.show() is called, it causes NPE; calling dialog.show() works fine!
        dialog.show();

        // need to do dialog.getButton() to prevent the dialog from closing when BUTTON_NEUTRAL is clicked
        // more info: http://stackoverflow.com/a/15619098/3739412
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("{  }");
                input.setSelection(2);
            }
        });

        // to show keyboard
        input.requestFocus();
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    @Override
    public void onInterstitialAdClosed() {
        // Do nothing
    }

    private boolean hasInternet() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    @Override
    public ArrayList<String> getDataList() {
        return onlineList;
    }

    @Override
    public void passData(String clicked) {
        String text = etMessage.getText().toString();
        etMessage.setText(text + clicked);
        etMessage.setSelection(etMessage.getText().length());
    }

}
