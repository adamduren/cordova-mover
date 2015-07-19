/*
 * Copyright 2013 Red Folder Consultancy Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

 /*
  * Declare a factory class which is used to create the background service "wrapper"
  */
function BackgroundServiceFactory() { }

BackgroundServiceFactory.prototype.create = function (serviceName) {
	var exec = require("cordova/exec");

	var BackgroundService = function (serviceName) {
		var ServiceName = serviceName;
		this.getServiceName = function() {
			return ServiceName;
		};
	};

	/**
	  * All methods attempt to return the following data in both the success and failure callbacks
	  * Front end development should take into account any all or all of these values may be null
	  *
	  * Following returned in the JSONObject:
	  *		Boolean Success - was the call a success
	  *		int ErrorCode - Error code if an error occurred, else will be zero
	  *		String ErrorMessage - Text representation of the error code
	  *		Boolean ServiceRunning - True if the Service is running
	  *		Boolean TimerEnabled - True if the Timer is enabled
	  *		Boolean RegisteredForBootStart - True if the Service is registered for boot start
	  *		JSONObject Configuration - A JSONObject of the configuration of the service (contents dependant on the service)
	  *		JSONObject LastestResult - A JSONObject of the last result of the service (contents dependant on the service)
	  *		int TimerMilliseconds - Milliseconds used by the background service if Timer enabled
	  *		Boolean RegisteredForUpdates - True if the Service is registered to send updates to the front-end
	  */

    function promiseHelper(method, params) {
    	var promise = new Promise(function(resolve, reject) {
	      	cordova.exec(
		        function() { resolve.apply(null, arguments); },
		        function() { reject.apply(null, arguments); },
		        'BackgroundServicePlugin',
		        method,
		        params
		      );
    		}
		);

    	return promise;
    }

	/**
	  * Starts the Service
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.startService = function() {
		return promiseHelper('startService', [this.getServiceName()]);
	};

	/**
	  * Stops the Service
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.stopService = function() {
		return promiseHelper('stopService', [this.getServiceName()]);
	};

	/**
	  * Enables the Service Timer
	  *
	  * @param milliseconds The milliseconds used for the timer
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.enableTimer = function(milliseconds) {
		return promiseHelper('enableTimer', [this.getServiceName(), milliseconds]);
	};

	/**
	  * Disabled the Service Timer
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.disableTimer = function() {
		return promiseHelper('disableTimer', [this.getServiceName()]);
	};

	/**
	  * Sets the configuration for the service
	  *
	  * @param configuration JSONObject to be sent to the service
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.setConfiguration = function(configuration) {
		return promiseHelper('setConfiguration', [this.getServiceName(), configuration]);
	};

	/**
	  * Registers the service for Boot Start
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.registerForBootStart = function() {
		return promiseHelper('registerForBootStart', [this.getServiceName()]);
	};

	/**
	  * Deregisters the service for Boot Start
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.deregisterForBootStart = function() {
		return promiseHelper('deregisterForBootStart', [this.getServiceName()]);
	};

	/**
	  * Get the current status of the service.
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.isRegisteredForBootStart = function() {
		return promiseHelper('isRegisteredForBootStart', [this.getServiceName()]);
	};


	/**
	  * Returns the status of the service
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.getStatus = function() {
		return promiseHelper('getStatus', [this.getServiceName()]);
	};

	/**
	  * Returns the doWork once
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.runOnce = function() {
		return promiseHelper('runOnce', [this.getServiceName()]);
	};

	/**
	  * Registers for doWork() updates
	  *
	  * @param successCallback The callback which will be called if the method is successful
	  * @param failureCallback The callback which will be called if the method encounters an error
	  */
	BackgroundService.prototype.registerForUpdates = function(successCallback, failureCallback) {
		return exec(	successCallback,
						failureCallback,
						'BackgroundServicePlugin',
						'registerForUpdates',
						[this.getServiceName()]);
	};

	/**
	  * Deregisters for doWork() updates
	  *
	  * @return Promise
	  */
	BackgroundService.prototype.deregisterForUpdates = function() {
		return promiseHelper('deregisterForUpdates', [this.getServiceName()]);
	};

	var backgroundService = new BackgroundService(serviceName);
	return backgroundService;
};

module.exports = new BackgroundServiceFactory();
