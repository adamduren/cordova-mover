package com.alto.mover;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.alto.mover.backgroundservice.BackgroundService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

class UploadResult {
    private JSONArray cards;

    UploadResult() {
        cards = new JSONArray();
    }

    public JSONArray getCards () {
        return cards;
    }

    private void addResult(String id, boolean success) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("success", id);
        result.put("id", success);
        cards.put(result);
    }

    public void addSuccess(String id) throws JSONException {
        addResult(id, true);
    }

    public void addFailure(String id) throws JSONException{
        addResult(id, false);

    }

    public void addAllCards(JSONArray cards) throws JSONException {
        for (int i=0; i<cards.length(); i++) {
            addSuccess(cards.getString(i));
        }
    }
}

class Log {
    String mLog = "";

    public void addMessage(String message) {
        mLog += message + "\n";
    }

    public String getLog() {
        return mLog;
    }
}

class Model {
    String id;
    String _rev;
    String documentType;
    String dateCreated;

    public Model(JSONObject model) throws JSONException {
        id = model.getString("_id");
        _rev = model.getString("_rev");
        documentType = model.getString("documentType");
        dateCreated = model.getString("dateCreated");
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("_id", id);
        json.put("_rev", _rev);
        json.put("documentType", documentType);
        json.put("dateCreated", dateCreated);

        return json;
    }
}

class Cloud extends  Model {
    String name;
    String privateDirectory;
    String user;
    String password;
    String host;
    int port;

    public Cloud(JSONObject cloud) throws JSONException {
        super(cloud);

        name = cloud.getString("name");
        privateDirectory = cloud.getString("privateDirectory");
        user = cloud.getString("username");
        password = cloud.getString("password");
        host = cloud.getString("host");
        port = cloud.optInt("port", 22);
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject cloud = super.toJSON();

        cloud.put("_id", id);
        cloud.put("name", name);
        cloud.put("privateDirectory", privateDirectory);
        cloud.put("username", user);
        cloud.put("password", password);
        cloud.put("host", host);
        cloud.put("port", port);

        return cloud;
    }
}

class Card extends Model {
    String id;
    String name;
    String text;
    boolean synced;
    String cloudId;
    String imageURI;
    String videoURI;

    public Card(JSONObject card) throws JSONException {
        super(card);

        this.cloudId = card.getString("cloudId");
        this.imageURI = card.optString("imageURI");
        this.name = card.getString("name");
        this.text = card.optString("text");
        this.synced = card.getBoolean("synced");
        this.videoURI = card.getString("videoURI");
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject card = super.toJSON();

        card.put("cloudId", cloudId);
        card.put("imageURI", imageURI);
        card.put("name", name);
        card.put("text", text);
        card.put("synced", synced);
        card.put("videoURI", videoURI);

        return card;
    }
}

public class MoverService extends BackgroundService {
    JSONObject mConfig = new JSONObject();
    Session mSession;
    ChannelSftp mChannel;
    Log mLog;

    protected void connect(Cloud cloud) throws JSONException, SftpException, JSchException {
        JSch jsch = new JSch();

        mSession = jsch.getSession(cloud.user, cloud.host, cloud.port);
        mSession.setPassword(cloud.password);
        mSession.setConfig("StrictHostKeyChecking", "no");
        mSession.connect();

        mChannel = (ChannelSftp) mSession.openChannel("sftp");
        mChannel.connect();
    }

    protected ArrayList<Card> getCardsForCloud(Cloud cloud, JSONArray cards) throws JSONException {
        String currentCloudId = cloud.id;
        ArrayList<Card> cardsInCloud = new ArrayList<Card>();

        for (int i=0; i<cards.length(); i++) {
            Card card = new Card(cards.getJSONObject(i));
            if (currentCloudId.equals(card.cloudId)) {
                cardsInCloud.add(card);
            }
        }

        return cardsInCloud;
    }

