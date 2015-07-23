package com.alto.mover;

import android.content.ContentResolver;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

class UploadResult {
    private JSONArray results;

    UploadResult() {
        results = new JSONArray();
    }

    public JSONArray getResults() {
        return results;
    }

    private void addResult(String id, boolean success) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("success", success);
        result.put("id", id);
        results.put(result);
    }

    public void addSuccess(String id) throws JSONException {
        addResult(id, true);
    }

    public void addFailure(String id) throws JSONException{
        addResult(id, false);

    }

    public void addAllResults(JSONArray newResults) throws JSONException {
        for (int i=0; i<newResults.length(); i++) {
            results.put(newResults.get(i));
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
    String name;
    String text;
    Integer status;
    String cloudId;
    Uri _imageURI;
    Uri _videoURI;
    ContentResolver _resolver;

    public Card(JSONObject card, ContentResolver resolver) throws JSONException {
        super(card);

        cloudId = card.getString("cloudId");
        name = card.getString("name");
        text = card.optString("text");
        status = card.getInt("status");
        _resolver = resolver;

        if (!card.optString("imageURI").isEmpty()) {
            _imageURI = Uri.parse(card.optString("imageURI"));
        }

        if (!card.optString("videoURI").isEmpty()) {
            _videoURI = Uri.parse(card.optString("videoURI"));
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject card = super.toJSON();

        card.put("cloudId", cloudId);
        card.put("_imageURI", _imageURI);
        card.put("imageURI", getImagePrivateStoragePath());
        card.put("videoURI", getVideoPrivateStoragePath());
        card.put("_videoURI", _videoURI);
        card.put("name", name);
        card.put("text", text);
        card.put("status", status);

        return card;
    }

    public String getImageFilename() {
        if (!hasImage()) {
            return null;
        }
        return getFilenameFromUri(_imageURI);
    }

    public String getVideoFilename() {
        if (!hasVideo()) {
            return null;
        }
        return getFilenameFromUri(_videoURI);
    }

    private String getFilenameFromUri(Uri uri) {
        String path = getRealPathFromURI(uri);
        String[] parts = path.split("/");
        return parts[parts.length-1];
    }

    private String getRealPathFromURI(Uri contentUri) {
        Cursor cursor = null;

        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = _resolver.query(contentUri,  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean hasImage() {
        return _imageURI != null;
    }

    public boolean hasVideo() {
        return _videoURI != null;
    }

    public InputStream getImage() throws FileNotFoundException {
        return _resolver.openInputStream(_imageURI);
    }

    public String getPrivateStorageDir() {
    return id + "_media";
    }

    public String getImagePrivateStoragePath() {
        if (!hasImage()) {
            return null;
        }
        return getPrivateStorageDir() + "/" + getImageFilename();
    }

    public String getVideoPrivateStoragePath() {
        if (!hasVideo()) {
            return null;
        }
        return getPrivateStorageDir() + "/" + getVideoFilename();
    }
}

public class MoverService extends BackgroundService {
    JSONObject mConfig = new JSONObject();
    Session mSession;
    ChannelSftp mChannel;
    Log mLog;

    protected void connect(Cloud cloud) throws JSONException, SftpException, JSchException {
        mLog.addMessage("Connecting");
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
        ContentResolver resolver = getContentResolver();

        for (int i=0; i<cards.length(); i++) {
            Card card = new Card(cards.getJSONObject(i), resolver);
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

    private boolean uploadCard(Card card){
        boolean success;

        try {
            mLog.addMessage("On card " + card.id);

            OutputStream output = mChannel.put(card.id);
            output.write(card.toJSON().toString().getBytes());
            output.close();

            if (card.hasImage()) {
                ensurePath(card.getPrivateStorageDir());

                mLog.addMessage("Trying to open " + card._imageURI);
                InputStream input = card.getImage();
                mLog.addMessage("File opened");

                mLog.addMessage("Saving to " + card.getPrivateStorageDir());
                output = mChannel.put(card.getImagePrivateStoragePath());
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
            connect(cloud);

            mLog.addMessage("In directory " + mChannel.pwd());
            ensurePath(cloud.privateDirectory);

            mLog.addMessage("Changing to directory " + cloud.privateDirectory);
            mChannel.cd(cloud.privateDirectory);

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

                // Keeps from making unnecessary connections to clouds
                if (cardsInCloud.isEmpty()) {
                    continue;
                }

                uploadResult.addAllResults(processCloud(cloud, cardsInCloud).getResults());
            }

            // We also provide the same message in our JSON Result
            result.put("updatedCards", uploadResult.getResults());
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
