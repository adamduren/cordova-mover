package com.alto.mover;

import android.util.Log;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.file.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.UUID;


public class Mover extends CordovaPlugin {
    HashMap<String, ChannelSftp> channels = new HashMap();

    private interface SftpOp {
        void run(String channelId, ChannelSftp channel, JSONArray args) throws Exception;
    }

    @Override
    public boolean execute(final String action, final String rawArgs, final CallbackContext callbackContext) throws JSONException {
        Log.i("Wimsy", "Executing action: " + action);

        if (action.equals("testConnection") || action.equals("connect")) {
            JSONArray args = new JSONArray(rawArgs);
            JSONObject hostConfig = args.getJSONObject(0);
            String user = hostConfig.getString("user");
            String password = hostConfig.getString("password");
            String host = hostConfig.getString("host");
            int port = hostConfig.optInt("port", 22);

            if (action.equals("testConnection")) {
                this.testConnection(user, password, host, port, callbackContext);
            } else {
                this.connect(user, password, host, port, callbackContext);
            }
        } else if (action.equals("disconnect")) {
            JSONArray args = new JSONArray(rawArgs);
            this.disconnect(args.getString(0), callbackContext);
        } else if (action.equals("cd")) {
            JSONArray args = new JSONArray(rawArgs);
            this.cd(args.getString(0), args.getString(1), callbackContext);
        } else if (action.equals("pwd")) {
            JSONArray args = new JSONArray(rawArgs);
            this.pwd(args.getString(0), callbackContext);
        } else if (action.equals("put")) {
            this.put(rawArgs, callbackContext);
        } else if (action.equals("mkdir")) {
            JSONArray args = new JSONArray(rawArgs);
            this.mkdir(args.getString(0), args.getString(1), args.getBoolean(2), callbackContext);
        }  else if (action.equals("stat")) {
            JSONArray args = new JSONArray(rawArgs);
            this.stat(args.getString(0), args.getString(1), callbackContext);
        }  else if (action.equals("rm")) {
            this.rm(rawArgs, callbackContext);
        }  else if (action.equals("rmdir")) {
            this.rmdir(rawArgs, callbackContext);
        } else {
            return false;
        }


        return true;
    }

