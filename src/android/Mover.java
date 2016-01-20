package com.alto.mover;

import android.util.Log;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.file.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.UUID;


public class Mover extends CordovaPlugin {
    HashMap<String, ChannelSftp> mSftpChannels = new HashMap();
    HashMap<String, FTPClient> mFtpChannels = new HashMap();

    String mTestFilename = "_altoTest";

    private interface SftpOp {
        void run(ChannelSftp channel, FTPClient client, JSONObject args) throws Exception;
    }

    @Override
    public boolean execute(final String action, final String rawArgs, final CallbackContext callbackContext) throws JSONException {
        Log.i("Wimsy", "Executing action: " + action);

        final JSONObject args = new JSONObject(rawArgs);
        if (action.equals("testConnection")) {
            this.testConnection(args, callbackContext);
        } else if (action.equals("connect")) {
            this.connect(args, callbackContext);
        } else if (action.equals("disconnect")) {
            this.disconnect(args, callbackContext);
        } else if (action.equals("put")) {
            this.put(args, callbackContext);
        } else if (action.equals("rm")) {
            this.rm(args, callbackContext);
        }  else if (action.equals("rmdir")) {
            this.rmdir(args, callbackContext);
        } else {
            return false;
        }

        return true;
    }

    private void _testConnectionFtp(String user, String password, String host, int port, CallbackContext callbackContext) {
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(host, port);

            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                callbackContext.error("Operation failed. Server reply code: \" + replyCode");
                return;
            }

            boolean success = ftpClient.login(user, password);

            if (!success) {
                callbackContext.error("Bad username or password");
                return;
            }

            OutputStream output = ftpClient.storeFileStream(mTestFilename);
            output.write("Hello Alto".getBytes());
            output.close();

            if (!ftpClient.completePendingCommand()) {
                callbackContext.error("Could not write file");
            }

            ftpClient.deleteFile(mTestFilename);
            ftpClient.logout();
            ftpClient.disconnect();

