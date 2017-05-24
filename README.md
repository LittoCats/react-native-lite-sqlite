# react-native-lite-sqlite

lightweight sqlite interface for react-native


### Usage

```
import SQLite from 'react-native-lite-sqlite'

db = new SQLite(${dbPath})

db.open();
db.execute('select * from sqlite_master');  // 
db.close()

```

Because open and close method serialized automitically, so you can write your code as sync program if you do not care the result;

### API


##### `SQLite`

  the Database constructor.

##### `open` ()=> promise

##### `execute` (sql, ... parametersToBind)=> promise

##### `open` ()=> promise
