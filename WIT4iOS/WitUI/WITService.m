//
//  WITService.m
//  Wit
//
//  Created by Aqua on 13-7-2.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITService.h"
#import "WITConstants.h"
#import "WITUtils.h"
#import "WITInFile.h"
#import "WITOutFile.h"

static id serviceSingleton = nil;                       //单例

@implementation WITService

@synthesize sendDirectory;
@synthesize recvDirectory;

+ (void) initialize
{
    if (!serviceSingleton)
    {
        serviceSingleton = [[WITService alloc] init];
    }
}

+ (WITService *) mainService                            //获取单例
{
    return (WITService *) serviceSingleton;
}

- (NSArray *) userlist                                  //用户列表
{
    @synchronized(userMtx)
    {
        return [[userDict allValues] copy];             //哈希表中的值
    }
}

- (NSArray *) filelist                                  //传输文件列表
{
    NSMutableArray *array = [[NSMutableArray alloc] init];

    @synchronized(sendMtx)
    {
        [array addObjectsFromArray:[sendDict allValues]];
    }
    
    @synchronized(recvMtx)
    {
        [array addObjectsFromArray:[recvDict allValues]];
    }
    
    return [array copy];                                //两个哈希表值之并
}

- (void) setMyname:(NSString *) myname                  //我的名字
{
    env.username = myname;
}

- (NSString *) myname
{
    return env.username;
}

- (id) init                                             //初始化
{
    self = [super init];
    
    if (self)
    {
        NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
        NSString *documentDir = [paths objectAtIndex:0];
        
        sendDirectory = [documentDir stringByAppendingPathComponent:SEND_DIRECTORY];
        recvDirectory = [documentDir stringByAppendingPathComponent:RECV_DIRECTORY];
        
        NSFileManager *fm = [NSFileManager defaultManager];
        
        [fm createDirectoryAtPath:sendDirectory
      withIntermediateDirectories:YES
                       attributes:nil
                            error:nil];
        
        [fm createDirectoryAtPath:recvDirectory
      withIntermediateDirectories:YES
                       attributes:nil
                            error:nil];
        
        env = [[WITNetworkEnv alloc] initWithService:self];//初始化底层环境
        
        userDict = [[NSMutableDictionary alloc] init];
        userMtx = [[NSObject alloc] init];
        
        sendDict = [[NSMutableDictionary alloc] init];
        sendMtx = [[NSObject alloc] init];
        
        recvDict = [[NSMutableDictionary alloc] init];
        recvMtx = [[NSObject alloc] init];
        
        timer = [NSTimer scheduledTimerWithTimeInterval:REFRESH_PERIOD
                                                 target:self
                                               selector:@selector(scanTimeoutUser:)
                                               userInfo:nil
                                                repeats:YES];
                                                        //定期扫描超时用户
    }
    
    return self;
}

- (void) close                                          //关闭
{
    if ([self isConnected]) [self sendDisconnectRequest];//如果正在连接，发送断开通知
    [timer invalidate];                                 //停止扫描超时用户
    [env close];                                        //关闭底层环境
}

- (void) refreshIP                                      //刷新
{
    [env generatePacket];
}

