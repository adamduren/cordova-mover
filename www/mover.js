/*global cordova, module*/

var className = 'Mover';

module.exports = {
  testConnection: function (user, password, host, port) {
    var promise = new Promise(function(resolve, reject) {
      cordova.exec(
        function() { resolve(); },
        function(errorMessage) { reject(errorMessage); },
        className,
        'testConnection',
        [{
          user: user,
          password: password,
          host: host,
          port: port,
        }]
      );
    });

    return promise;
  }
};
