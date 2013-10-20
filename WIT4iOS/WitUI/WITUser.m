//
//  WITUser.m
//  Wit
//
//  Created by Aqua on 13-7-3.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITUser.h"
#import "WITConstants.h"
#import "WITUtils.h"

@implementation WITUser

@synthesize userId;
@synthesize ipAddr;
@synthesize username;

- (id) initWithPacket:(NSData *) packet     //根据数据包创建用户
{
    self = [super init];
    
    if (self)
    {
        [self applyPacket:packet];          //直接调用函数
    }
    
    return self;
}

- (void) applyPacket:(NSData *) packet      //将数据包应用在用户上
{
    Byte *buffer, *bytes;
    
    buffer = (Byte *) [packet bytes];
    
    bytes = (Byte *) malloc(ID_LEN);        //ID
    memcpy(bytes, buffer + ID_START, ID_LEN);
    userId = [WITUtils convertBytes2UnsignedLong:bytes];
    free(bytes);
        
    bytes = (Byte *) malloc(PACKET_SIZE - NAME_START);
    memcpy(bytes, buffer + NAME_START, PACKET_SIZE - NAME_START);
    username = [WITUtils convertBytes2String:bytes];
    free(bytes);                            //用户名
    
    bytes = (Byte *) malloc(IP_LEN);
    memcpy(bytes, buffer + IP_START, IP_LEN);
    ipAddr = [WITUtils convertBytes2IPString:bytes];
    free(bytes);                            //IP
    
    timestamp = [[[NSDate alloc] init] timeIntervalSince1970];
}                                           //标记当前时间戳

- (BOOL) isTimeout                          //是否超时
{
    long long now = [[[NSDate alloc] init] timeIntervalSince1970];
    return now - timestamp > TIMEOUT;       //和当前时间比较
}

- (BOOL) isEqual:(id)object                 //是否相等
{
    return object != nil && [object isKindOfClass:[WITUser class]] && userId == ((WITUser *) object).userId;
}                                           //比较ID

- (NSString *) description                  //转为字符串输出
{
    NSString *ret = [NSString stringWithFormat:@"%lu\t%@\t%@\t%llu\n", userId, username, ipAddr, timestamp];
    return ret;
}

@end