            callbackContext.success();
        } catch (IOException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void _testConnectionSftp(String user, String password, String host, int port, CallbackContext callbackContext) {
        try {
            JSch jsch = new JSch();

            Session session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();

            OutputStream output = channel.put(mTestFilename);
            output.write("Hello Alto".getBytes());
            output.close();

            channel.rm(mTestFilename);
            channel.disconnect();

            session.disconnect();

            callbackContext.success();
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void testConnection(final JSONObject args, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                final String user;
                final String password;
                final String host;
                final String protocol;
                final int port;
                try {
                    user = args.getString("user");
                    password = args.getString("password");
                    host = args.getString("host");
                    protocol = args.getString("protocol");
                } catch (JSONException e) {
                    callbackContext.error(e.getMessage());
                    return;
                }

                if (protocol == "SFTP") {
                    port = args.optInt("port", 22);
                    _testConnectionSftp(user, password, host, port, callbackContext);
                } else {
                    port = args.optInt("port", 21);
                    _testConnectionFtp(user, password, host, port, callbackContext);
                }
            }
        });
    }

    private void _connectSftp(String user, String password, String host, int port, CallbackContext callbackContext) throws JSchException {
        JSch jsch = new JSch();

        Session session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();

        String key = UUID.randomUUID().toString();
        mSftpChannels.put(key, channel);

        callbackContext.success(key);
    }

    private void _connectFtp(String user, String password, String host, int port, CallbackContext callbackContext) throws IOException {
        FTPClient ftpClient = new FTPClient();
        ftpClient.connect(host, port);

        int replyCode = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(replyCode)) {
            callbackContext.error("Operation failed. Server reply code: \" + replyCode");
            return;
        }

        boolean success = ftpClient.login(user, password);

        if (!success) {
            callbackContext.error("Bad username or password");
            return;
        }

        String key = UUID.randomUUID().toString();
        mFtpChannels.put(key, ftpClient);

        callbackContext.success(key);
    }

    private void connect(final JSONObject args, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {

                try {
                    final String user;
                    final String password;
                    final String host;
                    final int port;
                    final String protocol;

                    try {
                        user = args.getString("user");
                        password = args.getString("password");
                        host = args.getString("host");
                        protocol = args.getString("protocol");
                    } catch (JSONException e) {
                        callbackContext.error(e.getMessage());
                        return;
                    }

                    if (protocol == "SFTP") {
                        port = args.optInt("port", 22);
                        _connectSftp(user, password, host, port, callbackContext);
                    } else {
                        port = args.optInt("port", 21);
                        _connectFtp(user, password, host, port, callbackContext);
                    }
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void disconnect(JSONObject args, CallbackContext callbackContext) {
        String channelId;
        String protocol;

        try {
            channelId = args.getString("key");
            protocol = args.getString("protocol");
        } catch (JSONException e) {
            callbackContext.error(e.getMessage());
            return;
        }

        if (protocol == "SFTP") {
            ChannelSftp channel = mSftpChannels.get(channelId);
            Session session = null;

            if (channel == null) {
                callbackContext.error("Invalid channelId of " + channelId);
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
        } else {
            FTPClient ftpClient = mFtpChannels.get(channelId);

            if (ftpClient == null) {
                callbackContext.error("Invalid channelId of " + channelId);
                return;
            }

            try {
                ftpClient.logout();
                ftpClient.disconnect();
            } catch (IOException e){

            }
        }

        callbackContext.success();
    }

    private void put(final JSONObject args, final CallbackContext callbackContext) {
        threadHelper(new SftpOp() {
            @Override
            public void run(ChannelSftp channel, FTPClient ftpClient, JSONObject args) throws Exception {
                try {
                    String name = args.getString("name");
                    JSONObject dataContainer = args.getJSONObject("dataContainer");
                    boolean ensurePath = args.getBoolean("ensurePath");

                    if (ensurePath) {
                        _ensurePath(channel, ftpClient, name, true);
                    }

                    String type = dataContainer.getString("type");

                    Log.w("Wimsy", "Putting file " + name + " of type " + type);

                    OutputStream output;

                    if (channel != null) {
                        output = channel.put(name);
                    } else {
                        output = ftpClient.storeFileStream(name);
                    }

                    byte[] dataByteArray;

                    if (type.equals("Int8Array")) {
                        JSONArray dataArray = dataContainer.getJSONArray("data");
                        dataByteArray = new byte[dataArray.length()];

                        for (int i = 0; i < dataArray.length(); i++) {
                            dataByteArray[i] = (byte) dataArray.getInt(i);
                        }
                    } else {
                        String data = dataContainer.getString("data");
                        dataByteArray = data.getBytes();
                    }

                    Log.w("alto", "writing");
                    output.write(dataByteArray);
                    Log.w("alto", "closing");
                    output.flush();
                    output.close();

                    if (ftpClient != null && !ftpClient.completePendingCommand()) {
                        callbackContext.error("Could not write file" + name);
                    }
                    Log.w("alto", "send success");

                    if (channel != null) {
                        callbackContext.success(channel.pwd());
                    } else {
                        callbackContext.success(ftpClient.pwd());
                    }

                } catch (Exception e) {
                    Log.e("alto", e.getClass().getName() + ":" + e.getMessage());
                    e.printStackTrace();
                    Log.e("alto", e.getStackTrace().toString());
                    callbackContext.error(e.getMessage());
                }
                Log.w("alto", "return");
            }
        }, args, callbackContext);

    }

    private void rm(final JSONObject args, final CallbackContext callbackContext) {
        threadHelper(new SftpOp() {
            @Override
            public void run(ChannelSftp channel, FTPClient ftpClient, JSONObject args) throws JSONException {
                String name = args.getString("name");
                Log.w("Wimsy", "Deleting file " + name);

                try {
                    if (channel != null) {
                        channel.rm(name);
                    } else {
                        ftpClient.deleteFile(name);
                    }
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.success("File not found: " + name);
                }

            }
        }, args, callbackContext);

    }

    private void rmdir(final JSONObject args, final CallbackContext callbackContext) {
        threadHelper(new SftpOp() {
            @Override
            public void run(ChannelSftp channel, FTPClient ftpClient, JSONObject args) throws JSONException {
                String name = args.getString("name");
                Log.w("Wimsy", "Deleting folder " + name);

                try {
                    if (channel != null) {
                        channel.rmdir(name);
                    } else {
                        ftpClient.rmd(name);
                    }

                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.success("File not found: " + name);
                }
            }
        }, args, callbackContext);

    }

    private void _ensurePath(ChannelSftp channel, FTPClient ftpClient, String path, boolean excludeLast) throws Exception {
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

            if (channel != null) {
                try {
                    channel.stat(buildPath);
                } catch (Exception e) {
                    channel.mkdir(buildPath);
                }
            } else {
                ftpClient.makeDirectory(buildPath);
            }

        }
    }

    private void threadHelper(final SftpOp f, final JSONObject args, final CallbackContext callbackContext) {

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String protocol = args.getString("protocol");
                    String cacheKey = args.getString("key");

                    if (protocol == "SFTP") {
                        ChannelSftp channel = mSftpChannels.get(cacheKey);

                        if (channel == null) {
                            callbackContext.error("Invalid channelId");
                            return;
                        }

                        synchronized (mSftpChannels) {
                            Log.w("alto", "Starting");
                            f.run(channel, null, args);
                            Log.w("alto", "Done");
                        }
                    } else {
                        FTPClient client = mFtpChannels.get(cacheKey);

                        if (client == null) {
                            callbackContext.error("Invalid clientId");
                            return;
                        }

                        synchronized (mFtpChannels) {
                            Log.w("alto", "Starting");
                            f.run(null, client, args);
                            Log.w("alto", "Done");
                        }
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
