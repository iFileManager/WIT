//
//  WITInFile.m
//  Wit
//
//  Created by Aqua on 13-7-3.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITInFile.h"
#import "WITConstants.h"
#import "WITUtils.h"

@implementation WITInFile

- (id) initWithFile:(NSString *) _filepath ofType:(unsigned) _type toUser:(WITUser *) user
{
    self = [super initWithUser:user];               //创建基类对象
    
    if (self)
    {
        filepath = _filepath;
        type = _type;
        filename = [filepath lastPathComponent];    //截取短文件名
        
        NSFileManager *fm = [NSFileManager defaultManager];
        
        NSError *err = nil;
        NSDictionary *attributes = [fm attributesOfItemAtPath:filepath error:&err];
        
        if (err)
            [self terminate];
        else
            size = [[attributes objectForKey:NSFileSize] unsignedLongValue];
    }                                               //获取文件大小
    
    return self;
}

- (NSData *) packet                                 //产生数据包
{
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    NSNumber *userIdNum = [defaults objectForKey:DEFAULT_USERID];
    unsigned long userId = [userIdNum unsignedLongValue];
    
    Byte *buffer, *bytes;
    
    buffer = (Byte *)malloc(PACKET_SIZE);
    
    memset(buffer, 0, PACKET_SIZE);
    
    memcpy(buffer, HEADER, RESERVED_LEN);           //头部
    buffer[TAG_POS] = [self isStarted] ? SEND_FILE : SEND_FILENAME;//标志
    
    bytes = [WITUtils convertUnsignedLong2Bytes:userId];  
    memcpy(buffer + ID_START, bytes, ID_LEN);       //ID
    free(bytes);
    
    bytes = [WITUtils convertUnsigned2Bytes:port];  
    memcpy(buffer + PORT_START, bytes, PORT_LEN);   //端口号
    free(bytes);
    
    bytes = [WITUtils convertUnsigned2Bytes:type];  
    memcpy(buffer + TYPE_START, bytes, TYPE_LEN);   //类型
    free(bytes);
    
    bytes = [WITUtils convertUnsignedLong2Bytes:size];  
    memcpy(buffer + SIZE_START, bytes, SIZE_LEN);   //大小
    free(bytes);
    
    bytes = [WITUtils convertString2Bytes:filename withMaxLength:PACKET_SIZE - FILENAME_START];
    memcpy(buffer + FILENAME_START, bytes, PACKET_SIZE - FILENAME_START);
    free(bytes);                                    //短文件名
    
    NSData *ret = [[NSData alloc] initWithBytes:buffer length:PACKET_SIZE];
    free(buffer);
    
    return ret;
}

- (void) start                                      //开始传输
{
    [super start];
    
    NSError *err = nil;
    if (![socket acceptOnPort:0 error:&err])        //绑定端口
    {
        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        [center postNotificationName:NOTIFY_RECEIVE_REQUEST
                              object:self
                            userInfo:[NSDictionary dictionaryWithObject:
                                      [NSString stringWithFormat:@"文件%@绑定端口失败", self.filename]
                                                                 forKey:@"msg"]];
        [self terminate];
    }
    else
    {
        port = [socket localPort];                  //设置端口号
    }
}

- (void) close                                      //关闭
{
    [super close];
    if (client != nil && [client isConnected])      //关闭对方连接时产生的套接字
        [client disconnect];
}

- (void) socket:(GCDAsyncSocket *)sock didAcceptNewSocket:(GCDAsyncSocket *)newSocket
{
    if (client != nil || ![[newSocket connectedHost] isEqualToString:theUser.ipAddr])
    {
        [newSocket disconnect];                     //已连接或者不是指定的用户，断开
    }
    else
    {
        client = newSocket;                         //设为连接对方的套接字
    }
    
    stream = [[NSInputStream alloc] initWithFileAtPath:filepath];
    [stream open];                                  //打开数据流
	
	[self send];                                    //开始发送
}

- (void) socket:(GCDAsyncSocket *)sock didWriteDataWithTag:(long)tag
{
    [self send];                                    //发送完成后继续发送
}

- (NSTimeInterval) socket:(GCDAsyncSocket *)sock
shouldTimeoutWriteWithTag:(long)tag
                  elapsed:(NSTimeInterval)elapsed
                bytesDone:(NSUInteger)length
{
    if ([self isCompleted])                         //超时，看是否发送完成
        [self close];
    else
        [self terminate];
    
    return 0.0;
}

- (void) send                                       //发送
{
    if ([self isTerminated] || [self isCompleted] || [self isOverflowed])
    {
        [self close];                               //异常终止/完成/溢出都会导致发送结束
        return;
    }
    
    NSInputStream *input = (NSInputStream *) stream;//转换为输入流
    
    Byte *buffer = (Byte *) malloc(STREAM_BUFFER_SIZE);
    unsigned readBytes = [input read:buffer maxLength:STREAM_BUFFER_SIZE];
    
    if (readBytes <= 0)                             //读取完成
    {
        free(buffer);
        
        [self close];
        return;
    }
    
    buffer = realloc(buffer, readBytes);            //重新调整大小
    NSData *data = [[NSData alloc] initWithBytes:buffer length:readBytes];
    free(buffer);
    
    [client writeData:data withTimeout:TIMEOUT tag:0];
    completedSize += readBytes;                     //写入套接字，设置超时时间，更新已完成大小
    
    [self notifyFilelistChange];                    //通知文件列表改变
}

@end
