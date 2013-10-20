//
//  WITService.h
//  Wit
//
//  Created by Aqua on 13-7-2.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "WITNetworkEnv.h"
#import "WITUser.h"

@class WITNetworkEnv;

@interface WITService : NSObject                        //服务
{
    WITNetworkEnv *env;                                 //底层环境
    WITUser *requestUser, *connectUser;                 //想连接的用户、实际连接的用户
    
    NSMutableDictionary *userDict, *sendDict, *recvDict;//用户、发送文件、接收文件
    NSObject *userMtx, *sendMtx, *recvMtx;              //用户、发送文件、接收文件互斥体
    NSTimer *timer;                                     //计时器，扫描超时用户
}

@property (readwrite, copy) NSString *myname;           //我的名字
@property (readonly) NSArray *userlist;                 //用户列表
@property (readonly) NSArray *filelist;                 //文件列表
@property (readonly) NSString *sendDirectory;           //发送目录
@property (readonly) NSString *recvDirectory;           //接收目录

+ (WITService *) mainService;                           //单例

- (void) close;                                         //关闭服务
- (void) refreshIP;                                     //刷新IP  
- (void) decode:(NSData *) packet forUser:(unsigned long) userId;
                                                        //解码
- (BOOL) isRequesting;                                  //是否正在请求
- (BOOL) isRequesting:(WITUser *) user;                 //是否正在请求某用户

- (BOOL) isConnected;                                   //是否已连接
- (BOOL) isConnectedTo:(WITUser *) user;                //是否已连接到某用户

- (void) sendConnectRequest:(WITUser *) user;           //对用户发送连接请求
- (void) cancelConnectRequest;                          //撤回连接请求

- (void) acceptConnectRequest:(WITUser *) user;         //接收连接请求
- (void) refuseConnectRequest:(WITUser *) user;         //拒绝连接请求

- (void) sendDisconnectRequest;                         //发送断开连接通知（非请求）

- (BOOL) sendFile:(NSString *) filepath ofType:(unsigned) type;
                                                        //发送文件
@end