    private void testConnection(String user, String password, String host, int port, CallbackContext callbackContext) {
        try {
            JSch jsch = new JSch();
            String testFilename = "_altoTest";

            Session session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            OutputStream output = channel.put(testFilename);
            output.write("Hello Alto".getBytes());
            output.close();

            channel.rm(testFilename);
            channel.disconnect();

            session.disconnect();

            callbackContext.success();
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void connect(final String user, final String password, final String host, final int port, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {

                try {
                    JSch jsch = new JSch();

                    Session session = jsch.getSession(user, host, port);
                    session.setPassword(password);
                    session.setConfig("StrictHostKeyChecking", "no");
                    session.connect();

                    ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
                    channel.connect();

                    String key = UUID.randomUUID().toString();
                    channels.put(key, channel);

                    callbackContext.success(key);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void disconnect(String channelId, CallbackContext callbackContext){
        ChannelSftp channel = channels.get(channelId);
        Session session = null;

        if (channel == null) {
            callbackContext.error("Invalid channelId");
            return;
        }

        try {
            session = channel.getSession();
        } catch (JSchException e){

        } finally {
            channel.disconnect();

            if (session != null) {
                session.disconnect();
            }
        }

        callbackContext.success();
    }

    private void cd(String channelId, String path, CallbackContext callbackContext){
        ChannelSftp channel = channels.get(channelId);

        if (channel == null) {
            callbackContext.error("Invalid channelId");
            return;
        }

        try {
            channel.cd(path);
            callbackContext.success(channel.pwd());
        } catch (SftpException e){
            callbackContext.error(e.getMessage());
        }
    }

    private void pwd(String channelId, CallbackContext callbackContext){
        ChannelSftp channel = channels.get(channelId);

        if (channel == null) {
            callbackContext.error("Invalid channelId");
            return;
        }

        try {
            callbackContext.success(channel.pwd());
        } catch (SftpException e){
            callbackContext.error(e.getMessage());
        }
    }

    private void put(final String rawArgs, final CallbackContext callbackContext) {
        threadHelper(new SftpOp() {
            @Override
            public void run(String channelId, ChannelSftp channel, JSONArray args) throws Exception {
                try {
                    String name = args.getString(1);
                    JSONObject dataContainer = args.getJSONObject(2);
                    boolean ensurePath = args.getBoolean(3);

                    if (ensurePath) {
                        _ensurePath(channelId, name, true);
                    }

                    String type = dataContainer.getString("type");

                    Log.w("Wimsy", "Putting file " + name + " of type " + type);

                    OutputStream output = channel.put(name);

                    byte[] dataByteArray;

                    if (type.equals("Int8Array")) {
                        Log.w("alto", "Is Int8Array");

                        JSONArray dataArray = dataContainer.getJSONArray("data");
                        dataByteArray = new byte[dataArray.length()];

                        for (int i = 0; i < dataArray.length(); i++) {
                            dataByteArray[i] = (byte) dataArray.getInt(i);
                        }
                    } else {
                        String data = dataContainer.getString("data");
                        dataByteArray = data.getBytes();
                    }
                    Log.w("alto", dataByteArray.toString());
                    output.write(dataByteArray);
                    output.close();
                    callbackContext.success(channel.pwd());
                } catch (Exception e) {
                    Log.e("alto", e.getClass().getName() + ":" + e.getMessage());
                    callbackContext.error(e.getMessage());
                }
            }
        }, rawArgs, callbackContext);

    }

    private void rm(final String rawArgs, final CallbackContext callbackContext) {
        threadHelper(new SftpOp() {
            @Override
            public void run(String channelId, ChannelSftp channel, JSONArray args) throws JSONException {
                String name = args.getString(1);
                Log.w("Wimsy", "Deleting file " + name);

                try {
                    channel.rm(name);
                    callbackContext.success();
                } catch (SftpException e) {
                    callbackContext.success("File not found");
                }
            }
        }, rawArgs, callbackContext);

    }

    private void rmdir(final String rawArgs, final CallbackContext callbackContext) {
        threadHelper(new SftpOp() {
            @Override
            public void run(String channelId, ChannelSftp channel, JSONArray args) throws JSONException {
                String name = args.getString(1);
                Log.w("Wimsy", "Deleting folder " + name);

                try {
                    channel.rmdir(name);
                    callbackContext.success();
                } catch (SftpException e) {
                    callbackContext.success("File not found");
                }
            }
        }, rawArgs, callbackContext);

    }

    private void mkdir(String channelId, String path, boolean recursive, CallbackContext callbackContext){
        ChannelSftp channel = channels.get(channelId);

        if (channel == null) {
            callbackContext.error("Invalid channelId");
            return;
        }

        try {

            if (recursive) {
                _ensurePath(channelId, path);
            } else {
                channel.mkdir(path);
            }

            callbackContext.success();
        } catch (Exception e){
            callbackContext.error(e.getMessage());
        }
    }

    private void stat(String channelId, String path, CallbackContext callbackContext){
        ChannelSftp channel = channels.get(channelId);

        if (channel == null) {
            callbackContext.error("Invalid channelId");
            return;
        }

        try {
            channel.stat(path);
            callbackContext.success();
        } catch (Exception e){
            callbackContext.error(e.getMessage());
        }
    }

    private void _ensurePath(String channelId, String path) throws Exception {
        _ensurePath(channelId, path, false);
    }

    private void _ensurePath(String channelId, String path, boolean excludeLast) throws Exception {
        ChannelSftp channel = channels.get(channelId);

        if (channel == null) {
            throw new Exception("Invalid channelId");
        }

        String buildPath = "";
        if (path.substring(0, 1).equals("/")) {
            path = path.substring(1);
            buildPath += "/";
        }

        String[] pathSegments = path.split("/");
        int size = pathSegments.length;

        if (excludeLast) {
            size -= 1;
        }

        for (int i=0; i<size; i++) {
            buildPath += pathSegments[i] + "/";

            try {
                channel.stat(buildPath);
            } catch (Exception e) {
                channel.mkdir(buildPath);
            }
        }
    }

    private void threadHelper(final SftpOp f, final String rawArgs, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    JSONArray args = new JSONArray(rawArgs);
                    String channelId = args.getString(0);
                    ChannelSftp channel = channels.get(channelId);

                    if (channel == null) {
                        callbackContext.error("Invalid channelId");
                        return;
                    }
                    synchronized (channel) {
                        f.run(channelId, channel, args);
                    }
                } catch ( Exception e) {
                    if (e instanceof JSONException ) {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.JSON_EXCEPTION));
                    } else {
                        e.printStackTrace();
                        callbackContext.error(FileUtils.UNKNOWN_ERR);
                    }
                }
            }
        });
    }
}