- (void) decode:(NSData *) packet forUser:(unsigned long) userId;
{                                                       //解码
    Byte *buffer = (Byte *) [packet bytes];
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    
    WITUser *user;
    WITOutFile *outFile, *tmpFile;
    
    switch (buffer[TAG_POS])
    {
        case REG_USER:                                  //接到注册用户请求
            [self updateUser:userId withPacket:packet];
            break;
        case UNREG_USER:                                //接到退出注册用户请求
            [self removeUser:userId];
            break;
        case CONNECT:                                   //接到连接请求
            if (![self containsUser:userId]) return;    //检查是否在用户列表中
            user = [self getUser:userId];               //获取用户
            
            if ([self isConnected])                     //是否已连接
			{
                if ([self isConnectedToId:userId])
                    [self acceptConnectRequest:user];
				else
                    [self refuseConnectRequest:user];
				return;
			}
            
            [center postNotificationName:NOTIFY_RECEIVE_REQUEST
                                  object:self
                                userInfo:[NSDictionary dictionaryWithObject:user forKey:@"user"]];
                                                        //通知收到连接请求
			break;
        case ACCEPT_CONNECT:                            //对方接收连接
            if (![self isRequestingId:userId])          //检查是否是请求用户
            {
                [self refuseStranger:userId];
                return;
            }
            
            [self connect:requestUser];                 //连接对方
            [center postNotificationName:NOTIFY_REMOTE_ACCEPT_REQUEST object:self];
                                                        //通知连接请求被接受
            break;
        case REFUSE_CONNECT:                            //对方拒绝连接
            if (![self isRequestingId:userId]) return;  //检查是否是请求用户
            
            [self cancelConnectRequest];                //撤回连接请求
            [center postNotificationName:NOTIFY_REMOTE_REFUSE_REQUEST object:self];
                                                        //通知连接请求被拒绝
            break;
        case DISCONNECT:                                //对方断开连接
            if (![self isConnectedToId:userId]) return; //检查是否是连接用户
            
            [self disconnect];                          //断开连接
            [center postNotificationName:NOTIFY_DISCONNECT object:self];
                                                        //通知连接被断开
            break;
        case SEND_FILENAME:                             //收到对方发送的文件名
            if (![self isConnectedToId:userId])         //检查是否是连接用户
            {
                [self refuseStranger:userId];
                return;
            }
            
            [self receiveFileWithPacket:packet];        //填到接收列表
            
            break;
        case SEND_FILE:                                 //收到对方发送的文件
            if (![self isConnectedToId:userId])         //检查是否是连接用户
            {
                [self refuseStranger:userId];
                return;
            }
            
            tmpFile = [[WITOutFile alloc] initWithPacket:packet fromUser:connectUser];
            if (![self containsReceiveFile:tmpFile.filename]) return;
                                                        //检查是否在接收列表中
            outFile = [self getReceiveFile:tmpFile.filename];
            [outFile applyPacket:packet];               //更新文件信息，端口号
            [self startReceive:outFile];                //开始接收
            
            break;
        default:
            break;
    }
}

- (void) updateUser:(unsigned long) userId withPacket:(NSData *) packet
{                                                       //更新用户信息
    NSNumber *userIdNum = [NSNumber numberWithUnsignedLong:userId];
    WITUser *user;
    
    @synchronized(userMtx)
    {
        if ([[userDict allKeys] containsObject:userIdNum])
        {                                               //已有此用户
            user = [userDict objectForKey:userIdNum];
            [user applyPacket:packet];                  //更新之
        }
        else
        {
            user = [[WITUser alloc] initWithPacket:packet];
            [userDict setObject:user forKey:userIdNum]; //添加新用户
        }
    }
    
    [self notifyUserlistChange];                        //通知用户列表改变
}

- (void) removeUser:(unsigned long) userId              //删除用户
{
    NSNumber *userIdNum = [NSNumber numberWithUnsignedLong:userId];
    
    @synchronized(userMtx)
    {
        if ([[userDict allKeys] containsObject:userIdNum])
        {
            [userDict removeObjectForKey:userIdNum];            
        }
    }
                                                        //检查是否是请求的或连接的用户
    if ([self isRequestingId:userId]) [self cancelConnectRequest];
    if ([self isConnectedToId:userId]) [self disconnect];
    
    [self notifyUserlistChange];                        //通知用户列表改变
}

- (void) scanTimeoutUser:(NSTimer *) timer              //扫描超时用户
{
    NSMutableArray *array = [[NSMutableArray alloc] init];
    @synchronized(userMtx)
    {
        for (NSNumber *userIdNum in [userDict allKeys])
        {
            WITUser *user = [userDict objectForKey:userIdNum];
            if ([user isTimeout]) [array addObject:userIdNum];
        }                                               //记下所有超时用户ID
    }
    
    for (NSNumber *userIdNum in array)                  //删除超时用户
    {
        [self removeUser:[userIdNum unsignedLongValue]];
    }
}

- (void) setRequestUser:(WITUser *) user                //设置请求用户
{
    requestUser = user;
    [self notifyUserlistChange];                        //通知用户列表改变
}

- (BOOL) isRequesting                                   //是否正在请求用户
{
    return requestUser != nil;
}

- (BOOL) isRequesting:(WITUser *) user                  //是否正在请求指定用户
{
    return requestUser != nil && [requestUser isEqual:user];
}

- (BOOL) isRequestingId:(unsigned long) userId          //是否正在请求指定ID的用户
{
    return requestUser != nil && requestUser.userId == userId;
}

