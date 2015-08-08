package com.alto.mover;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.UUID;


public class Mover extends CordovaPlugin {
    HashMap<String, ChannelSftp> channels = new HashMap();


    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("testConnection") || action.equals("connect")) {
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
            return true;
        } else if (action.equals("disconnect")) {
            this.disconnect(args.getString(0), callbackContext);
            return true;
        } else if (action.equals("cd")) {
            this.cd(args.getString(0), args.getString(1), callbackContext);
            return true;
        } else if (action.equals("pwd")) {
            this.pwd(args.getString(0), callbackContext);
            return true;
        } else if (action.equals("put")) {
            this.put(args.getString(0), args.getString(1), args.getString(2), args.getBoolean(3), callbackContext);
        } else if (action.equals("mkdir")) {
            this.mkdir(args.getString(0), args.getString(1), args.getBoolean(2), callbackContext);
        }  else if (action.equals("stat")) {
            this.stat(args.getString(0), args.getString(1), callbackContext);
        }

        return false;
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

    private void connect(String user, String password, String host, int port, CallbackContext callbackContext) {
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

    private void put(String channelId, String name, String data, boolean ensurePath, CallbackContext callbackContext){
        ChannelSftp channel = channels.get(channelId);

        if (channel == null) {
            callbackContext.error("Invalid channelId");
            return;
        }

        try {
            if (ensurePath) {
                _ensurePath(channelId, name, true);
            }

            OutputStream output = channel.put(name);
            output.write(data.getBytes());
            output.close();
            callbackContext.success(channel.pwd());
        } catch (Exception e){
            callbackContext.error(e.getMessage());
        }
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


}
