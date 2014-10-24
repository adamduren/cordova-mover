package com.alto.mover;

import java.io.OutputStream;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.jcraft.jsch.*;


public class Mover extends CordovaPlugin {
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("hello")) {
            String host = args.getString(0);
            String password = args.getString(1);
            this.hello(host, password, callbackContext);
            return true;
        }
        return false;
    }

    private void hello(String host, String password, CallbackContext callbackContext) {
        try {
            JSch jsch = new JSch();
            int port = 22;

            String user = host.substring(0, host.indexOf('@'));
            host = host.substring(host.indexOf('@') + 1);

            Session session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
            OutputStream output = channel.put("test.txt");

            output.write("testing".getBytes());
            output.close();

            channel.exit();

            callbackContext.success("Yay");
        } catch (Exception e) {
            callbackContext.success(e.getMessage());
        }
    }
}