- (void) setConnectUser:(WITUser *) user                //设置连接用户
{
    connectUser = user;
    [self notifyUserlistChange];                        //通知用户列表改变
}

- (BOOL) isConnected                                    //是否正在连接用户
{
    return connectUser != nil;
}

- (BOOL) isConnectedTo:(WITUser *) user                 //是否正在连接指定用户
{
    return connectUser != nil && [connectUser isEqual:user];
}

- (BOOL) isConnectedToId:(unsigned long) userId         //是否正在连接指定ID的用户
{
    return connectUser != nil && connectUser.userId == userId;
}

- (void) connect:(WITUser *) user                       //连接用户
{
    [self setRequestUser:nil];                          //取消请求用户
    [self setConnectUser:user];                         //设置连接用户
    
    [self performSelectorInBackground:@selector(autoSend) withObject:nil];
}                                                       //开始发送文件

- (void) disconnect                                     //断开连接
{
    [self setConnectUser:nil];                          //取消连接用户
    
    @synchronized(sendMtx)
    {
        for (WITInFile *inFile in [sendDict allValues]) //逐个终止发送的文件
        {
            [inFile terminate];
        }
        [sendDict removeAllObjects];                    //清空发送文件列表
    }
    
    @synchronized(recvMtx)
    {
        for (WITOutFile *outFile in [recvDict allValues])//逐个终止接收的文件
        {
            [outFile terminate];
        }
        [recvDict removeAllObjects];                    //清空接收文件列表
    }
    
    [self notifyFilelistChange];                        //通知文件列表改变
}

- (void) sendConnectRequest:(WITUser *) user            //发送连接请求
{
    if (![self containsUser:user.userId]) return;       //必须已存在用户
    if ([self isConnected]) return;                     //不能已连接
    
    [self setRequestUser:user];                         //设置请求的用户
    
    NSData *newPacket = env.packet;
    
    Byte *buffer = (Byte *) [newPacket bytes];
    buffer[TAG_POS] = CONNECT;
    
    [env send:newPacket to:requestUser];                //单播连接包
}

- (void) cancelConnectRequest                           //撤回请求
{
    if (![self isRequesting]) return;
    
    [self setRequestUser:nil];
}

- (void) acceptConnectRequest:(WITUser *) user          //接收连接请求
{
    NSData *newPacket = env.packet;
    
    Byte *buffer = (Byte *) [newPacket bytes];
    buffer[TAG_POS] = ACCEPT_CONNECT;
    
    [env send:newPacket to:user];                       //单播回应
    
    [self connect:user];                                //连接到用户
}

- (void) refuseConnectRequest:(WITUser *) user          //拒绝连接请求
{
    NSData *newPacket = env.packet;
    
    Byte *buffer = (Byte *) [newPacket bytes];
    buffer[TAG_POS] = REFUSE_CONNECT;
    
    [env send:newPacket to:user];                       //单播回应
}

- (void) sendDisconnectRequest                          //断开连接
{
    if (![self isConnected]) return;                    //不能未连接
    
    NSData *newPacket = env.packet;
    
    Byte *buffer = (Byte *) [newPacket bytes];
    buffer[TAG_POS] = DISCONNECT;
    
    [env send:newPacket to:connectUser];                //单播断开通知
    
    [self disconnect];                                  //断开连接
    
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    [center postNotificationName:NOTIFY_DISCONNECT object:self];
}                                                       //通知已断开

- (void) refuseStranger:(unsigned long) userId          //拒绝非请求/连接用户的一些数据包
{
    if (![self containsUser:userId]) return;
    WITUser *user = [self getUser:userId];
    
    NSData *newPacket = env.packet;
    
    Byte *buffer = (Byte *) [newPacket bytes];
    buffer[TAG_POS] = DISCONNECT;
    
    [env send:newPacket to:user];                       //返回断开通知
}

- (void) notifyUserlistChange                           //通知用户列表更新
{
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    [center postNotificationName:NOTIFY_USERLIST_CHANGE object:self];
}

- (BOOL) containsUser:(unsigned long) userId            //用户是否在列表中
{
    NSNumber *userIdNum = [NSNumber numberWithUnsignedLong:userId];
    
    @synchronized(userMtx)
    {
        return [[userDict allKeys] containsObject:userIdNum];
    }
}

