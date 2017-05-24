//
//  RNTKSQLite.m
//  RNTKSQLite
//
//  Created by 程巍巍 on 5/10/17.
//  Copyright © 2017 程巍巍. All rights reserved.
//
#import "LiteSQLite.h"
#import "sqlite3.h"

struct {
    typeof(sqlite3_open_v2)* open;
    typeof(sqlite3_close_v2)* close;
    typeof(sqlite3_prepare_v2)* prepare;
    typeof(sqlite3_step)* step;
    typeof(sqlite3_finalize)* finalize;
    typeof(sqlite3_bind_parameter_count)* bind_parameter_count;
    typeof(sqlite3_bind_text)* bind_text;
    typeof(sqlite3_column_count)* column_count;
    typeof(sqlite3_column_double)* column_double;
    typeof(sqlite3_column_int64)* column_int64;
    typeof(sqlite3_column_name)* column_name;
    typeof(sqlite3_column_text)* column_text;
    typeof(sqlite3_column_type)* column_type;
    typeof(sqlite3_errstr)* errstr;
} SQLite ;

#define EXPORT_METHOD(method) RCT_EXPORT_METHOD(method resolve: (RCTPromiseResolveBlock) resolve reject: (RCTPromiseRejectBlock) reject)
#define kSS(st)[NSString stringWithFormat: @ "%@", @(st)]

@implementation LiteSQLite {
    __strong NSMutableDictionary * _dbm;
}

RCT_EXTERN void RCTRegisterModule(Class);
+ (NSString *)moduleName { return @"LiteSQLite"; }
+ (void)load {
    RCTRegisterModule(self);

    SQLite.open = sqlite3_open_v2;
    SQLite.close = sqlite3_close_v2;
    SQLite.prepare = sqlite3_prepare_v2;
    SQLite.step = sqlite3_step;
    SQLite.finalize = sqlite3_finalize;
    SQLite.bind_parameter_count = sqlite3_bind_parameter_count;
    SQLite.bind_text = sqlite3_bind_text;
    SQLite.column_count = sqlite3_column_count;
    SQLite.column_double = sqlite3_column_double;
    SQLite.column_int64 = sqlite3_column_int64;
    SQLite.column_name = sqlite3_column_name;
    SQLite.column_text = sqlite3_column_text;
    SQLite.column_type = sqlite3_column_type;
    SQLite.errstr = sqlite3_errstr;
}

- (instancetype) init {
    if (self = [super init]) {
        _dbm = [NSMutableDictionary new];
    }
    return self;
}

- (void) dealloc {
    for (NSString * dbf in _dbm.allKeys) {
        sqlite3 * dbi = [_dbm[dbf] pointerValue];
        if (dbi) sqlite3_close_v2(dbi);
    }
}

EXPORT_METHOD(open: (NSString * ) dbPath) {
    if ([dbPath hasPrefix: @ "./"] || ![dbPath hasPrefix: @ "/"]) {
        NSArray * paths = NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES);
        NSString * library = paths.firstObject;
        dbPath = [library stringByAppendingPathComponent: dbPath];
    }

    // 如果 文件夹不存在，则创建空文件
    {
        NSString * dir = [dbPath stringByDeletingLastPathComponent];
        if (![[NSFileManager defaultManager] fileExistsAtPath: dir]) {
            NSError * error = nil;
            [[NSFileManager defaultManager] createDirectoryAtPath: dir withIntermediateDirectories: YES attributes: nil error: & error];
            if (error) {
                reject(kSS(error.code), error.domain, error);
                return;
            }
        }
    }

    sqlite3 * ppdb = NULL;
    int status = SQLite.open(dbPath.UTF8String, & ppdb, SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE, NULL);
    if (SQLITE_OK != status) {
        NSString * msg = [NSString stringWithUTF8String: SQLite.errstr(status)];
        reject(kSS(status), msg, nil);
    } else {
        @synchronized (self.class) {
            static NSInteger flag = 0;
            NSValue* value = [NSValue valueWithPointer:ppdb];
            _dbm[@(++flag)] = value;
            resolve(@(flag));
        }
    }
}

EXPORT_METHOD(execute: (NSInteger)ppdbflag :(NSString * )sql :(NSArray * )params) {
    sqlite3 * ppdb = [_dbm[@(ppdbflag)] pointerValue];
    int status = SQLITE_OK;

    // step 1 compile sql
    sqlite3_stmt * stmt = NULL;
    status = SQLite.prepare(ppdb, sql.UTF8String, -1, & stmt, NULL);

    // step 2 bind params
    if (SQLITE_OK == status) {
        int count = MIN((int) params.count, SQLite.bind_parameter_count(stmt));
        for (int index = 0; index < count && SQLITE_OK == status; index++) {
            NSString * text = [NSString stringWithFormat: @ "%@", params[index]];
            status = SQLite.bind_text(stmt, index+1, text.UTF8String, -1, NULL);
        }
    }
    // step 3 execute sql
    id result = [NSMutableArray new];
    if (SQLITE_OK == status) {
        status = SQLite.step(stmt);
        if (SQLITE_ROW == status) {
            int columnCount = SQLite.column_count(stmt);
            int columnType[columnCount];

            NSMutableArray* row = [NSMutableArray new];

            // 获取 column name
            for (int index = 0; index < columnCount; index++) {
                NSString * name = [NSString stringWithUTF8String: SQLite.column_name(stmt, index)];
                [row addObject:name];
                columnType[index] = SQLite.column_type(stmt, index);
            }

            [result addObject:row];

            do {
                NSMutableArray* row = [NSMutableArray new];
                for (int index = 0; index < columnCount; index++) {
                    switch (columnType[index]) {
                        case SQLITE_INTEGER:
                            [row addObject:@(SQLite.column_int64(stmt, index))];
                            break;
                        case SQLITE_FLOAT:
                            [row addObject:@(SQLite.column_double(stmt, index))];
                            break;
                        default: {
                            const char* utf8str = SQLite.column_text(stmt, index);
                            [row addObject: utf8str ? [NSString stringWithUTF8String:utf8str] : NSNull.null];
                        }
                    }
                }
                [result addObject:row];
            }while(SQLITE_ROW == (status = SQLite.step(stmt)));
        }
    }
    SQLite.finalize(stmt);
    // step 4 callback
    if (SQLITE_DONE != status) {
        NSString * msg = [NSString stringWithUTF8String: SQLite.errstr(status)];
        reject(kSS(status), msg, nil);
    } else {
        resolve(result);
    }
}
EXPORT_METHOD(close:(NSInteger)ppdbflag) {
    sqlite3 * ppdb = [_dbm[@(ppdbflag)] pointerValue];
    int status = SQLite.close(ppdb);
    if (SQLITE_OK != status) {
        NSString * msg = [NSString stringWithUTF8String: SQLite.errstr(status)];
        reject(kSS(status), msg, nil);
    } else {
        [_dbm removeObjectForKey: @(ppdbflag)];
        resolve(nil);
    }
}
@end
