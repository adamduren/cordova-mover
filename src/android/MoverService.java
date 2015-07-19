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

class UploadResult {
    private JSONArray successfulCardIds;
    private JSONArray failedCardIds;

    UploadResult() {
        successfulCardIds = new JSONArray();
        failedCardIds = new JSONArray();
    }

    public JSONArray getSuccesses () {
        return successfulCardIds;
    }

    public JSONArray getFailures () {
        return successfulCardIds;
    }

    public void addSuccess(String id) {
        successfulCardIds.put(id);
    }

    public void addFailure(String id) {
        failedCardIds.put(id);
    }

    public void addAllSuccesses(JSONArray successIds) throws JSONException {
        for (int i=0; i<successIds.length(); i++) {
           addSuccess(successIds.getString(i));
        }
    }

    public void addAllFailures(JSONArray failureIds) throws JSONException {
        for (int i=0; i<failureIds.length(); i++) {
            addSuccess(failureIds.getString(i));
        }
    }
}

class Log {
    String mLog = "";

    public void addMessage(String message) {
        mLog += message + "\n";
    }
}

public class MoverService extends BackgroundService {
    JSONObject mConfig = new JSONObject();
    Session mSession;
    ChannelSftp mChannel;
    Log mLog = new Log();

    protected void connect(JSONObject cloud) throws JSONException, SftpException, JSchException {
        JSch jsch = new JSch();

        String user = cloud.getString("username");
        String password = cloud.getString("password");
        String host = cloud.getString("host");
        int port = cloud.optInt("port", 22);

        mSession = jsch.getSession(user, host, port);
        mSession.setPassword(password);
        mSession.setConfig("StrictHostKeyChecking", "no");
        mSession.connect();

        ChannelSftp mChannel = (ChannelSftp) mSession.openChannel("sftp");
        mChannel.connect();
    }

    protected JSONArray getCardsForCloud(JSONObject cloud, JSONArray cards) throws JSONException {
        String currentCloudId = cloud.getString("_id");
        JSONArray cardsInCloud = new JSONArray();

        for (int i=0; i<cards.length(); i++) {
            JSONObject card = cards.getJSONObject(i);
            if (currentCloudId.equals(card.getString("cloudId"))) {
                cardsInCloud.put(card);
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
                mChannel.mkdir(buildPath);
            }
        }
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

    private UploadResult uploadCards(JSONArray cards) throws JSONException {
        UploadResult result = new UploadResult();

        for (int i=0; i<cards.length(); i++) {
            JSONObject card = cards.getJSONObject(i);
            String currentCardId = card.getString("_id");

            if (uploadCard(card)) {
                result.addSuccess(currentCardId);
            } else {
                result.addFailure(currentCardId);
            }
        }

        return result;
    }

    private boolean uploadCard(JSONObject card){
        boolean success;

        try {
            String currentCardId = card.getString("_id");
            String cardImageURIString = card.optString("imageURI");
            mLog.addMessage("\n On card " + currentCardId);

            OutputStream output = mChannel.put(currentCardId);
            output.write(card.toString().getBytes());
            output.close();

            if (!cardImageURIString.equals("")) {
                Uri cardImageUri = Uri.parse(cardImageURIString);
                mLog.addMessage("\n Trying to open " + cardImageURIString);
                mLog.addMessage("\n Parsed as " + cardImageUri.toString());
                InputStream input = getContentResolver().openInputStream(cardImageUri);
                mLog.addMessage("\n File opened");

                ensurePath(cardImageUri.getPath(), true);

                mLog.addMessage("\n Saving to " + cardImageUri.getPath().substring(1));
                output = mChannel.put(cardImageUri.getPath().substring(1));
                copy(input, output);
                output.close();
            }

            success = true;
        } catch (Exception e) {
            success = false;
        }

        return success;
    }

    private UploadResult processCloud(JSONObject cloud, JSONArray cards) {
        UploadResult result = new UploadResult();

        try {
            mLog.addMessage("\n On cloud " + cloud.getString("name"));

            String privateDirectory = cloud.getString("privateDirectory");

            mLog.addMessage("\n Connecting");
            connect(cloud);

            mLog.addMessage("\n In directory " + mChannel.pwd());
            mLog.addMessage("\n Changing to directory " + privateDirectory);
            mChannel.cd(privateDirectory);

            UploadResult cloudUploadResult = uploadCards(cards);
            result.addAllSuccesses(cloudUploadResult.getSuccesses());
            result.addAllFailures(cloudUploadResult.getFailures());

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

        try {
            JSONArray cards = mConfig.optJSONArray("cards");
            JSONArray clouds = mConfig.optJSONArray("clouds");

            for (int i=0; i<clouds.length(); i++) {
                JSONObject cloud = clouds.getJSONObject(i);
                JSONArray cardsInCloud = getCardsForCloud(cloud, cards);

                if (cardsInCloud.length() == 0) {
                    continue;
                }

                processCloud(cloud, cards);
            }

            // We also provide the same message in our JSON Result
            result.put("successfulCardIds", uploadResult.getSuccesses());
            result.put("failedCardIds", uploadResult.getFailures());
            result.put("log", mLog);
        } catch (Exception e) {
            // In production code, you would have some exception handling here
            try {
              result.put("error", e.toString());
              result.put("log", mLog);
            } catch (JSONException e2) {}
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