- (WITUser *) getUser:(unsigned long) userId            //从列表中获取用户
{
    NSNumber *userIdNum = [NSNumber numberWithUnsignedLong:userId];
    
    @synchronized(userMtx)
    {
        return [userDict objectForKey:userIdNum];
    }
}

- (BOOL) containsSendFile:(NSString *) filename         //文件是否在发送列表中
{
    @synchronized(sendMtx)
    {
        return [[sendDict allKeys] containsObject:filename];
    }
}

- (WITInFile *) getSendFile:(NSString *) filename       //从发送列表中获取文件
{
    @synchronized(sendMtx)
    {
        return [sendDict objectForKey:filename];
    }
}

- (void) removeSendFile:(NSString *) filename           //从发送列表中删除文件
{
    @synchronized(sendMtx)
    {
        [sendDict removeObjectForKey:filename];
    }
    [self notifyFilelistChange];                        //通知文件列表更新
}
                  
- (BOOL) containsReceiveFile:(NSString *) filename      //文件是否在接收列表中
{
    @synchronized(recvMtx)
    {
        return [[recvDict allKeys] containsObject:filename];
    }
}

- (WITOutFile *) getReceiveFile:(NSString *) filename   //从接收列表中获取文件
{
    @synchronized(recvMtx)
    {
        return [recvDict objectForKey:filename];
    }
}

- (void) removeReceiveFile:(NSString *) filename        //从接收列表中删除文件
{
    @synchronized(recvMtx)
    {
        [recvDict removeObjectForKey:filename];
    }
    [self notifyFilelistChange];                        //通知文件列表更新
}

- (BOOL) sendFile:(NSString *) filepath ofType:(unsigned) type
{                                                       //将文件加入发送列表
    if (![self isConnected])                            //判断是否连接
    {
        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        [center postNotificationName:NOTIFY_RECEIVE_REQUEST
                              object:self
                            userInfo:[NSDictionary dictionaryWithObject:@"未连接"
                                                                 forKey:@"msg"]];
        return NO;
    }
    
    WITInFile *inFile = [[WITInFile alloc] initWithFile:filepath ofType:type toUser:connectUser];
    if ([self containsSendFile:inFile.filename])        //判断是否已有同短文件名文件发送
    {
        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        [center postNotificationName:NOTIFY_RECEIVE_REQUEST
                              object:self
                            userInfo:[NSDictionary dictionaryWithObject:
                                      [NSString stringWithFormat:@"文件名%@已在发送列表中", inFile.filename]
                                                                 forKey:@"msg"]];
        return NO;
    }
    
    @synchronized(sendMtx)                              //加入传输列表
    {
        [sendDict setObject:inFile forKey:inFile.filename];        
    }
    
    [env send:inFile.packet to:connectUser];
    [self notifyFilelistChange];                        //通知文件列表更新
    
    return YES;
}

- (void) receiveFileWithPacket:(NSData *) packet        //将文件加入接收列表
{
    WITOutFile *outFile = [[WITOutFile alloc] initWithPacket:packet fromUser:connectUser];
    @synchronized(recvMtx)                              //加入传输列表
    {
        [recvDict setObject:outFile forKey:outFile.filename];        
    }
    [self notifyFilelistChange];                        //通知文件列表更新
}

- (void) autoSend                                       //自动发送
{
    while ([self isConnected])                          //已连接
    {
        NSArray *array;
        @synchronized(sendMtx)
        {
            array = [sendDict allValues];
        }
        
        for (WITInFile *inFile in array)                //发送各个文件
        {
            [self startSend:inFile];
        }
        
        [NSThread sleepForTimeInterval:0.5];            //睡眠半秒
    }
}

- (void) startSend:(WITInFile *) inFile                 //开始发送
{
    if ([inFile isStarted] || [inFile isTerminated]) return;
    
    [inFile start];
    [env send:inFile.packet to:connectUser];            //发送数据包
    
    [self notifyFilelistChange];                        //通知文件列表更新
}

- (void) startReceive:(WITOutFile *) outFile            //开始接收
{
    if ([outFile isStarted] || [outFile isTerminated]) return;
    
    [outFile start];
    
    [self notifyFilelistChange];                        //通知文件列表更新
}

- (void) notifyFilelistChange                           //通知文件列表更新
{
    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    [center postNotificationName:NOTIFY_FILELIST_CHANGE object:self];
}

@end
