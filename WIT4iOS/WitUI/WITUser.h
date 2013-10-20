//
//  WITUser.h
//  Wit
//
//  Created by Aqua on 13-7-3.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import <Foundation/Foundation.h>

@interface WITUser : NSObject               //在线用户
{
    unsigned long long timestamp;           //时间戳，供删除
}

@property (readonly) unsigned long userId;  //用户ID
@property (readonly) NSString *ipAddr;      //IP地址
@property (readonly) NSString *username;    //用户名

- (id) initWithPacket:(NSData *) packet;    //通过数据包创建用户
- (void) applyPacket:(NSData *) packet;     //将数据包中内容更新到用户上

- (BOOL) isTimeout;                         //是否超时

@end
