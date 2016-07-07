# Cordova Mover
A Cordova library to support common SFTP / FTP operations

This was written for my personal project [Wimsy](http://wimsy.com). There's a lot of remaining work that could be done to the library. If you want to contribute read below on Contributing and Future Work.

## Installation
Until this is published to NPM install with:

`cordova plugin add https://github.com/adamduren/cordova-mover.git#master`

## Usage
```javascript

// Creates a connection object and calls connect
// returns a promise
const connection = window.mover.connectionFactory(
  username,
  password,
  host,
  port,
  protocol);

// It's important to cleanup your connections
connection
.then(connection => connection.ls('/path/to/folder'))
.then(data => {
  console.log(data); // Do some work
  connection.disconnect();
});

// Put text
connection
.then(connection => {
  const ensurePath = true; // Creates folders along the way
  return connection.put('/path/to/file', { type: 'string', data: 'some string' }, ensurePath);
});

// Put file
connection
.then(connection => {
  const ensurePath = true; // Creates folders along the way
  return connection.put('/path/to/file', { type: 'url', data: '/local/path' }, ensurePath);
});

// Remove file
connection
.then(connection => connection.rm('/path/to/file'));

// Remove folder
connection
.then(connection => connection.rmdir('/path/to/folder'));


// currently only lists folders
connection
.then(connection => connection.ls('/path/to/folder'));
.then(data => console.log(data));

// TODO: Add other examples

```

## Contributing
 Issues and Pull Requests are welcome. I will do my best to respond in a timely manner. Please provide as much information as possible when creating an issue. Issues with Pull Requests are more likely to be addressed first.

## Future Work
  * Release to NPM
  * Usage Documentation and Examples
  * Documentation
  * Automated tests
  * iOS support

### iOS Support
If anyone has experience with iOS development and or SFTP/FTP libraries I'd greatly appreciate the help. I originally experimented with using a Java transpiler with J2ObjcC to create the iOS libraries. It worked for FTP but not SFTP since the Java crypto libraries were not supported for transpiling.
