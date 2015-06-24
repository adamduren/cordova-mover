package com.alto.mover;

import java.io.OutputStream;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.jcraft.jsch.*;


public class Mover extends CordovaPlugin {
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("testConnection")) {
            JSONObject hostConfig = args.getJSONObject(0);
            String user = hostConfig.getString("user");
            String password = hostConfig.getString("password");
            String host = hostConfig.getString("host");
            int port = hostConfig.optInt("port", 22);
            this.testConnection(user, password, host, port, callbackContext);
            return true;
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
            channel.exit();

            callbackContext.success();
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
    }
}
