package com.littocats.rntksqlite;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.database.DatabaseUtils.STATEMENT_ABORT;
import static android.database.DatabaseUtils.STATEMENT_ATTACH;
import static android.database.DatabaseUtils.STATEMENT_BEGIN;
import static android.database.DatabaseUtils.STATEMENT_COMMIT;
import static android.database.DatabaseUtils.STATEMENT_DDL;
import static android.database.DatabaseUtils.STATEMENT_OTHER;
import static android.database.DatabaseUtils.STATEMENT_PRAGMA;
import static android.database.DatabaseUtils.STATEMENT_SELECT;
import static android.database.DatabaseUtils.STATEMENT_UNPREPARED;
import static android.database.DatabaseUtils.STATEMENT_UPDATE;

/**
 * Created by Dragon-Li on 5/10/17.
 */

public class SQLiteModule extends ReactContextBaseJavaModule{

    private Map<String, Sqlite3> mReferencedObject = new HashMap<>();
    private String addRef(Sqlite3 o) {
        try {
            String s = (o.getClass().getName() + o.hashCode() + new Date().getTime());
            char hexDigits[]={'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
            byte[] btInput = s.getBytes();
            // 获得MD5摘要算法的 MessageDigest 对象
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            // 使用指定的字节更新摘要
            mdInst.update(btInput);
            // 获得密文
            byte[] md = mdInst.digest();
            // 把密文转换成十六进制的字符串形式
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            String flag = new String(str);
            mReferencedObject.put(flag, o);
            return flag;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private Sqlite3 getRef(String flag)
    {
        return mReferencedObject.get(flag);
    }

    @Override
    protected void finalize() throws Throwable {
        for (String flag: mReferencedObject.keySet()) {
            mReferencedObject.get(flag).close();
            mReferencedObject.remove(flag);
        }
        super.finalize();
    }

    private void removeRef(String flag)
    {
        mReferencedObject.remove(flag);
    }


    public SQLiteModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "LiteSQLite";
    }

    /**
     *
     * @param dbPath 绝对路径
     * @param promise
     */
    @ReactMethod
    public void open(String dbPath, Promise promise)
    {
        try {
            File file = new File(dbPath);
            file.getParentFile().mkdirs();
            Sqlite3 sqlite3 = new Sqlite3(file.getAbsolutePath());

            promise.resolve(addRef(sqlite3));
        }catch (Exception e) {
            e.printStackTrace();
            promise.reject(e);
        }
    }

    @ReactMethod
    public void execute(String flag, String sql, ReadableArray params, Promise promise)
    {
        try {
            Sqlite3 sqlite3 = getRef(flag);
            Object result = sqlite3.executeSQL(sql, params);
            promise.resolve(result);
        }catch (Exception e) {
            Log.d(getName(), "" + sql);
            e.printStackTrace();
            promise.reject(e);
        }
    }

    @ReactMethod
    public void close(String flag, Promise promise)
    {
        try {
            Sqlite3 sqlite3 = getRef(flag);
            sqlite3.close();
            removeRef(flag);
            promise.resolve(null);
        }catch (Exception e) {
            e.printStackTrace();
            promise.reject(e);
        }
    }

    public class Sqlite3 {

        private String mPath;
        private SQLiteDatabase mDatabase;
        public Sqlite3(String dbpath) {
            this.mPath = dbpath;
            this.mDatabase = SQLiteDatabase.openOrCreateDatabase(dbpath, null);
        }

        public ReadableArray executeSQL(String sql, ReadableArray params) throws JSONException {
            List<String> binds = new ArrayList<>();
            for (int index = 0; index < params.size(); index++) {
                ReadableType type = params.getType(index);
                switch (type) {
//                    case Array: l.put(convert(array.getArray(index))); break;
//                    case Map: l.put(convert(array.getMap(index))); break;
                    case String: binds.add(params.getString(index)); break;
                    case Number: binds.add("" + params.getDouble(index)); break;
//                    case Boolean: binds.add(); break;
                    default: binds.add("");
                }
            }

            WritableArray results = new WritableNativeArray();

            int sqlType = DatabaseUtils.getSqlStatementType(sql);
            switch (sqlType) {
                case STATEMENT_PRAGMA:
                case STATEMENT_SELECT: {
                    Cursor cursor = mDatabase.rawQuery(sql, binds.toArray(new String[]{}));
                    if (cursor.getCount() > 0) {
                        WritableArray row = new WritableNativeArray();
                        int columnCount = cursor.getColumnCount();

                        for (int column = 0; column < columnCount; column++) {
                            row.pushString(cursor.getColumnName(column));
                        }
                        results.pushArray(row);

                        do {
                            cursor.moveToNext();
                            row = new WritableNativeArray();

                            for (int column = 0; column < columnCount; column++) {
                                int type = cursor.getType(column);
                                switch (type) {
                                    case Cursor.FIELD_TYPE_INTEGER: {
                                        long num = cursor.getLong(column);
                                        row.pushDouble(num);
                                    }break;
                                    case Cursor.FIELD_TYPE_FLOAT: {
                                        double num = cursor.getDouble(column);
                                        row.pushDouble(num);
                                    }break;
                                    default: {
                                        String str = cursor.getString(column);
                                        row.pushString(str);
                                    }break;
                                }
                            }
                            results.pushArray(row);
                        }while (!cursor.isLast());
                    }
                }break;
                default: {
                    mDatabase.execSQL(sql, binds.toArray(new String[]{}));
                }
            }

            return results;
        }

        public void close()
        {
            mDatabase.close();
        }
    }
}
