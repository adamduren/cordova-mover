package com.alto.mover;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class Mover extends CordovaPlugin {
    final BaseMover mover = new BaseMover();

    private class ContextProxy implements BaseMover.IMoverInterface {
        private CallbackContext callbackContext;

        ContextProxy(CallbackContext callbackContext) {
            this.callbackContext = callbackContext;
        }

        @Override
        public void success(ArrayList<HashMap<String, String>> messages) {
            JSONArray encoded_messages;

            try {
                encoded_messages = new JSONArray();
                for (HashMap<String, String> message: messages) {
                    encoded_messages.put(new JSONObject(message));
                }
            } catch (Exception e) {
                encoded_messages = new JSONArray();
            }

            this.callbackContext.success(encoded_messages);
        }

        @Override
        public void success(String message) {
            this.callbackContext.success(message);
        }

        @Override
        public void success() {
            this.callbackContext.success();
        }

        @Override
        public void error(String message) {
            this.callbackContext.error(message);
        }
    }

    @Override
    public boolean execute(final String action, final String rawArgs, final CallbackContext callbackContext) throws JSONException {
        Log.i("Wimsy", "Executing action: " + action);

        ContextProxy contextProxy = new ContextProxy(callbackContext);
        final JSONArray argsArray = new JSONArray(rawArgs);
        final JSONObject args = new JSONObject(argsArray.getString(0));
        if (action.equals("testConnection")) {
            this.testConnection(args, contextProxy);
        } else if (action.equals("connect")) {
            this.connect(args, contextProxy);
        } else if (action.equals("disconnect")) {
            this.disconnect(args, contextProxy);
        } else if (action.equals("put")) {
            this.put(args, contextProxy);
        } else if (action.equals("rm")) {
            this.rm(args, contextProxy);
        }  else if (action.equals("rmdir")) {
            this.rmdir(args, contextProxy);
        }  else if (action.equals("ls")) {
            this.ls(args, contextProxy);
        } else {
            return false;
        }

        return true;
    }

    private void testConnection(final JSONObject args, final BaseMover.IMoverInterface callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                mover.testConnection(args, callbackContext);
            }
        });
    }


    private void connect(final JSONObject args, final BaseMover.IMoverInterface callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                mover.connect(args, callbackContext);
            }
        });
    }

    private void disconnect(JSONObject args, BaseMover.IMoverInterface callbackContext) {
        mover.disconnect(args, callbackContext);
    }

    private void put(final JSONObject args, final BaseMover.IMoverInterface callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                mover.put(args, callbackContext);
            }
        });
    }

    private void rm(final JSONObject args, final BaseMover.IMoverInterface callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                mover.rm(args, callbackContext);
            }
        });
    }

    private void rmdir(final JSONObject args, final BaseMover.IMoverInterface callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                mover.rmdir(args, callbackContext);
            }
        });
    }

    private void ls(final JSONObject args, final BaseMover.IMoverInterface callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                mover.ls(args, callbackContext);
            }
        });
    }
}
