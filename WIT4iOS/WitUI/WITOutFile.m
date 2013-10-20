//
//  WITOutFile.m
//  Wit
//
//  Created by Aqua on 13-7-3.
//  Copyright (c) 2013年 __MyCompanyName__. All rights reserved.
//

#import "WITOutFile.h"
#import "WITConstants.h"
#import "WITService.h"
#import "WITUtils.h"

@implementation WITOutFile

- (id) initWithPacket:(NSData *) packet fromUser:(WITUser *) user
{                                               //根据数据包创建文件对象
    self = [super initWithUser:user];
    
    if (self)
    {
        [self applyPacket:packet];              //调用应用数据包函数
    }
    
    return self;
}

- (void) applyPacket:(NSData *) packet          //应用数据包
{
    Byte *buffer, *bytes;
    
    buffer = (Byte *) [packet bytes];
    
    bytes = (Byte *) malloc(PORT_LEN);
    memcpy(bytes, buffer + PORT_START, PORT_LEN);
    port = [WITUtils convertBytes2Unsigned:bytes];
    free(bytes);                                //端口号
    
    bytes = (Byte *) malloc(TYPE_LEN);
    memcpy(bytes, buffer + TYPE_START, TYPE_LEN);
    type = [WITUtils convertBytes2Unsigned:bytes];
    free(bytes);                                //类型
    
    bytes = (Byte *) malloc(SIZE_LEN);
    memcpy(bytes, buffer + SIZE_START, SIZE_LEN);
    size = [WITUtils convertBytes2UnsignedLong:bytes];
    free(bytes);                                //大小
    
    bytes = (Byte *) malloc(PACKET_SIZE - FILENAME_START);
    memcpy(bytes, buffer + FILENAME_START, PACKET_SIZE - FILENAME_START);
    filename = [WITUtils convertBytes2String:bytes];
    free(bytes);                                //文件名
    
    filepath = [[WITService mainService].recvDirectory stringByAppendingPathComponent:filename];
}                                               //完整路径是沙盒中的Documents/receive目录下

- (void) start                                  //开始传输
{
    [super start];
    
    NSError *err = nil;
    if(![socket connectToHost:theUser.ipAddr onPort:port error:&err])
    {                                           //连接到对方服务器
        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        [center postNotificationName:NOTIFY_RECEIVE_REQUEST
                              object:self
                            userInfo:[NSDictionary dictionaryWithObject:
                                      [NSString stringWithFormat:@"文件%@连接对方失败", self.filename]
                                                                 forKey:@"msg"]];
        [self terminate];
    }
}

- (void) socket:(GCDAsyncSocket *)sock didConnectToHost:(NSString *)host port:(uint16_t)port
{                                               //连接成功
    stream = [[NSOutputStream alloc] initToFileAtPath:filepath append:NO];
    [stream open];                              //创建并打开数据流
    [socket readDataWithTimeout:TIMEOUT tag:0]; //开始读套接字
}

- (void) socket:(GCDAsyncSocket *)sock didReadData:(NSData *)data withTag:(long)tag
{
    [self receive:data];                        //读取成功，调用函数
}

- (NSTimeInterval) socket:(GCDAsyncSocket *)sock
 shouldTimeoutReadWithTag:(long)tag
                  elapsed:(NSTimeInterval)elapsed
                bytesDone:(NSUInteger)length
{
    if ([self isCompleted])                     //超时，判断是否完成
        [self close];
    else
        [self terminate];
    
    return 0.0;
}

- (void) receive:(NSData *) data                //处理读取的数据
{
    if ([self isTerminated])                    //异常终止
    {
        [self close];
        return;
    }
    
    NSOutputStream *output = (NSOutputStream *) stream;
    
    int completed, len, writeBytes;
    
    Byte *buffer = (Byte *) [data bytes];
    
    len = [data length];
    completed = 0;
    
    while (completed < len)                     //将读取的数据写入
    {
        writeBytes = [output write:(buffer + completed) maxLength:(len - completed)];
        if (writeBytes < 0)                     //写入失败，出错
        {
            [self terminate];
            return;
        }
        completed += writeBytes;                //加上写入的字节数
    }
    
    completedSize += len;                       //更新已完成数
    
    if ([self isCompleted] || [self isOverflowed])//完成或溢出
    {
        [self close];
        return;
    }
    
    [socket readDataWithTimeout:TIMEOUT tag:0]; //继续读下一段
    [self notifyFilelistChange];                //通知文件列表改变
}

@end
