//
//  WITFile.m
//  Wit
//
//  Created by Aqua on 13-7-3.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITFile.h"
#import "WITConstants.h"

@implementation WITFile

@synthesize type;                               //实现各属性
@synthesize port;

@synthesize filename;
@synthesize filepath;

@synthesize size;
@synthesize completedSize;

- (id) initWithUser:(WITUser *) user;           //根据用户创建文件对象
{
    self = [super init];
    
    if (self)
    {
        started = terminated = closed = NO;     //未开始，未(异常)?终止
        size = completedSize = port = 0;
        theUser = user;
    }
    
    return  self;
}

- (float) percent                               //百分比
{
    if (size == 0)
        return 0.0;
    else
        return (completedSize + 0.0) / size;
}

- (void) start                                  //开始传输
{
    started = YES;
    
    dispatch_queue_t socketQueue = dispatch_queue_create("socketQueue", NULL);
    socket = [[GCDAsyncSocket alloc] initWithDelegate:self delegateQueue:socketQueue];
}                                               //创建套接字，加入创建的消息队列

- (void) terminate                              //异常终止
{
    terminated = YES;
    [self close];
}

- (void) close                                  //终止
{
    if (closed) return;
    closed = YES;
    
    if (![self isTerminated] && [self isCompleted])
    {
        [self notifyTransmissionSuccess];       //文件传输成功
    }
    else
    {
        [self notifyTransmissionFailed];        //文件传输失败
    }
    
    if (socket != nil && [socket isConnected])  //关闭套接字
    {
        [socket disconnect];
    }
    
    if (stream != nil)                          //关闭数据流
    {
        [stream close];
        stream = nil;
    }
}

- (BOOL) isStarted                              //是否已启动
{
    return started;
}

- (BOOL) isTerminated                           //是否已异常终止
{
    return terminated;
}

- (BOOL) isCompleted                            //是否完成
{
    return size == completedSize;
}

- (BOOL) isOverflowed                           //是否数据过溢
{
    return size < completedSize;
}

- (BOOL) isEqual:(id)object                     //判断文件名是否相等
{
    return object != nil
    && [object isKindOfClass:[WITFile class]]
    && [filename isEqualToString:((WITFile *) object).filename];
}

- (NSString *) description                      //转为字符串输出
{
    NSString *ret = [NSString stringWithFormat:@"%@\t%u\t%lu\t%u\n", filepath, type, size, port];
    return ret;
}

- (void) notifyTransmissionFailed               //通知传输失败
{
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    [center postNotificationName:NOTIFY_TRANS_FAILED
                          object:self
                        userInfo:[NSDictionary dictionaryWithObject:self
                                                             forKey:@"file"]];
}

- (void) notifyTransmissionSuccess              //通知传输成功
{
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    [center postNotificationName:NOTIFY_TRANS_SUCCESS
                          object:self
                        userInfo:[NSDictionary dictionaryWithObject:self
                                                             forKey:@"file"]];
}

- (void) notifyFilelistChange                   //通知文件列表改变
{
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    [center postNotificationName:NOTIFY_FILELIST_CHANGE object:self];
}

- (void) socketDidDisconnect:(GCDAsyncSocket *)sock withError:(NSError *)err;
{                                               //连接被断开
    [self close];                               //关闭
}

@end
