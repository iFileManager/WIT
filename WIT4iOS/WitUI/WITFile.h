//
//  WITFile.h
//  Wit
//
//  Created by Aqua on 13-7-3.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "GCDAsyncSocket.h"
#import "WITUser.h"

@interface WITFile : NSObject               //传输文件
{
    NSString *filepath, *filename;          //绝对路径，短文件名
        
    unsigned type, port;                    //文件类型，发送方绑定的端口号
    unsigned long size, completedSize;      //大小，已传输大小
    
    BOOL started, terminated, closed;       //是否开始，是否异常终止，是否关闭
    
    WITUser *theUser;                       //对方用户
    
    GCDAsyncSocket *socket;                 //套接字
    NSStream *stream;                       //数据流
}

@property (readonly) unsigned type;         //类型
@property (readwrite) unsigned port;        //端口号

@property (readonly) NSString *filename;    //文件名
@property (readonly) NSString *filepath;    //绝对路径

@property (readonly) unsigned long size;    //大小
@property (readonly) unsigned long completedSize;//已传输大小

@property (readonly) float percent;         //百分比

- (id) initWithUser:(WITUser *) user;       //初始化，包括对方用户

- (void) start;                             //开始传输
- (void) terminate;                         //异常终止
- (void) close;                             //关闭

- (BOOL) isStarted;                         //是否开始
- (BOOL) isTerminated;                      //是否异常终止

- (BOOL) isCompleted;                       //是否完成
- (BOOL) isOverflowed;                      //是否数据溢出（接收的大小多余文件本身大小）

- (void) notifyFilelistChange;              //通知文件列表改变

@end
