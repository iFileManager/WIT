//
//  WITNetworkEnv.h
//  Wit
//
//  Created by Aqua on 13-7-1.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "WITSyncUdpSocket.h"
#import "WITService.h"
#import "WITUser.h"

@class WITService;

@interface WITNetworkEnv : NSObject
{
    WITService *service;                            //服务
    WITSyncUdpSocket *sendSocket, *recvSocket;         //异步UDP套接字
    
    unsigned long userId;                           //MAC地址生成的用户ID
    NSString *theUsername, *brdAddr, *ipAddr;       //用户名、广播和IP地址
    NSData *packet;                                 //用户数据包
    NSTimer *timer;                                 //定时器
}

@property (readwrite, copy) NSString *username;     //用户名

@property (readonly) NSData *packet;
@property (readonly) BOOL closed;                   //已关闭

- (void) close;                                     //停止服务

- (id) initWithService:(WITService *) service_;     //初始化

- (void) generatePacket;                            //生成数据包
- (void) send:(NSData *) data to:(WITUser *) user;  //单播

@end