    protected void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int filesize;
        while ((filesize = input.read(buffer)) != -1) {
            output.write(buffer, 0, filesize);
        }
    }

    protected void ensurePath(String path) throws SftpException {
        ensurePath(path, false);
    }

    protected void ensurePath(String path, boolean excludeLast) throws SftpException {
        mLog.addMessage("Ensuring path" + path);

        String buildPath = "";
        if (path.substring(0, 1).equals("/")) {
            path = path.substring(1);
        }

        String[] pathSegments = path.split("/");
        int size = pathSegments.length;

        if (excludeLast) {
            size -= 1;
        }

        for (int i=0; i<size; i++) {
            buildPath += pathSegments[i] + "/";

            try {
                mChannel.stat(buildPath);
            } catch (Exception e) {
                mLog.addMessage("Creating" + buildPath);
                mChannel.mkdir(buildPath);
            }
        }
    }

    protected String getFilenameFromUri(Uri uri) {
        String path = getRealPathFromURI(uri);
        String[] parts = path.split("/");
        return parts[parts.length-1];
    }

    private boolean uploadCard(Card card){
        boolean success;

        try {
            mLog.addMessage("On card " + card.id);

            OutputStream output = mChannel.put(card.id);
            output.write(card.toJSON().toString().getBytes());
            output.close();

            if (!card.imageURI.equals("")) {
                String mediaDir = card.id + "_media";
                Uri cardImageUri = Uri.parse(card.imageURI);
                String imageDest = mediaDir + "/" + getFilenameFromUri(cardImageUri);

                mLog.addMessage("Trying to open " + card.imageURI);
                InputStream input = getContentResolver().openInputStream(cardImageUri);
                mLog.addMessage("File opened");

                ensurePath(mediaDir);

                mLog.addMessage("Saving to " + imageDest);
                output = mChannel.put(imageDest);
                copy(input, output);
                output.close();
            }

            success = true;
        } catch (Exception e) {
            success = false;
        }

        return success;
    }

    private UploadResult processCloud(Cloud cloud, ArrayList<Card> cards) {
        UploadResult result = new UploadResult();

        try {
            mLog.addMessage("On cloud " + cloud.name);

            String privateDirectory = cloud.privateDirectory;

            mLog.addMessage("Connecting");
            connect(cloud);

            mLog.addMessage("In directory " + mChannel.pwd());

            ensurePath(privateDirectory);

            mLog.addMessage("Changing to directory " + privateDirectory);
            mChannel.cd(privateDirectory);

            for (int i=0; i<cards.size(); i++) {
                Card card = cards.get(i);

                if (uploadCard(card)) {
                    result.addSuccess(card.id);
                } else {
                    result.addFailure(card.id);
                }
            }

            mChannel.exit();
            mSession.disconnect();
        } catch (Exception e) {
            mLog.addMessage(e.toString());
        }

        return result;
    }

    public String getRealPathFromURI(Uri contentUri) {
        Cursor cursor = null;

        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = getContentResolver().query(contentUri,  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    protected JSONObject doWork() {
        JSONObject result = new JSONObject();
        UploadResult uploadResult = new UploadResult();
        mLog = new Log();

        try {
            JSONArray cards = mConfig.optJSONArray("cards");
            JSONArray clouds = mConfig.optJSONArray("clouds");

            for (int i=0; i<clouds.length(); i++) {
                Cloud cloud = new Cloud(clouds.getJSONObject(i));
                ArrayList<Card> cardsInCloud = getCardsForCloud(cloud, cards);

                if (cardsInCloud.isEmpty()) {
                    continue;
                }

                uploadResult.addAllCards(processCloud(cloud, cardsInCloud).getCards());
            }

            // We also provide the same message in our JSON Result
            result.put("updatedCards", uploadResult.getCards());
        } catch (JSONException e) {
            // In production code, you would have some exception handling here
            try {
              result.put("error", e.toString());
            } catch (JSONException e2) {}
        } finally {
            try {
                result.put("log", mLog.getLog());
            } catch (JSONException e) {}

        }

        return result;
    }

    @Override
    protected JSONObject getConfig() {
       return mConfig;
    }

    @Override
    protected void setConfig(JSONObject config) {
        mConfig = config;
    }

    @Override
    protected JSONObject initialiseLatestResult() {
       JSONObject result = new JSONObject();
       return result;
    }
}
