/* global cordova, module, Promise*/
/* jshint esnext:true, browser:true */

"use strict";

var className = 'Mover';

function promiseHelper(method, data) {
  data = JSON.stringify(data || {});

  var promise = new Promise(function(resolve, reject) {
    try {
      cordova.exec(
        function() { resolve.apply(null, arguments); },
        function() { reject.apply(null, arguments); },
        className,
        method,
        [data]
      );
    } catch (e) {
      reject(e);
    }
  });

  return promise;
}

function connectionFactory(user, password, host, port, protocol) {
  var con = new Connection();
  return con.connect(user, password, host, port, protocol);
}

function Connection() {}

Connection.prototype.connect = function connect(user, password, host, port, protocol) {
  var promise =  promiseHelper('connect', {
    user: user,
    password: password,
    host: host,
    port: port,
    protocol: protocol,
  });

  var _this = this;
  _this.protocol = protocol;

  return promise.then(function(key) {
    _this.key = key;
    return _this;
  });
};

Connection.prototype.disconnect = function disconnect() {
  var promise = promiseHelper('disconnect', {
    key: this.key,
    protocol: this.protocol,
  });
  var _this = this;

  promise.then(function() {
    _this.key = null;
  });

  return promise;
};

Connection.prototype.put = function put(name, data, ensurePath) {
  ensurePath = !!ensurePath;

  return promiseHelper('put', {
    key: this.key,
    protocol: this.protocol,
    name: name,
    dataContainer: data,
    ensurePath: ensurePath,
  });
};

Connection.prototype.rm = function rm(name) {
  return promiseHelper('rm', {
    key: this.key,
    protocol: this.protocol,
    name: name,
  });
};

Connection.prototype.rmdir = function rmdir(name) {
  return promiseHelper('rmdir', {
    key: this.key,
    protocol: this.protocol,
    name: name,
  });
 };

 Connection.prototype.ls = function ls(name) {
   return promiseHelper('ls', {
     key: this.key,
     protocol: this.protocol,
     name: name,
   });
  };

module.exports = {
  testConnection: function (user, password, host, port, protocol) {
    return promiseHelper('testConnection', {
      user: user,
      password: password,
      host: host,
      port: port,
      protocol: protocol,
    });
  },
  Connection: Connection,
  connectionFactory: connectionFactory,
};
