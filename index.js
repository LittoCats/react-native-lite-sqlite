import {
  NativeModules
} from 'react-native';

import enqueue from 'react-native-lite-enqueue';

const Native = NativeModules.LiteSQLite;

export default class Database {

  constructor(dbPath) {
    const queue = {};
    let context;

    this.__proto__ = {
      get open() { return open; },
      get close() { return close; },
      get execute() { return execute; },
      get context() { return context; },
      get dbPath() { return dbPath; }
    };

    function open() {
      return enqueue(queue, function(){
        if (context) return new Promise.resolve(this);
        return Native.open(dbPath).then((database)=> {
          context = database;
          return this;
        })
      })
    }

    function close() {
      return enqueue(queue, function() {
        if (!context) return new Promise.resolve(context);
        return Native.close(context).then(function() {
          return context = undefined;
        })
      });
    }

    function execute(sql, ... args) {
      return enqueue(queue, function() {
        if (!context) throw new Error('Database has not been opened .');
        return Native.execute(context, sql, args).then(function(result){
          if (result.length) {
            const factory = createFactory(result.shift());
            result = result.map(factory);
          }
          return result;
        });
      })
    }
  }
}

function createFactory(columns) {
  columns = columns.map(function(column){ return column.split(/[^_a-zA-Z0-9\$]+/g).filter(function(column){return column.length;}) }).map(function (paths) {
    if (paths.length === 1) {
      return function (row, data) {
        row[paths[0]] = data;
      }
    }else{
      return function (row, data) {
        row = row[paths[0]] = row[paths[0]] || {};
        row[paths[1]] = data;
      }
    }
  });
  

  return function (data) {
    const row = {};
    columns.forEach(function(assign, index) {
      assign(row, data[index])
    })
    return row;
  };
}