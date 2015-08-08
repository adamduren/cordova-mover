/*global cordova, module, Promise*/

"use strict";

var className = 'Mover';

function promiseHelper(method, params) {
  params = params || [];

  var promise = new Promise(function(resolve, reject) {
      cordova.exec(
        function() { resolve.apply(null, arguments); },
        function() { reject.apply(null, arguments); },
        className,
        method,
        params
      );
    }
  );

  return promise;
}

function connectionFactory(user, password, host, port) {
  var con = new Connection();
  return con.connect(user, password, host, port);
}

function Connection() {}

Connection.prototype.connect = function connect(user, password, host, port) {
  var promise =  promiseHelper(
    'connect',
    [{
      user: user,
      password: password,
      host: host,
      port: port,
    }]
  );
  var _this = this;

  promise.then(function(key) {
    _this.key = key;
  });

  return promise;
};

Connection.prototype.disconnect = function disconnect() {
  var promise = promiseHelper('disconnect', [this.key]);
  var _this = this;

  promise.then(function() {
    _this.key = null;
  });

  return promise;
};

Connection.prototype.cd = function cd(path) {
  var promise = promiseHelper('cd', [this.key, path]);
  var _this;

  promise.then(function(cwd) {
    _this.cwd = cwd;
  });

  return promise;
};

Connection.prototype.pwd = function pwd() {
  if (this.cwd) {
    return Promise.resolve(this.cwd);
  }

  return promiseHelper('pwd', [this.key]);
};

Connection.prototype.put = function put(name, data, ensurePath) {
  ensurePath = !!ensurePath;

  if (typeof data === 'object') {
    data = JSON.stringify(data);
  }
  return promiseHelper('put', [this.key, name, data, ensurePath]);
};

Connection.prototype.stat = function put(path) {
  return promiseHelper('stat', [this.key, path]);
};

Connection.prototype.mkdir = function mkdir(path, recursive) {
  recursive = !!recursive;
  return promiseHelper('mkdir', [this.key, path, recursive]);
};

module.exports = {
  testConnection: function (user, password, host, port) {
    return promiseHelper(
      'testConnection',
      [{
        user: user,
        password: password,
        host: host,
        port: port,
      }]
    );
  },
  Connection: Connection,
  connectionFactory: connectionFactory,

};
